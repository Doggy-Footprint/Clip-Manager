#include "AudioOutput.h"

#include <android/api-level.h>
#include <android/log.h>
#include <dlfcn.h>

#define LOG_TAG "ClipPlayer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace clip {

namespace {
using CreateStreamBuilderFn = aaudio_result_t (*)(AAudioStreamBuilder **);
using SetDirectionFn = void (*)(AAudioStreamBuilder *, aaudio_direction_t);
using SetSampleRateFn = void (*)(AAudioStreamBuilder *, int32_t);
using SetChannelCountFn = void (*)(AAudioStreamBuilder *, int32_t);
using SetFormatFn = void (*)(AAudioStreamBuilder *, aaudio_format_t);
using SetPerformanceModeFn = void (*)(AAudioStreamBuilder *, aaudio_performance_mode_t);
using OpenStreamFn = aaudio_result_t (*)(AAudioStreamBuilder *, AAudioStream **);
using DeleteBuilderFn = aaudio_result_t (*)(AAudioStreamBuilder *);
using RequestStartFn = aaudio_result_t (*)(AAudioStream *);
using RequestStopFn = aaudio_result_t (*)(AAudioStream *);
using CloseStreamFn = aaudio_result_t (*)(AAudioStream *);
using WriteFn = aaudio_result_t (*)(AAudioStream *, const void *, int32_t, int64_t);

struct AAudioApi {
    CreateStreamBuilderFn createStreamBuilder;
    SetDirectionFn setDirection;
    SetSampleRateFn setSampleRate;
    SetChannelCountFn setChannelCount;
    SetFormatFn setFormat;
    SetPerformanceModeFn setPerformanceMode;
    OpenStreamFn openStream;
    DeleteBuilderFn deleteBuilder;
    RequestStartFn requestStart;
    RequestStopFn requestStop;
    CloseStreamFn closeStream;
    WriteFn write;
};

bool loadAAudioApi(void *lib, AAudioApi *api) {
    api->createStreamBuilder = reinterpret_cast<CreateStreamBuilderFn>(dlsym(lib, "AAudio_createStreamBuilder"));
    api->setDirection = reinterpret_cast<SetDirectionFn>(dlsym(lib, "AAudioStreamBuilder_setDirection"));
    api->setSampleRate = reinterpret_cast<SetSampleRateFn>(dlsym(lib, "AAudioStreamBuilder_setSampleRate"));
    api->setChannelCount = reinterpret_cast<SetChannelCountFn>(dlsym(lib, "AAudioStreamBuilder_setChannelCount"));
    api->setFormat = reinterpret_cast<SetFormatFn>(dlsym(lib, "AAudioStreamBuilder_setFormat"));
    api->setPerformanceMode = reinterpret_cast<SetPerformanceModeFn>(dlsym(lib, "AAudioStreamBuilder_setPerformanceMode"));
    api->openStream = reinterpret_cast<OpenStreamFn>(dlsym(lib, "AAudioStreamBuilder_openStream"));
    api->deleteBuilder = reinterpret_cast<DeleteBuilderFn>(dlsym(lib, "AAudioStreamBuilder_delete"));
    api->requestStart = reinterpret_cast<RequestStartFn>(dlsym(lib, "AAudioStream_requestStart"));
    api->requestStop = reinterpret_cast<RequestStopFn>(dlsym(lib, "AAudioStream_requestStop"));
    api->closeStream = reinterpret_cast<CloseStreamFn>(dlsym(lib, "AAudioStream_close"));
    api->write = reinterpret_cast<WriteFn>(dlsym(lib, "AAudioStream_write"));
    return api->createStreamBuilder && api->setDirection && api->setSampleRate && api->setChannelCount &&
           api->setFormat && api->setPerformanceMode && api->openStream && api->deleteBuilder &&
           api->requestStart && api->requestStop && api->closeStream && api->write;
}

AAudioApi *g_api = nullptr;
} // namespace

bool AudioOutput::open(JavaVM *vm, int sampleRate, int channels) {
    vm_ = vm;
    if (android_get_device_api_level() >= 26 && openAAudio(sampleRate, channels)) {
        usingAAudio_ = true;
        return true;
    }
    JNIEnv *env = nullptr;
    if (vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return false;
    }
    usingAAudio_ = false;
    return openAudioTrack(env, sampleRate, channels);
}

bool AudioOutput::openAAudio(int sampleRate, int channels) {
    aaudioLib_ = dlopen("libaaudio.so", RTLD_NOW);
    if (!aaudioLib_) return false;

    static AAudioApi api;
    if (!loadAAudioApi(aaudioLib_, &api)) {
        dlclose(aaudioLib_);
        aaudioLib_ = nullptr;
        return false;
    }
    g_api = &api;

    if (api.createStreamBuilder(&builder_) != AAUDIO_OK || builder_ == nullptr) {
        return false;
    }
    api.setDirection(builder_, AAUDIO_DIRECTION_OUTPUT);
    api.setSampleRate(builder_, sampleRate);
    api.setChannelCount(builder_, channels);
    api.setFormat(builder_, AAUDIO_FORMAT_PCM_I16);
    api.setPerformanceMode(builder_, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);

    if (api.openStream(builder_, &stream_) != AAUDIO_OK || stream_ == nullptr) {
        api.deleteBuilder(builder_);
        builder_ = nullptr;
        return false;
    }
    return true;
}

bool AudioOutput::openAudioTrack(JNIEnv *env, int sampleRate, int channels) {
    jclass audioTrackClass = env->FindClass("android/media/AudioTrack");
    if (!audioTrackClass) {
        env->ExceptionClear();
        return false;
    }
    jclass audioFormatClass = env->FindClass("android/media/AudioFormat");
    jmethodID minBufferSizeMethod = env->GetStaticMethodID(
            audioTrackClass, "getMinBufferSize", "(III)I");
    // CHANNEL_OUT_STEREO=12, CHANNEL_OUT_MONO=4, ENCODING_PCM_16BIT=2 (AudioFormat constants).
    jint channelConfig = channels >= 2 ? 12 : 4;
    jint encoding = 2;
    jint minBufferSize = env->CallStaticIntMethod(
            audioTrackClass, minBufferSizeMethod, sampleRate, channelConfig, encoding);
    if (minBufferSize <= 0) minBufferSize = 4096;

    // STREAM_MUSIC=3, MODE_STREAM=1.
    jmethodID ctor = env->GetMethodID(audioTrackClass, "<init>", "(IIIIII)V");
    jobject local = env->NewObject(
            audioTrackClass, ctor, 3, sampleRate, channelConfig, encoding, minBufferSize * 2, 1);
    if (!local) {
        env->ExceptionClear();
        return false;
    }
    track_ = env->NewGlobalRef(local);
    env->DeleteLocalRef(local);
    env->DeleteLocalRef(audioTrackClass);
    if (audioFormatClass) env->DeleteLocalRef(audioFormatClass);

    jclass trackClass = env->GetObjectClass(track_);
    writeMethod_ = env->GetMethodID(trackClass, "write", "([SII)I");
    playMethod_ = env->GetMethodID(trackClass, "play", "()V");
    stopMethod_ = env->GetMethodID(trackClass, "stop", "()V");
    releaseMethod_ = env->GetMethodID(trackClass, "release", "()V");
    env->DeleteLocalRef(trackClass);
    return true;
}

void AudioOutput::write(const int16_t *data, int frames) {
    if (usingAAudio_) {
        if (stream_ && g_api) g_api->write(stream_, data, frames, 100'000'000LL);
        return;
    }
    if (!track_ || !vm_) return;
    JNIEnv *env = nullptr;
    if (vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return;
    int samples = frames * 2;
    jshortArray array = env->NewShortArray(samples);
    env->SetShortArrayRegion(array, 0, samples, reinterpret_cast<const jshort *>(data));
    env->CallIntMethod(track_, writeMethod_, array, 0, samples);
    env->DeleteLocalRef(array);
}

void AudioOutput::start() {
    if (usingAAudio_) {
        if (stream_ && g_api) g_api->requestStart(stream_);
    } else if (track_ && vm_) {
        JNIEnv *env = nullptr;
        if (vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->CallVoidMethod(track_, playMethod_);
        }
    }
}

void AudioOutput::stop() {
    if (usingAAudio_) {
        if (stream_ && g_api) g_api->requestStop(stream_);
    } else if (track_ && vm_) {
        JNIEnv *env = nullptr;
        if (vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->CallVoidMethod(track_, stopMethod_);
        }
    }
}

void AudioOutput::close() {
    if (usingAAudio_) {
        if (stream_ && g_api) {
            g_api->closeStream(stream_);
            stream_ = nullptr;
        }
        if (builder_ && g_api) {
            g_api->deleteBuilder(builder_);
            builder_ = nullptr;
        }
        if (aaudioLib_) {
            dlclose(aaudioLib_);
            aaudioLib_ = nullptr;
        }
    } else if (track_ && vm_) {
        JNIEnv *env = nullptr;
        if (vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->CallVoidMethod(track_, releaseMethod_);
            env->DeleteGlobalRef(track_);
        }
        track_ = nullptr;
    }
}

} // namespace clip
