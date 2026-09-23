#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <libusb.h>

// Declared in spd_dump.c after patching (see PATCHING.md). Signature:
//   int spd_dump_run(int argc, char **argv,
//                     libusb_device_handle *preopened_handle,
//                     void (*log_fn)(const char *line));
extern "C" int spd_dump_run(int argc, char **argv,
                             libusb_device_handle *preopened_handle,
                             void (*log_fn)(const char *line));

#define TAG "spddump-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static libusb_context *g_ctx = nullptr;
static libusb_device_handle *g_handle = nullptr;

// Stashed so the C-style log callback (no user-data param in spd_dump's
// existing logging hooks) can reach back into the JVM.
static JavaVM *g_vm = nullptr;

static void forward_log_to_java(const char *line) {
    if (!g_vm) { LOGI("%s", line); return; }
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != 0) { LOGI("%s", line); return; }
        attached = true;
    }
    jclass cls = env->FindClass("com/rynvortex/spddump/NativeBridge");
    if (cls) {
        jmethodID mid = env->GetStaticMethodID(cls, "onNativeLog", "(Ljava/lang/String;)V");
        if (mid) {
            jstring jline = env->NewStringUTF(line);
            env->CallStaticVoidMethod(cls, mid, jline);
            env->DeleteLocalRef(jline);
        }
        env->DeleteLocalRef(cls);
    }
    if (attached) g_vm->DetachCurrentThread();
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rynvortex_spddump_NativeBridge_nativeInit(JNIEnv *, jobject) {
    int rc = libusb_init(&g_ctx);
    if (rc != 0) {
        forward_log_to_java(("libusb_init failed: " + std::to_string(rc)).c_str());
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

// The non-root path: wrap a fd we already have OS-level permission for
// (handed to us by UsbBackend.kt via UsbManager) instead of opening the
// device node ourselves - that's the part that would otherwise need root.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_rynvortex_spddump_NativeBridge_nativeOpenWithFd(
        JNIEnv *, jobject, jint fd, jint vendorId, jint productId) {
    if (!g_ctx) {
        forward_log_to_java("nativeOpenWithFd called before nativeInit");
        return JNI_FALSE;
    }
    int rc = libusb_wrap_sys_device(g_ctx, (intptr_t) fd, &g_handle);
    if (rc != 0 || !g_handle) {
        forward_log_to_java(("libusb_wrap_sys_device failed: " + std::to_string(rc)).c_str());
        return JNI_FALSE;
    }
    forward_log_to_java(("Wrapped fd " + std::to_string(fd) + " for " +
                          std::to_string(vendorId) + ":" + std::to_string(productId)).c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rynvortex_spddump_NativeBridge_nativeRunCommands(
        JNIEnv *env, jobject, jobjectArray argsArray) {
    int argc = env->GetArrayLength(argsArray);
    std::vector<std::string> owned;
    std::vector<char *> argv;
    owned.reserve(argc + 1);
    argv.reserve(argc + 1);

    // argv[0] conventionally the program name; spd_dump doesn't rely on it
    // but keep the shape familiar for its existing arg-parsing loop.
    owned.emplace_back("spd_dump");
    argv.push_back(const_cast<char *>(owned.back().c_str()));

    for (int i = 0; i < argc; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(argsArray, i);
        const char *chars = env->GetStringUTFChars(jstr, nullptr);
        owned.emplace_back(chars);
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }
    // argv pointers must be built after `owned` stops reallocating
    argv.clear();
    for (auto &s : owned) argv.push_back(const_cast<char *>(s.c_str()));

    // g_handle may be null here if running the root path instead, in which
    // case spd_dump_run should fall back to its normal libusb_open(vid,pid) scan.
    return spd_dump_run((int) argv.size(), argv.data(), g_handle, forward_log_to_java);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rynvortex_spddump_NativeBridge_nativeClose(JNIEnv *, jobject) {
    if (g_handle) { libusb_close(g_handle); g_handle = nullptr; }
    if (g_ctx) { libusb_exit(g_ctx); g_ctx = nullptr; }
}
