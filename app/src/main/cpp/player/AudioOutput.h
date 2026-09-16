#pragma once

#include <cstdint>
#include <jni.h>

#include <aaudio/AAudio.h>

namespace clip {

// Plays interleaved S16 stereo PCM. Prefers AAudio (API 26+) resolved via
// dlopen so the .so still loads on API<26 devices, which lack libaaudio.so
// entirely and would fail DT_NEEDED resolution if it were link-time linked.
// Falls back to android.media.AudioTrack through JNI otherwise.
class AudioOutput {
public:
    bool open(JavaVM *vm, int sampleRate, int channels);
    void write(const int16_t *data, int frames);
    void start();
    void stop();
    void close();
    ~AudioOutput() { close(); }

private:
    bool openAAudio(int sampleRate, int channels);
    bool openAudioTrack(JNIEnv *env, int sampleRate, int channels);

    JavaVM *vm_ = nullptr;

    // AAudio backend, resolved dynamically.
    void *aaudioLib_ = nullptr;
    AAudioStreamBuilder *builder_ = nullptr;
    AAudioStream *stream_ = nullptr;

    // AudioTrack (JNI) backend.
    jobject track_ = nullptr;
    jmethodID writeMethod_ = nullptr;
    jmethodID playMethod_ = nullptr;
    jmethodID stopMethod_ = nullptr;
    jmethodID releaseMethod_ = nullptr;

    bool usingAAudio_ = false;
};

} // namespace clip
