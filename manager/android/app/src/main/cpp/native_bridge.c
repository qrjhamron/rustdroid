#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "include/boot_image_parser.h"
#include "include/boot_image_patcher.h"
#include "include/native_log.h"

#define TAG "RustDroidNative"
#define LIB_NAME "rustdroid_native"
#define LIB_VERSION "rustdroid-native-1.0.0"

static const char* current_abi(void) {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}

static jstring to_jstring(JNIEnv* env, const char* text) {
    if (!text) text = "";
    return (*env)->NewStringUTF(env, text);
}

static void escape_json(const char* src, char* dst, size_t dst_len) {
    size_t j = 0;
    if (!src || dst_len == 0) return;
    for (size_t i = 0; src[i] != '\0' && j + 2 < dst_len; ++i) {
        char c = src[i];
        if (c == '"' || c == '\\') {
            dst[j++] = '\\';
            dst[j++] = c;
        } else if (c == '\n' || c == '\r' || c == '\t') {
            dst[j++] = '\\';
            dst[j++] = (c == '\n') ? 'n' : (c == '\r' ? 'r' : 't');
        } else {
            dst[j++] = c;
        }
    }
    dst[j] = '\0';
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    __android_log_print(ANDROID_LOG_INFO, TAG, "JNI loaded: %s", LIB_VERSION);
    native_log_write("native", "info", "Native library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_rustdroid_manager_NativeBridge_nativeGetLibraryStatusJson(JNIEnv* env, jobject thiz) {
    (void)thiz;
    char json[512];
    snprintf(json, sizeof(json),
             "{\"loaded\":true,\"libraryName\":\"%s\",\"abi\":\"%s\",\"version\":\"%s\",\"error\":null}",
             LIB_NAME, current_abi(), LIB_VERSION);
    return to_jstring(env, json);
}

JNIEXPORT jstring JNICALL
Java_com_rustdroid_manager_NativeBridge_nativeGetLibraryVersion(JNIEnv* env, jobject thiz) {
    (void)thiz;
    return to_jstring(env, LIB_VERSION);
}

JNIEXPORT jstring JNICALL
Java_com_rustdroid_manager_NativeBridge_nativeAnalyzeBootImage(JNIEnv* env, jobject thiz, jstring path_) {
    (void)thiz;
    if (!path_) {
        return to_jstring(env, "{\"success\":false,\"error\":\"image path is required\"}");
    }

    const char* path = (*env)->GetStringUTFChars(env, path_, NULL);
    if (!path) {
        return to_jstring(env, "{\"success\":false,\"error\":\"failed to read image path\"}");
    }

    boot_image_info info;
    int ok = analyze_boot_image_file(path, &info);

    char json[1024];
    if (!ok) {
        char escaped[512];
        escape_json(info.error, escaped, sizeof(escaped));
        snprintf(json, sizeof(json),
                 "{\"success\":false,\"path\":\"%s\",\"error\":\"%s\",\"format\":\"unknown\",\"headerVersion\":0,\"kernelDetected\":false,\"ramdiskDetected\":false,\"patchStatus\":\"Failed\"}",
                 path, escaped);
        native_log_writef("native", "error", "Analyze failed for %s: %s", path, info.error);
    } else {
        const char* patch_status = info.already_patched ? "Patched" : "Not patched";
        snprintf(json, sizeof(json),
                 "{\"success\":true,\"path\":\"%s\",\"error\":null,\"format\":\"%s\",\"headerVersion\":%u,\"pageSize\":%u,\"kernelSize\":%u,\"ramdiskSize\":%u,\"kernelDetected\":%s,\"ramdiskDetected\":%s,\"avbFooterDetected\":%s,\"patchStatus\":\"%s\"}",
                 path,
                 info.format,
                 info.header_version,
                 info.page_size,
                 info.kernel_size,
                 info.ramdisk_size,
                 info.kernel_size > 0 ? "true" : "false",
                 info.ramdisk_size > 0 ? "true" : "false",
                 info.avb_footer_detected ? "true" : "false",
                 patch_status);
        native_log_writef("native", "info", "Analyzed boot image: %s", path);
    }

    (*env)->ReleaseStringUTFChars(env, path_, path);
    return to_jstring(env, json);
}

JNIEXPORT jstring JNICALL
Java_com_rustdroid_manager_NativeBridge_nativePatchBootImage(JNIEnv* env, jobject thiz, jstring input_path_, jstring output_dir_) {
    (void)thiz;
    if (!input_path_ || !output_dir_) {
        return to_jstring(env, "{\"success\":false,\"error\":\"inputPath and outputDir are required\"}");
    }

    const char* input_path = (*env)->GetStringUTFChars(env, input_path_, NULL);
    const char* output_dir = (*env)->GetStringUTFChars(env, output_dir_, NULL);

    if (!input_path || !output_dir) {
        if (input_path) (*env)->ReleaseStringUTFChars(env, input_path_, input_path);
        if (output_dir) (*env)->ReleaseStringUTFChars(env, output_dir_, output_dir);
        return to_jstring(env, "{\"success\":false,\"error\":\"failed to decode JNI strings\"}");
    }

    boot_patch_result res;
    int ok = patch_boot_image_file(input_path, output_dir, &res);

    char json[1400];
    if (!ok) {
        char escaped[512];
        escape_json(res.error, escaped, sizeof(escaped));
        snprintf(json, sizeof(json),
                 "{\"success\":false,\"error\":\"%s\",\"outputPath\":null,\"outputFileName\":null,\"outputSha256\":null,\"manualFlashWarning\":\"Flashing is manual. Keep your original boot.img safe.\"}",
                 escaped[0] ? escaped : "patch failed");
    } else {
        snprintf(json, sizeof(json),
                 "{\"success\":true,\"error\":null,\"outputPath\":\"%s\",\"outputFileName\":\"%s\",\"outputSha256\":\"%s\",\"manualFlashWarning\":\"Flashing is manual. Never overwrite your original boot.img.\"}",
                 res.output_path,
                 res.output_filename,
                 res.output_sha256);
    }

    (*env)->ReleaseStringUTFChars(env, input_path_, input_path);
    (*env)->ReleaseStringUTFChars(env, output_dir_, output_dir);
    return to_jstring(env, json);
}

JNIEXPORT jstring JNICALL
Java_com_rustdroid_manager_NativeBridge_nativeGetLogs(JNIEnv* env, jobject thiz, jstring category_) {
    (void)thiz;
    const char* category = NULL;
    if (category_) {
        category = (*env)->GetStringUTFChars(env, category_, NULL);
    }

    char* json = native_log_get_json(category ? category : "all");
    if (category && category_) {
        (*env)->ReleaseStringUTFChars(env, category_, category);
    }
    if (!json) {
        return to_jstring(env, "{\"status\":\"error\",\"error\":\"failed to load logs\",\"entries\":[]}");
    }

    jstring out = to_jstring(env, json);
    free(json);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_rustdroid_manager_NativeBridge_nativeClearLogs(JNIEnv* env, jobject thiz, jstring category_) {
    (void)thiz;
    const char* category = NULL;
    if (category_) {
        category = (*env)->GetStringUTFChars(env, category_, NULL);
    }

    int ok = native_log_clear(category ? category : "all");
    if (category && category_) {
        (*env)->ReleaseStringUTFChars(env, category_, category);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}
