/*
 * @file selinux_glue.c
 * @brief SELinux integration glue - READ-ONLY inspection without library dependencies.
 *
 * SAFETY DOCUMENTATION (v1.4 Audit):
 *
 * PURPOSE:
 *   This file provides READ-ONLY access to SELinux status information.
 *   It reads from standard Linux /sys/fs/selinux and /proc pseudo-files
 *   without linking against private libselinux.so libraries.
 *
 * CURRENT STATUS: READ-ONLY
 *   - Only reads SELinux enforcing state from /sys/fs/selinux/enforce.
 *   - Only reads process security context from /proc/self/attr/current.
 *   - No SELinux modification functions exist in this file.
 *
 * WHY THIS FILE IS SAFE:
 *   - All operations are strictly READ-ONLY using open(O_RDONLY).
 *   - O_CLOEXEC is set on all file descriptors to prevent fd leaks.
 *   - All buffers are bounds-checked with explicit length parameters.
 *   - All pointer parameters are validated before use.
 *   - File descriptors are always closed after reading.
 *   - Host fallbacks return safe mock values.
 *
 * WHAT THIS FILE MUST NEVER DO:
 *   - NEVER call setenforce() or write to /sys/fs/selinux/enforce.
 *   - NEVER modify SELinux policy (no selinux_reload_policy).
 *   - NEVER write to /proc/self/attr/current or any SELinux attribute.
 *   - NEVER call security_compute_av or modify access vectors.
 *   - NEVER weaken or disable SELinux in any way.
 *   - NEVER set SELinux to permissive mode.
 *   - NEVER load or modify sepolicy files.
 *   - NEVER call system(), popen(), or execve().
 *   - NEVER access /dev/block or any block devices.
 *   - NEVER perform privilege escalation.
 */

#include "rustdroid_c.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

/**
 * @brief Check if SELinux is currently enforcing (READ-ONLY).
 *
 * SAFETY: This function only reads a single byte from /sys/fs/selinux/enforce.
 * It uses O_RDONLY | O_CLOEXEC and never writes to any SELinux interface.
 * On hosts without SELinux (testing), returns 0 (permissive/disabled).
 *
 * @return 1 if Enforcing, 0 if Permissive or unsupported, -1 on read failure.
 */
int rustdroid_c_selinux_is_enforcing(void) {
    int fd = open("/sys/fs/selinux/enforce", O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        /* SELinux might be disabled or unsupported on local testing host */
        return 0; 
    }

    char buf[2] = {0};
    ssize_t n = read(fd, buf, 1);
    close(fd);

    if (n != 1) {
        return -1;
    }

    return (buf[0] == '1') ? 1 : 0;
}

/**
 * @brief Read the SELinux context of the current process (READ-ONLY).
 *
 * SAFETY: This function only reads from /proc/self/attr/current using O_RDONLY.
 * It uses O_CLOEXEC and never writes to any process attribute.
 * On hosts without /proc SELinux support, returns a safe mock context.
 * Output is bounded by max_len and always null-terminated.
 *
 * @param buf     Output buffer for the SELinux context string. Must not be NULL.
 * @param max_len Maximum bytes to write into buf. Must be > 0.
 * @return 0 on success, or -1 on failure.
 */
int rustdroid_c_selinux_get_context(char *buf, int max_len) {
    /* Validate input parameters */
    if (!buf || max_len <= 0) {
        errno = EINVAL;
        return -1;
    }

    int fd = open("/proc/self/attr/current", O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        /* Fallback for host simulation - return safe mock context */
        strncpy(buf, "u:r:untrusted_app:s0", max_len - 1);
        buf[max_len - 1] = '\0';
        return 0;
    }

    memset(buf, 0, max_len);
    ssize_t n = read(fd, buf, max_len - 1);
    close(fd);

    if (n < 0) {
        return -1;
    }

    /* Trim trailing newline or whitespace if present */
    while (n > 0 && (buf[n - 1] == '\n' || buf[n - 1] == '\r' || buf[n - 1] == ' ' || buf[n - 1] == '\0')) {
        buf[n - 1] = '\0';
        n--;
    }

    return 0;
}
