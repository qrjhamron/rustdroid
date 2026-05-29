# RustDroid Android Manager

This is the Kotlin + Jetpack Compose application designed to provide a rich visual interface for RustDroid root management.

## Key Goals
- **Thin UI Principle**: No core root-level decision-making or binary patching occurs in Kotlin.
- **JNI Integration**: Calls native C/Rust functions bundled inside `librustdroid_core.so` or invokes CLI commands using root/su wrappers.
- **Safe Operations**: Provides a read-only audit analyzer for users to verify they are flashing valid headers.

## High-Premium UI Design
The UI is styled using top.yukonga.miuix.kmp (Miuix) components, featuring:
- Clean MIUI/HyperOS-inspired grouped layout sections.
- Rounded card containers and preference controls.
- Dynamic theme controllers supporting light and dark schemes.

## v1.4 UI Screens

| Tab | Description |
|-----|-------------|
| **Dashboard** | System overview with safety badges, version info, and bridge status |
| **Security** | Security Center with SELinux status, dangerous capabilities, static safety scan results |
| **CGlue** | C Glue Audit screen showing per-file audit status and forbidden symbol checks |
| **Root** | Root status with execution mode, daemon connection, SELinux context |
| **Pending** | Pending root authorization requests queue with approve/deny |
| **Policies** | App permission policy management (add/update/revoke rules) |
| **Modules** | Full module management with ZIP install, toggle, uninstall, script plan, logs |
| **Audit** | Boot image header audit engine |
| **Verify** | Patched ramdisk verification with safety scope checks |
| **Logs** | Multi-log viewer (su, daemon, first_boot, self_check, module) with redaction indicator |
| **PostBoot** | Post-boot audit validation checklist |

## v1.4 Security Features
- **Mock mode banner**: Clearly indicates when no real daemon is connected
- **Safety badges**: All dangerous capabilities shown with DISABLED status
- **Redaction indicator**: Log viewer shows active redaction policy
- **C Glue Audit**: Per-file safety status with forbidden symbol verification
- **Security Center**: Centralized view of all security constraints

## JSON Bridge API
Communication between Compose UI and Rust core utilizes structured JSON payloads via the `com.rustdroid.manager.NativeBridge` JNI mapping. Business logic is strictly kept inside the Rust core to ensure auditability and security boundary checks.

### v1.4 New JNI Methods
- `nativeGetSecurityStatus()` — Returns security dashboard data
- `nativeGetCGlueAudit()` — Returns C glue file audit results
- `nativeGetStaticSafetyReport()` — Returns static safety scan report
- `nativeGetUiSafetyScope()` — Returns UI safety badges and capabilities
- `nativeGetRedactionPolicy()` — Returns redaction policy configuration
- `nativeValidateNativeBridgeState(json)` — Validates bridge state input

## Safety Constraints Enforced
- No automatic flashing of partitions or rebooting.
- No attestation/Play Integrity/banking/anti-cheat bypasses.
- No stealth or root hiding modules implemented.
- Module mounting is not implemented in this build.
- Module scripts are not executed.
- SELinux is read-only (no modification).
