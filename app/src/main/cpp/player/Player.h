#pragma once

#include <android/native_window.h>
#include <jni.h>
#include <media/NdkMediaCodec.h>

#include <atomic>
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
    bool initVideoDecoder(AVCodecParameters *params);
    void closeVideoDecoder();
    void closeAudioDecoder();
    void renderHwFrame(AVPacket *packet, uint64_t generation);
    void feedAndDrainHw(AVPacket *packet, uint64_t generation);
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
    bool useHwDecoder_ = false;
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
