#include <jni.h>

#include "player/Player.h"

namespace {
JavaVM *g_vm = nullptr;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativeOpen(JNIEnv *env, jobject, jstring path) {
    const char *pathChars = env->GetStringUTFChars(path, nullptr);
    auto *player = new clip::Player(g_vm);
    bool ok = player->open(pathChars);
    env->ReleaseStringUTFChars(path, pathChars);
    if (!ok) {
        delete player;
        return 0;
    }
    return reinterpret_cast<jlong>(player);
}

JNIEXPORT void JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativeSetSurface(JNIEnv *env, jobject, jlong handle, jobject surface) {
    reinterpret_cast<clip::Player *>(handle)->setSurface(env, surface);
}

JNIEXPORT void JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativePlay(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<clip::Player *>(handle)->play();
}

JNIEXPORT void JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativePause(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<clip::Player *>(handle)->pause();
}

JNIEXPORT jboolean JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativeIsPlaying(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<clip::Player *>(handle)->isPlaying();
}

JNIEXPORT void JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativeSeekTo(JNIEnv *, jobject, jlong handle, jlong positionMs, jint mode) {
    reinterpret_cast<clip::Player *>(handle)->seekTo(positionMs, mode);
}

JNIEXPORT jlong JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativePositionMs(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<clip::Player *>(handle)->positionMs();
}

JNIEXPORT jlong JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativeDurationMs(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<clip::Player *>(handle)->durationMs();
}

JNIEXPORT void JNICALL
Java_com_doggy_clip_1manager_core_player_NativePlayer_nativeRelease(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<clip::Player *>(handle);
}

} // extern "C"
