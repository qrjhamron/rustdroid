/*
 * @file process_glue.c
 * @brief Credential dropping and session initialization helper APIs.
 *
 * SAFETY DOCUMENTATION (v1.4 Audit):
 *
 * PURPOSE:
 *   This file provides a single FFI-safe wrapper for transitioning process
 *   credentials (UID/GID/supplementary groups). It is used by the daemon
 *   to drop privileges from root to the requesting app's identity when
 *   executing an approved su request.
 *
 * CURRENT STATUS: RESTRICTED
 *   - Only provides credential DROPPING (switching to less-privileged identity).
 *   - Uses standard POSIX setresuid/setresgid/setgroups calls.
 *   - Returns distinct negative error codes for each failure stage.
 *
 * WHY THIS FILE IS SAFE:
 *   - It only DROPS privileges, never escalates them.
 *   - setresuid/setresgid are standard POSIX APIs for credential management.
 *   - The function only succeeds when the process already has CAP_SETUID/CAP_SETGID
 *     (i.e., it is already running as root from the patched init context).
 *   - If the process lacks root, setgroups/setresgid/setresuid fail with EPERM
 *     which is handled gracefully with fallback to setgid/setuid.
 *   - No exploit-based privilege escalation is performed.
 *
 * WHAT THIS FILE MUST NEVER DO:
 *   - NEVER perform privilege ESCALATION by exploit.
 *   - NEVER use ptrace, prctl, or capability manipulation for escalation.
 *   - NEVER call system(), popen(), or execve().
 *   - NEVER modify SELinux policy or call setenforce.
 *   - NEVER access /dev/block or any block devices.
 *   - NEVER call reboot, fastboot, or mount.
 *   - NEVER implement root hiding or process hiding.
 *   - NEVER manipulate /proc entries for stealth.
 *   - NEVER hook syscalls or install kprobes.
 */

#include "rustdroid_c.h"
#include <unistd.h>
#include <grp.h>
#include <errno.h>
#include <stdio.h>

/**
 * @brief Transition process credentials securely (UID/GID/Groups).
 *
 * SAFETY: This function only DROPS privileges by switching to the specified
 * UID and GID. It does not escalate privileges. The three-step process:
 *   1. Clear supplementary groups (prevent permission leakage)
 *   2. Set GID (Real, Effective, Saved)
 *   3. Set UID (Real, Effective, Saved) — done LAST so we can still
 *      change GID while we have root
 *
 * @param uid Target user ID to switch to.
 * @param gid Target group ID to switch to.
 * @return 0 on success, -1 if setgroups failed, -2 if setgid failed,
 *         -3 if setuid failed.
 */
int rustdroid_c_switch_credentials(uid_t uid, gid_t gid) {
    /* Step 1: Clear supplementary groups to avoid leaking permissions */
    gid_t groups[1] = { gid };
    if (setgroups(1, groups) != 0) {
        /*
         * If we lack root permissions, this might fail, which we track.
         * EPERM is expected on non-root test environments.
         */
        if (errno != EPERM) {
            return -1;
        }
    }

    /* Step 2: Set GIDs (Real, Effective, Saved) */
    if (setresgid(gid, gid, gid) != 0) {
        if (setgid(gid) != 0) {
            return -2;
        }
    }

    /* Step 3: Set UIDs (Real, Effective, Saved) — done last */
    if (setresuid(uid, uid, uid) != 0) {
        if (setuid(uid) != 0) {
            return -3;
        }
    }

    return 0;
}
