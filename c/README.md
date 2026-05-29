# C Glue Layer

This directory houses minimal native C compatibility functions for low-level platform APIs.

## v1.4 Audit Status

| File | Status | Description |
|------|--------|-------------|
| `android_glue.c` | ✅ **SAFE** | Read-only Android property access |
| `mount_glue.c` | ⛔ **DISABLED** | Bind mount disabled via `RUSTDROID_V14_MOUNT_DISABLED` |
| `selinux_glue.c` | 🔒 **READ-ONLY** | SELinux read-only inspection |
| `process_glue.c` | ⚠️ **RESTRICTED** | Credential dropping only, no escalation |

## Directory Structure
* **`include/rustdroid_c.h`**: Shared FFI function prototypes with safety documentation.
* **`src/android_glue.c`**: Connects with Android native environment properties (`__system_property_get`).
* **`src/mount_glue.c`**: ~~Calls POSIX `mount`~~ **DISABLED in v1.4** — mount syscall compiled out.
* **`src/selinux_glue.c`**: Reads SELinux enforcing status and process context (read-only).
* **`src/process_glue.c`**: Manages process credentials (`setresuid`, `setresgid`, `setgroups`).

## Design Policies
1. **Zero Core Logic**: No parsing, rules evaluation, module verification, or state tracking is done in C.
2. **Safe FFI**: Every function takes primitive types or standard null-terminated `const char*` strings.
3. **No Panics**: All routines return integer status codes (0 for success, non-zero for failure) and set `errno` where possible.
4. **Host Fallbacks**: Every platform-dependent syscall has dummy/mock fallbacks for local test suite compatibility.

## v1.4 Security Hardening

All C source files have been audited and hardened with:
- Comprehensive safety documentation (per-function and per-file)
- Pointer and buffer length validation
- Explicit lists of forbidden operations
- `RUSTDROID_V14_MOUNT_DISABLED` compile guard for mount operations
- No `system()`, `popen()`, `execve()`, `setenforce`, or `pivot_root` calls
- No `/dev/block` access or block device writes
- No SELinux modification (read-only inspection only)
- No exploit-based privilege escalation

## Forbidden Symbols (Verified Clean)

The following symbols are verified to NOT exist in compiled C glue code:
- `setenforce` — SELinux modification (FORBIDDEN)
- `pivot_root` — Namespace manipulation (FORBIDDEN)
- `system(` — Shell execution (FORBIDDEN)
- `popen(` — Shell pipe execution (FORBIDDEN)
- `execve` — Process execution (FORBIDDEN)
- `reboot` — Device reboot (FORBIDDEN)
- `fastboot` — Bootloader flashing (FORBIDDEN)
- `/dev/block` — Block device access (FORBIDDEN)
