/*
 * @file android_glue.c
 * @brief Android-specific property and system API compatibility helpers.
 *
 * SAFETY DOCUMENTATION (v1.4 Audit):
 *
 * PURPOSE:
 *   This file provides a single FFI-safe wrapper for reading Android system
 *   properties. It is used by Rust core to inspect device environment metadata.
 *
 * WHAT THIS FILE DOES:
 *   - Reads Android system properties via __system_property_get() on Android.
 *   - Returns mock/simulated values on non-Android hosts for testing.
 *
 * WHY THIS FILE IS SAFE:
 *   - All operations are READ-ONLY property lookups.
 *   - All string copies use strncpy() with explicit null-termination.
 *   - All pointer parameters are validated before use.
 *   - Buffer length is validated to be positive.
 *   - No heap allocation beyond stack-local buffers.
 *
 * WHAT THIS FILE MUST NEVER DO:
 *   - NEVER call system(), popen(), or execve().
 *   - NEVER modify system properties (no __system_property_set).
 *   - NEVER access /dev/block or any block devices.
 *   - NEVER call setenforce or modify SELinux policy.
 *   - NEVER call reboot, fastboot, or mount.
 *   - NEVER execute shell commands.
 *   - NEVER perform privilege escalation.
 *   - NEVER write to any file or device node.
 */

#include "rustdroid_c.h"
#include <stdio.h>
#include <string.h>

#if defined(__ANDROID__)
#include <sys/system_properties.h>
#endif

/**
 * @brief Read a single Android system property by key.
 *
 * SAFETY: This function is read-only. It reads from the Android property
 * service which is a standard unprivileged API available to all apps.
 * On non-Android hosts, it returns mock values for testing.
 *
 * @param key   Null-terminated property key string. Must not be NULL.
 * @param value Output buffer for property value. Must not be NULL.
 * @param max_len Maximum bytes to write into value buffer. Must be > 0.
 * @return Length of value on success, or -1 on failure.
 */
int rustdroid_c_get_property(const char *key, char *value, int max_len) {
    /* Validate all input parameters */
    if (!key || !value || max_len <= 0) {
        return -1;
    }

#if defined(__ANDROID__)
    /* Read from live Android system properties (read-only API) */
    char temp_val[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(key, temp_val);
    if (len > 0) {
        strncpy(value, temp_val, max_len - 1);
        value[max_len - 1] = '\0';
        return len;
    }
    return -1;
#else
    /* Non-Android host simulation for local testing */
    if (strcmp(key, "ro.build.version.sdk") == 0) {
        strncpy(value, "34", max_len - 1); /* Mock Android 14 API level */
        value[max_len - 1] = '\0';
        return (int)strlen(value);
    }
    strncpy(value, "mock_value", max_len - 1);
    value[max_len - 1] = '\0';
    return (int)strlen(value);
#endif
}
