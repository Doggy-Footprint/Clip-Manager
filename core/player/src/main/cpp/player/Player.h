#pragma once

#include <android/native_window.h>
#include <jni.h>
#include <media/NdkMediaCodec.h>

#include <atomic>
#include <map>
#include <optional>
#include <chrono>
#include <string>
#include <thread>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavcodec/bsf.h>
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
}

#include "AudioOutput.h"
#include "Clock.h"
#include "PacketQueue.h"
#include "SeekController.h"

namespace clip {

// Owns one open media file's demux/decode/render/audio pipeline. Not
// copyable; one instance per NativePlayer.open() call from the JNI bridge.
class Player {
public:
    explicit Player(JavaVM *vm);
    ~Player();

    bool open(const std::string &path);
    void setSurface(JNIEnv *env, jobject surface);
    void play();
    void pause();
    bool isPlaying() const;
    void seekTo(int64_t positionMs, int mode);
    int64_t positionMs() const;
    int64_t durationMs() const;
    void release();

private:
    void demuxLoop();
    void videoLoop();
    void audioLoop();

    void executeSeek(const SeekRequest &request);
    struct KeyframeEntry { int64_t pts; int64_t pos; };
    std::optional<KeyframeEntry> findKeyframeAtOrBefore(int64_t targetPts, uint64_t generation);
    void recordVideoPacket(const AVPacket *packet);
    bool initVideoDecoder(AVCodecParameters *params);
    void closeVideoDecoder();
    void closeAudioDecoder();
    void renderHwFrame(AVPacket *packet, uint64_t generation);
    void feedAndDrainHw(AVPacket *packet, uint64_t generation);
    void drainHwOutput(uint64_t generation);
    bool hwDecoderStalled();
    void renderSwFrame(AVFrame *frame);
    void pacePresentation(double ptsSeconds);

    JavaVM *vm_;

    AVFormatContext *fmt_ = nullptr;
    int videoStreamIndex_ = -1;
    int audioStreamIndex_ = -1;
    AVRational videoTimeBase_{1, 1};
    AVRational audioTimeBase_{1, 1};

    PacketQueue videoQueue_;
    PacketQueue audioQueue_;
    SeekController seekController_;
    Clock clock_;

    std::atomic<bool> opened_{false};
    std::atomic<bool> running_{false};
    std::atomic<bool> playing_{false};
    std::atomic<bool> pendingImmediateFrame_{false};
    std::atomic<bool> needKeyframe_{false};
    std::atomic<bool> surfaceChanged_{false};
    std::atomic<bool> flushVideoDecoder_{false};
    std::atomic<bool> flushAudioDecoder_{false};
    std::atomic<int64_t> positionMs_{0};
    int64_t durationMs_ = 0;
    std::chrono::steady_clock::time_point seekStart_{};

    std::thread demuxThread_;
    std::thread videoThread_;
    std::thread audioThread_;

    std::mutex windowMutex_;
    ANativeWindow *window_ = nullptr;

    AVCodecParameters *videoParams_ = nullptr;

    // Demux-thread only.
    bool isMpegTs_ = false;
    std::map<int64_t, int64_t> keyframeIndex_;  // video pts -> byte position
    int64_t maxKeyframeInterval_ = 0;
    int64_t lastKeyframePts_ = AV_NOPTS_VALUE;
    bool awaitingVideoKeyframe_ = false;
    int64_t firstKeyframePts_ = AV_NOPTS_VALUE;
    bool useHwDecoder_ = false;
    // Some MediaCodec decoders (e.g. emulator c2.goldfish.*) accept input but never emit output.
    bool hwDecoderDisabled_ = false;
    bool hwOutputSeen_ = false;
    bool hwInputQueued_ = false;
    // Only time spent feeding/draining the codec counts, so pauses cannot trigger the fallback.
    std::chrono::steady_clock::duration hwTimeWithoutOutput_{};
    AMediaCodec *videoCodec_ = nullptr;
    AVCodecContext *videoCtx_ = nullptr;
    AVBSFContext *bsf_ = nullptr;
    struct SwsContext *sws_ = nullptr;

    bool hasAudio_ = false;
    AVCodecContext *audioCtx_ = nullptr;
    SwrContext *swr_ = nullptr;
    AudioOutput audioOutput_;
};

} // namespace clip
