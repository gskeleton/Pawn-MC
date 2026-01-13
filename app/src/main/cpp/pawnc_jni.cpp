#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <libgen.h>
#include <cstring>

#define LOG_TAG "PawnCompiler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
    int pc_compile(int argc, char **argv);
    void pawnc_set_output_callback(void (*callback)(const char *message));
    void pawnc_set_error_callback(void (*callback)(
        int number,
        const char *filename,
        int firstline,
        int lastline,
        const char *message
    ));
    void pawnc_clear_callbacks(void);
}

static JavaVM *g_jvm = nullptr;
static jobject g_outputListener = nullptr;
static jobject g_errorListener = nullptr;
static jmethodID g_outputMethod = nullptr;
static jmethodID g_errorMethod = nullptr;

static JNIEnv* getJNIEnv() {
    JNIEnv *env = nullptr;
    if (g_jvm) {
        int status = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            g_jvm->AttachCurrentThread(&env, nullptr);
        }
    }
    return env;
}

// Native output callback
static void nativeOutputCallback(const char *message) {
    JNIEnv *env = getJNIEnv();
    if (env && g_outputListener && g_outputMethod) {
        jstring jmsg = env->NewStringUTF(message ? message : "");
        env->CallVoidMethod(g_outputListener, g_outputMethod, jmsg);
        env->DeleteLocalRef(jmsg);
    }
}

// Native error callback
static void nativeErrorCallback(int number, const char *filename, int firstline, int lastline, const char *message) {
    JNIEnv *env = getJNIEnv();
    if (env && g_errorListener && g_errorMethod) {
        jstring jfilename = env->NewStringUTF(filename ? filename : "");
        jstring jmessage = env->NewStringUTF(message ? message : "");
        env->CallVoidMethod(g_errorListener, g_errorMethod, number, jfilename, firstline, lastline, jmessage);
        env->DeleteLocalRef(jfilename);
        env->DeleteLocalRef(jmessage);
    }
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_com_rvdjv_pawnmc_PawnCompiler_nativeCompile(JNIEnv *env, jobject thiz, jobjectArray args) {
    int argc = env->GetArrayLength(args);
    char **argv = new char*[argc];

    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(args, i);
        const char *str = env->GetStringUTFChars(jstr, nullptr);
        argv[i] = strdup(str);
        env->ReleaseStringUTFChars(jstr, str);
        env->DeleteLocalRef(jstr);
    }

    char *oldCwd = getcwd(nullptr, 0);
    if (argc > 1) {
        char *sourcePathCopy = strdup(argv[argc - 1]);
        char *sourceDir = dirname(sourcePathCopy);
        LOGI("Changing to directory: %s", sourceDir);
        if (chdir(sourceDir) != 0) {
            LOGE("Failed to chdir to %s", sourceDir);
        }
        free(sourcePathCopy);
    }

    LOGI("Calling pc_compile with %d args", argc);
    int result = pc_compile(argc, argv);
    LOGI("pc_compile returned %d", result);

    if (oldCwd) {
        chdir(oldCwd);
        free(oldCwd);
    }

    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    delete[] argv;

    return result;
}

JNIEXPORT void JNICALL
Java_com_rvdjv_pawnmc_PawnCompiler_nativeSetOutputCallback(JNIEnv *env, jobject thiz, jobject listener) {
    if (g_outputListener) {
        env->DeleteGlobalRef(g_outputListener);
        g_outputListener = nullptr;
    }

    if (listener) {
        g_outputListener = env->NewGlobalRef(listener);
        jclass listenerClass = env->GetObjectClass(listener);
        g_outputMethod = env->GetMethodID(listenerClass, "onOutput", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(listenerClass);
        pawnc_set_output_callback(nativeOutputCallback);
    } else {
        pawnc_set_output_callback(nullptr);
    }
}

JNIEXPORT void JNICALL
Java_com_rvdjv_pawnmc_PawnCompiler_nativeSetErrorCallback(JNIEnv *env, jobject thiz, jobject listener) {
    if (g_errorListener) {
        env->DeleteGlobalRef(g_errorListener);
        g_errorListener = nullptr;
    }

    if (listener) {
        g_errorListener = env->NewGlobalRef(listener);
        jclass listenerClass = env->GetObjectClass(listener);
        g_errorMethod = env->GetMethodID(listenerClass, "onError", "(ILjava/lang/String;IILjava/lang/String;)V");
        env->DeleteLocalRef(listenerClass);
        pawnc_set_error_callback(nativeErrorCallback);
    } else {
        pawnc_set_error_callback(nullptr);
    }
}

JNIEXPORT void JNICALL
Java_com_rvdjv_pawnmc_PawnCompiler_nativeClearCallbacks(JNIEnv *env, jobject thiz) {
    pawnc_clear_callbacks();

    if (g_outputListener) {
        env->DeleteGlobalRef(g_outputListener);
        g_outputListener = nullptr;
    }
    if (g_errorListener) {
        env->DeleteGlobalRef(g_errorListener);
        g_errorListener = nullptr;
    }
    g_outputMethod = nullptr;
    g_errorMethod = nullptr;
}

}