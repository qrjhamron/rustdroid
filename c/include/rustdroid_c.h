/*
 * @file rustdroid_c.h
 * @brief Public header for all RustDroid C glue FFI functions.
 *
 * SAFETY DOCUMENTATION (v1.4 Audit):
 *
 * This header declares the complete set of C-level FFI functions exported
 * by the RustDroid C glue layer. All functions follow these invariants:
 *
 *   1. ZERO CORE LOGIC: No parsing, rules evaluation, module verification,
 *      or state tracking is done in C. All decisions reside in Rust.
 *   2. SAFE FFI: Every function takes primitive types or standard
 *      null-terminated const char* strings. All pointer params are validated.
 *   3. NO PANICS: All routines return integer status codes (0 = success,
 *      non-zero = failure) and set errno where appropriate.
 *   4. HOST FALLBACKS: Every platform-dependent syscall has dummy/mock
 *      fallbacks for local test suite compatibility.
 *
 * v1.4 AUDIT STATUS:
 *   - android_glue.c:  SAFE     (read-only property access)
 *   - mount_glue.c:    DISABLED (mount syscall compiled out via guard)
 *   - selinux_glue.c:  READ-ONLY (only reads /sys/fs/selinux and /proc)
 *   - process_glue.c:  RESTRICTED (credential dropping only, no escalation)
 *
 * GLOBAL FORBIDDEN OPERATIONS (apply to ALL C glue files):
 *   - No system(), popen(), or execve() calls.
 *   - No setenforce or SELinux policy modification.
 *   - No pivot_root or namespace manipulation.
 *   - No /dev/block access or block device writes.
 *   - No reboot, fastboot, or automatic flashing.
 *   - No root hiding, process hiding, or stealth.
 *   - No kprobe installation or syscall hooking.
 *   - No attestation manipulation or Play Integrity bypass.
 *   - No shell command execution.
 */

#ifndef RUSTDROID_C_H
#define RUSTDROID_C_H

#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Read an Android system property by key (READ-ONLY).
 *
 * Safe: Only reads from Android property service. Uses strncpy with bounds.
 * Host fallback: Returns mock values for testing.
 *
 * @param key     Property key (null-terminated). Must not be NULL.
 * @param value   Output buffer. Must not be NULL.
 * @param max_len Buffer capacity in bytes. Must be > 0.
 * @return Length of value on success, or -1 on failure.
 */
int rustdroid_c_get_property(const char *key, char *value, int max_len);

/**
 * @brief Perform a Linux bind mount (DISABLED in v1.4).
 *
 * DISABLED: This function always returns -1 with errno=ENOSYS in v1.4.
 * The actual mount() syscall is compiled out via RUSTDROID_V14_MOUNT_DISABLED.
 * Exists only for API compatibility with future milestones.
 *
 * @param source Source path. Must not be NULL.
 * @param target Target mount point. Must not be NULL.
 * @return Always -1 in v1.4 (not implemented).
 */
int rustdroid_c_bind_mount(const char *source, const char *target);

/**
 * @brief Check if SELinux is enforcing (READ-ONLY).
 *
 * Safe: Only reads /sys/fs/selinux/enforce with O_RDONLY.
 * Never modifies SELinux state.
 *
 * @return 1 if Enforcing, 0 if Permissive/disabled, -1 on read error.
 */
int rustdroid_c_selinux_is_enforcing(void);

/**
 * @brief Read current process SELinux context (READ-ONLY).
 *
 * Safe: Only reads /proc/self/attr/current with O_RDONLY.
 * Never writes to SELinux attributes.
 * Host fallback: Returns "u:r:untrusted_app:s0".
 *
 * @param buf     Output buffer. Must not be NULL.
 * @param max_len Buffer capacity. Must be > 0.
 * @return 0 on success, -1 on failure.
 */
int rustdroid_c_selinux_get_context(char *buf, int max_len);

/**
 * @brief Switch process credentials - DROPS privileges only.
 *
 * Safe: Only drops from root to target UID/GID. Never escalates.
 * Uses standard POSIX setresuid/setresgid/setgroups.
 *
 * @param uid Target UID.
 * @param gid Target GID.
 * @return 0 on success, -1/-2/-3 on respective stage failure.
 */
int rustdroid_c_switch_credentials(uid_t uid, gid_t gid);

#ifdef __cplusplus
}
#endif

#endif /* RUSTDROID_C_H */
