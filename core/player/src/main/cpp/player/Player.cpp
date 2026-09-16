#include "Player.h"

#include <android/log.h>
#include <android/native_window_jni.h>

#include <algorithm>
#include <iterator>
#include <cstring>

#include "CodecMap.h"

extern "C" {
#include <libavutil/channel_layout.h>
}

#define LOG_TAG "ClipPlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace clip {

namespace {
constexpr size_t kMaxQueuedPackets = 256;
constexpr std::chrono::milliseconds kHwFirstOutputTimeout{1000};
}

Player::Player(JavaVM *vm) : vm_(vm) {}

Player::~Player() { release(); }

bool Player::open(const std::string &path) {
    if (avformat_open_input(&fmt_, path.c_str(), nullptr, nullptr) != 0) {
        return false;
    }
    if (avformat_find_stream_info(fmt_, nullptr) < 0) {
        avformat_close_input(&fmt_);
        return false;
    }

    videoStreamIndex_ = av_find_best_stream(fmt_, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (videoStreamIndex_ < 0) {
        avformat_close_input(&fmt_);
        return false;
    }
    AVStream *videoStream = fmt_->streams[videoStreamIndex_];
    videoTimeBase_ = videoStream->time_base;
    videoParams_ = videoStream->codecpar;
    isMpegTs_ = fmt_->iformat && fmt_->iformat->name && std::strstr(fmt_->iformat->name, "mpegts");
    if (!mediaCodecMimeFor(videoParams_->codec_id) && !avcodec_find_decoder(videoParams_->codec_id)) {
        avformat_close_input(&fmt_);
        return false;
    }

    audioStreamIndex_ = av_find_best_stream(fmt_, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    if (audioStreamIndex_ >= 0) {
        AVStream *audioStream = fmt_->streams[audioStreamIndex_];
        audioTimeBase_ = audioStream->time_base;
        const AVCodec *codec = avcodec_find_decoder(audioStream->codecpar->codec_id);
        if (codec) {
            audioCtx_ = avcodec_alloc_context3(codec);
            avcodec_parameters_to_context(audioCtx_, audioStream->codecpar);
            if (avcodec_open2(audioCtx_, codec, nullptr) == 0) {
                hasAudio_ = true;
            } else {
                avcodec_free_context(&audioCtx_);
            }
        }
    }

    durationMs_ = fmt_->duration > 0 ? fmt_->duration / 1000 : 0;
    clock_.reset(0.0);
    opened_ = true;
    running_ = true;
    demuxThread_ = std::thread(&Player::demuxLoop, this);
    videoThread_ = std::thread(&Player::videoLoop, this);
    if (hasAudio_) audioThread_ = std::thread(&Player::audioLoop, this);
    return true;
}

bool Player::initVideoDecoder(AVCodecParameters *params) {
    AVCodecID id = params->codec_id;
    const char *mime = mediaCodecMimeFor(id);
    useHwDecoder_ = false;
    hwOutputSeen_ = false;
    hwInputQueued_ = false;
    hwTimeWithoutOutput_ = {};

    if (mime && !hwDecoderDisabled_) {
        // TS packetizes H.264/HEVC as Annex-B already; other containers store
        // length-prefixed NAL units and need conversion before MediaCodec
        // (which expects inline SPS/PPS) will accept them.
        bool alreadyAnnexB = fmt_->iformat != nullptr && fmt_->iformat->name != nullptr &&
                              std::strstr(fmt_->iformat->name, "mpegts") != nullptr;
        if ((id == AV_CODEC_ID_H264 || id == AV_CODEC_ID_HEVC) && !alreadyAnnexB) {
            const char *bsfName = id == AV_CODEC_ID_H264 ? "h264_mp4toannexb" : "hevc_mp4toannexb";
            const AVBitStreamFilter *filter = av_bsf_get_by_name(bsfName);
            if (filter && av_bsf_alloc(filter, &bsf_) == 0) {
                avcodec_parameters_copy(bsf_->par_in, params);
                if (av_bsf_init(bsf_) != 0) {
                    av_bsf_free(&bsf_);
                    bsf_ = nullptr;
                }
            }
        }

        AMediaFormat *format = AMediaFormat_new();
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, params->width);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, params->height);
        if (id != AV_CODEC_ID_H264 && id != AV_CODEC_ID_HEVC && params->extradata_size > 0) {
            AMediaFormat_setBuffer(format, "csd-0", params->extradata, params->extradata_size);
        }

        videoCodec_ = AMediaCodec_createDecoderByType(mime);
        if (videoCodec_) {
            std::lock_guard<std::mutex> lock(windowMutex_);
            media_status_t status = AMediaCodec_configure(videoCodec_, format, window_, nullptr, 0);
            if (status == AMEDIA_OK && AMediaCodec_start(videoCodec_) == AMEDIA_OK) {
                useHwDecoder_ = true;
            } else {
                AMediaCodec_delete(videoCodec_);
                videoCodec_ = nullptr;
            }
        }
        AMediaFormat_delete(format);
        if (!useHwDecoder_ && bsf_) {
            av_bsf_free(&bsf_);
            bsf_ = nullptr;
        }
    }

    if (!useHwDecoder_) {
        const AVCodec *codec = avcodec_find_decoder(id);
        if (!codec) return false;
        videoCtx_ = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(videoCtx_, params);
        if (avcodec_open2(videoCtx_, codec, nullptr) != 0) {
            avcodec_free_context(&videoCtx_);
            return false;
        }
    }
    return true;
}

void Player::setSurface(JNIEnv *env, jobject surface) {
    ANativeWindow *newWindow = surface != nullptr ? ANativeWindow_fromSurface(env, surface) : nullptr;
    {
        std::lock_guard<std::mutex> lock(windowMutex_);
        if (window_) ANativeWindow_release(window_);
        window_ = newWindow;
    }
    surfaceChanged_ = true;
    // SurfaceView destroys the surface once surfaceDestroyed() returns, so give the video
    // thread a chance to tear down a MediaCodec still rendering into it.
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(500);
    while (opened_ && surfaceChanged_ && std::chrono::steady_clock::now() < deadline) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
}

void Player::play() {
    if (!opened_) return;
    playing_ = true;
    audioOutput_.start();
}

void Player::pause() {
    if (!opened_) return;
    playing_ = false;
    audioOutput_.stop();
}

bool Player::isPlaying() const { return playing_; }

void Player::seekTo(int64_t positionMs, int mode) {
    if (!opened_) return;
    uint64_t generation = seekController_.request(positionMs, mode);
    LOGI("seek request generation=%llu positionMs=%lld mode=%d",
         static_cast<unsigned long long>(generation), static_cast<long long>(positionMs), mode);
}

int64_t Player::positionMs() const { return opened_ ? positionMs_.load() : 0; }

int64_t Player::durationMs() const { return opened_ ? durationMs_ : 0; }

void Player::recordVideoPacket(const AVPacket *packet) {
    if (!(packet->flags & AV_PKT_FLAG_KEY) || packet->pos < 0) return;
    int64_t pts = packet->pts != AV_NOPTS_VALUE ? packet->pts : packet->dts;
    if (pts == AV_NOPTS_VALUE) return;
    keyframeIndex_[pts] = packet->pos;
    if (lastKeyframePts_ != AV_NOPTS_VALUE && pts > lastKeyframePts_) {
        maxKeyframeInterval_ = std::max(maxKeyframeInterval_, pts - lastKeyframePts_);
    }
    lastKeyframePts_ = pts;
}

// MPEG-TS has no container index and FFmpeg's mpegts timestamp seek lands on an arbitrary
// packet, so decoding would resume only at the *next* keyframe (up to a full GOP late).
// Use av_add_index_entry/av_index_search_timestamp to build and query our own
// keyframe index and seek to the keyframe at or before the target by byte position.
std::optional<Player::KeyframeEntry> Player::findKeyframeAtOrBefore(int64_t targetPts, uint64_t generation) {
    auto it = keyframeIndex_.upper_bound(targetPts);
    if (it != keyframeIndex_.begin()) {
        auto prev = std::prev(it);
        bool nextKnown = it != keyframeIndex_.end() && it->first - prev->first <= maxKeyframeInterval_;
        if (maxKeyframeInterval_ > 0 && (nextKnown || targetPts - prev->first <= maxKeyframeInterval_)) {
            return KeyframeEntry{prev->first, prev->second};
        }
    }

    AVStream *stream = fmt_->streams[videoStreamIndex_];
    int64_t startPts = stream->start_time != AV_NOPTS_VALUE ? stream->start_time : 0;
    int64_t window = maxKeyframeInterval_ > 0
                             ? maxKeyframeInterval_
                             : av_rescale_q(2, AVRational{1, 1}, videoTimeBase_);
    AVPacket *packet = av_packet_alloc();
    std::optional<KeyframeEntry> best;
    while (running_ && !seekController_.isStale(generation)) {
        int64_t probeFrom = std::max(startPts, targetPts - window);
        av_seek_frame(fmt_, videoStreamIndex_, probeFrom, AVSEEK_FLAG_BACKWARD);
        lastKeyframePts_ = AV_NOPTS_VALUE;
        while (av_read_frame(fmt_, packet) >= 0) {
            bool isVideo = packet->stream_index == videoStreamIndex_;
            int64_t pts = packet->pts != AV_NOPTS_VALUE ? packet->pts : packet->dts;
            if (isVideo) recordVideoPacket(packet);
            av_packet_unref(packet);
            if (!isVideo || pts == AV_NOPTS_VALUE) continue;
            if (pts > targetPts) break;
            if (seekController_.isStale(generation)) break;
        }
        auto found = keyframeIndex_.upper_bound(targetPts);
        if (found != keyframeIndex_.begin() && std::prev(found)->first >= probeFrom) {
            best = KeyframeEntry{std::prev(found)->first, std::prev(found)->second};
            break;
        }
        if (probeFrom == startPts) break;
        window *= 2;
    }
    av_packet_free(&packet);
    lastKeyframePts_ = AV_NOPTS_VALUE;
    return best;
}

void Player::executeSeek(const SeekRequest &request) {
    seekStart_ = std::chrono::steady_clock::now();
    bool seeked = false;
    if (isMpegTs_) {
        AVStream *stream = fmt_->streams[videoStreamIndex_];
        int64_t startPts = stream->start_time != AV_NOPTS_VALUE ? stream->start_time : 0;
        int64_t targetPts = startPts + av_rescale_q(request.positionMs, AVRational{1, 1000}, videoTimeBase_);
        auto keyframe = findKeyframeAtOrBefore(targetPts, request.generation);
        if (seekController_.isStale(request.generation)) return;
        if (keyframe) {
            seeked = av_seek_frame(fmt_, videoStreamIndex_, keyframe->pos, AVSEEK_FLAG_BYTE) >= 0;
        }
    }
    if (!seeked) {
        av_seek_frame(fmt_, -1, request.positionMs * 1000, AVSEEK_FLAG_BACKWARD);
    }
    lastKeyframePts_ = AV_NOPTS_VALUE;
    awaitingVideoKeyframe_ = true;
    firstKeyframePts_ = AV_NOPTS_VALUE;
    videoQueue_.flush();
    audioQueue_.flush();
    flushVideoDecoder_ = true;
    flushAudioDecoder_ = true;
    needKeyframe_ = true;
    pendingImmediateFrame_ = true;
    LOGI("seek executed generation=%llu positionMs=%lld prepare_ms=%lld",
         static_cast<unsigned long long>(request.generation), static_cast<long long>(request.positionMs),
         static_cast<long long>(std::chrono::duration_cast<std::chrono::milliseconds>(
                 std::chrono::steady_clock::now() - seekStart_).count()));
}

void Player::demuxLoop() {
    uint64_t currentGeneration = 0;
    while (running_) {
        auto request = seekController_.take();
        if (request) {
            executeSeek(*request);
            currentGeneration = request->generation;
            continue;
        }
        if (videoQueue_.size() >= kMaxQueuedPackets || audioQueue_.size() >= kMaxQueuedPackets) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }
        AVPacket *packet = av_packet_alloc();
        int ret = av_read_frame(fmt_, packet);
        if (ret < 0) {
            av_packet_free(&packet);
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            continue;
        }
        if (packet->stream_index == videoStreamIndex_) {
            if (isMpegTs_) recordVideoPacket(packet);
            if (awaitingVideoKeyframe_) {
                if (!(packet->flags & AV_PKT_FLAG_KEY)) {
                    av_packet_free(&packet);
                    continue;
                }
                awaitingVideoKeyframe_ = false;
                firstKeyframePts_ = packet->pts;
            }
            videoQueue_.push(packet, currentGeneration);
        } else if (hasAudio_ && packet->stream_index == audioStreamIndex_) {
            // Audio before the first video keyframe cannot be presented in sync, and queueing it
            // would stall the demuxer (queue bound) while video still searches for a keyframe.
            bool beforeKeyframe = awaitingVideoKeyframe_ ||
                                  (firstKeyframePts_ != AV_NOPTS_VALUE && packet->pts != AV_NOPTS_VALUE &&
                                   av_compare_ts(packet->pts, audioTimeBase_, firstKeyframePts_, videoTimeBase_) < 0);
            if (beforeKeyframe) {
                av_packet_free(&packet);
            } else {
                firstKeyframePts_ = AV_NOPTS_VALUE;
                audioQueue_.push(packet, currentGeneration);
            }
        } else {
            av_packet_free(&packet);
        }
    }
}

void Player::pacePresentation(double ptsSeconds) {
    while (running_ && playing_) {
        double diff = ptsSeconds - clock_.get();
        if (diff <= 0.005) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(std::min<int64_t>(static_cast<int64_t>(diff * 1000), 20)));
    }
}

void Player::renderSwFrame(AVFrame *frame) {
    std::lock_guard<std::mutex> lock(windowMutex_);
    if (!window_) return;
    ANativeWindow_setBuffersGeometry(window_, frame->width, frame->height, WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window_, &buffer, nullptr) != 0) return;
    sws_ = sws_getCachedContext(sws_, frame->width, frame->height, static_cast<AVPixelFormat>(frame->format),
                                 frame->width, frame->height, AV_PIX_FMT_RGBA, SWS_BILINEAR, nullptr, nullptr, nullptr);
    uint8_t *dst[4] = {static_cast<uint8_t *>(buffer.bits), nullptr, nullptr, nullptr};
    int dstStride[4] = {buffer.stride * 4, 0, 0, 0};
    sws_scale(sws_, frame->data, frame->linesize, 0, frame->height, dst, dstStride);
    ANativeWindow_unlockAndPost(window_);
}

bool Player::hwDecoderStalled() {
    if (hwOutputSeen_ || !hwInputQueued_ || hwTimeWithoutOutput_ < kHwFirstOutputTimeout) return false;
    LOGI("hw decoder produced no output, falling back to software decoding");
    hwDecoderDisabled_ = true;
    surfaceChanged_ = true;
    return true;
}

void Player::drainHwOutput(uint64_t generation) {
    AMediaCodecBufferInfo info;
    ssize_t outputIndex = AMediaCodec_dequeueOutputBuffer(videoCodec_, &info, 10000);
    if (outputIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) hwOutputSeen_ = true;
    if (outputIndex < 0) return;
    hwOutputSeen_ = true;
    double pts = info.presentationTimeUs / 1000000.0;
    bool render = !seekController_.isStale(generation);
    if (render && pendingImmediateFrame_) {
        clock_.reset(pts);
        positionMs_ = static_cast<int64_t>(pts * 1000);
        pendingImmediateFrame_ = false;
        LOGI("first frame after seek latency_ms=%lld",
             static_cast<long long>(std::chrono::duration_cast<std::chrono::milliseconds>(
                     std::chrono::steady_clock::now() - seekStart_).count()));
    } else if (render) {
        pacePresentation(pts);
        render = !seekController_.isStale(generation);
        if (render) {
            positionMs_ = static_cast<int64_t>(pts * 1000);
            if (!hasAudio_) clock_.set(pts);
        }
    }
    AMediaCodec_releaseOutputBuffer(videoCodec_, outputIndex, render);
}

void Player::feedAndDrainHw(AVPacket *packet, uint64_t generation) {
    // Dropping a packet when no input buffer is free corrupts every frame up to the next keyframe,
    // so wait for one while draining output (which is what frees input buffers).
    auto feedStart = std::chrono::steady_clock::now();
    auto accountWait = [&] {
        if (hwInputQueued_ && !hwOutputSeen_) {
            auto now = std::chrono::steady_clock::now();
            hwTimeWithoutOutput_ += now - feedStart;
            feedStart = now;
        }
    };
    while (true) {
        if (!running_ || surfaceChanged_ || flushVideoDecoder_ || seekController_.isStale(generation)) return;
        accountWait();
        if (hwDecoderStalled()) return;
        ssize_t inputIndex = AMediaCodec_dequeueInputBuffer(videoCodec_, 10000);
        if (inputIndex >= 0) {
            size_t bufferSize = 0;
            uint8_t *buffer = AMediaCodec_getInputBuffer(videoCodec_, inputIndex, &bufferSize);
            size_t copySize = std::min(static_cast<size_t>(packet->size), bufferSize);
            std::memcpy(buffer, packet->data, copySize);
            int64_t ptsUs = packet->pts == AV_NOPTS_VALUE
                                    ? 0
                                    : static_cast<int64_t>(packet->pts * av_q2d(videoTimeBase_) * 1000000.0);
            AMediaCodec_queueInputBuffer(videoCodec_, inputIndex, 0, copySize, ptsUs, 0);
            if (!hwInputQueued_) {
                hwInputQueued_ = true;
                feedStart = std::chrono::steady_clock::now();
            }
            break;
        }
        drainHwOutput(generation);
    }
    drainHwOutput(generation);
    accountWait();
    hwDecoderStalled();
}

void Player::renderHwFrame(AVPacket *packet, uint64_t generation) {
    if (bsf_) {
        av_bsf_send_packet(bsf_, packet);
        av_packet_free(&packet);
        AVPacket *filtered = av_packet_alloc();
        while (av_bsf_receive_packet(bsf_, filtered) == 0) {
            feedAndDrainHw(filtered, generation);
            av_packet_unref(filtered);
        }
        av_packet_free(&filtered);
    } else {
        feedAndDrainHw(packet, generation);
        av_packet_free(&packet);
    }
}

void Player::videoLoop() {
    AVFrame *frame = av_frame_alloc();
    bool decoderReady = false;
    while (running_) {
        // MediaCodec only accepts setOutputSurface() when it was configured with a surface,
        // and the SurfaceView's surface arrives after open(), so the decoder is (re)built here.
        if (surfaceChanged_.exchange(false)) {
            closeVideoDecoder();
            bool hasWindow;
            {
                std::lock_guard<std::mutex> lock(windowMutex_);
                hasWindow = window_ != nullptr;
            }
            decoderReady = hasWindow && initVideoDecoder(videoParams_);
            flushVideoDecoder_ = false;
            if (decoderReady) seekController_.request(positionMs_.load(), 0);
        }
        if (!decoderReady) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        if (flushVideoDecoder_.exchange(false)) {
            if (useHwDecoder_ && videoCodec_) AMediaCodec_flush(videoCodec_);
            if (videoCtx_) avcodec_flush_buffers(videoCtx_);
        }
        if (!playing_ && !pendingImmediateFrame_) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        TaggedPacket tagged;
        if (!videoQueue_.pop(&tagged)) break;
        AVPacket *packet = tagged.packet;
        if (seekController_.isStale(tagged.generation)) {
            av_packet_free(&packet);
            continue;
        }
        if (needKeyframe_) {
            if (!(packet->flags & AV_PKT_FLAG_KEY)) {
                av_packet_free(&packet);
                continue;
            }
            needKeyframe_ = false;
        }

        if (useHwDecoder_) {
            renderHwFrame(packet, tagged.generation);
            continue;
        }

        avcodec_send_packet(videoCtx_, packet);
        av_packet_free(&packet);
        while (avcodec_receive_frame(videoCtx_, frame) == 0) {
            if (seekController_.isStale(tagged.generation)) {
                av_frame_unref(frame);
                continue;
            }
            double pts = frame->pts == AV_NOPTS_VALUE ? 0.0 : frame->pts * av_q2d(videoTimeBase_);
            if (pendingImmediateFrame_) {
                renderSwFrame(frame);
                clock_.reset(pts);
                positionMs_ = static_cast<int64_t>(pts * 1000);
                pendingImmediateFrame_ = false;
                LOGI("first frame after seek latency_ms=%lld",
                     static_cast<long long>(std::chrono::duration_cast<std::chrono::milliseconds>(
                             std::chrono::steady_clock::now() - seekStart_).count()));
            } else {
                pacePresentation(pts);
                if (!seekController_.isStale(tagged.generation)) {
                    renderSwFrame(frame);
                    positionMs_ = static_cast<int64_t>(pts * 1000);
                    if (!hasAudio_) clock_.set(pts);
                }
            }
            av_frame_unref(frame);
        }
    }
    if (frame) av_frame_free(&frame);
}

void Player::audioLoop() {
    AVChannelLayout outLayout;
    av_channel_layout_default(&outLayout, 2);
    int outSampleRate = audioCtx_->sample_rate > 0 ? audioCtx_->sample_rate : 44100;
    swr_alloc_set_opts2(&swr_, &outLayout, AV_SAMPLE_FMT_S16, outSampleRate,
                         &audioCtx_->ch_layout, audioCtx_->sample_fmt, audioCtx_->sample_rate, 0, nullptr);
    swr_init(swr_);
    audioOutput_.open(vm_, outSampleRate, 2);
    if (playing_) audioOutput_.start();

    AVFrame *frame = av_frame_alloc();
    while (running_) {
        if (flushAudioDecoder_.exchange(false)) avcodec_flush_buffers(audioCtx_);
        if (!playing_) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        TaggedPacket tagged;
        if (!audioQueue_.pop(&tagged)) break;
        AVPacket *packet = tagged.packet;
        if (seekController_.isStale(tagged.generation)) {
            av_packet_free(&packet);
            continue;
        }
        avcodec_send_packet(audioCtx_, packet);
        av_packet_free(&packet);
        while (avcodec_receive_frame(audioCtx_, frame) == 0) {
            if (seekController_.isStale(tagged.generation)) {
                av_frame_unref(frame);
                continue;
            }
            int outSamples = swr_get_out_samples(swr_, frame->nb_samples);
            uint8_t *outBuffer = nullptr;
            av_samples_alloc(&outBuffer, nullptr, 2, outSamples, AV_SAMPLE_FMT_S16, 0);
            int converted = swr_convert(swr_, &outBuffer, outSamples,
                                         const_cast<const uint8_t **>(frame->extended_data), frame->nb_samples);
            double pts = frame->pts == AV_NOPTS_VALUE ? 0.0 : frame->pts * av_q2d(audioTimeBase_);
            if (converted > 0 && playing_) {
                clock_.set(pts);
                audioOutput_.write(reinterpret_cast<int16_t *>(outBuffer), converted);
            }
            av_freep(&outBuffer);
            av_frame_unref(frame);
        }
    }
    av_frame_free(&frame);
    av_channel_layout_uninit(&outLayout);
}

void Player::closeVideoDecoder() {
    useHwDecoder_ = false;
    if (videoCodec_) {
        AMediaCodec_stop(videoCodec_);
        AMediaCodec_delete(videoCodec_);
        videoCodec_ = nullptr;
    }
    if (bsf_) av_bsf_free(&bsf_);
    if (videoCtx_) avcodec_free_context(&videoCtx_);
    if (sws_) {
        sws_freeContext(sws_);
        sws_ = nullptr;
    }
}

void Player::closeAudioDecoder() {
    if (audioCtx_) avcodec_free_context(&audioCtx_);
    if (swr_) swr_free(&swr_);
}

void Player::release() {
    if (!opened_ && !running_) return;
    running_ = false;
    playing_ = false;
    videoQueue_.abort();
    audioQueue_.abort();
    if (demuxThread_.joinable()) demuxThread_.join();
    if (videoThread_.joinable()) videoThread_.join();
    if (audioThread_.joinable()) audioThread_.join();

    closeVideoDecoder();
    closeAudioDecoder();
    audioOutput_.close();

    {
        std::lock_guard<std::mutex> lock(windowMutex_);
        if (window_) {
            ANativeWindow_release(window_);
            window_ = nullptr;
        }
    }
    if (fmt_) avformat_close_input(&fmt_);
    opened_ = false;
}

} // namespace clip
