use serde::{Deserialize, Serialize};
use thiserror::Error;
use std::io::{Read, Write};

pub const RUSTDROID_IPC_VERSION: u32 = 1;
pub const MAX_MESSAGE_SIZE: u32 = 1048576; // 1MB limit to prevent memory exhaustion DoS

/// Central directories and paths for RustDroid
pub const SOCKET_PATH_DEFAULT: &str = "/data/adb/rustdroid/rustdroidd.sock";
pub const POLICY_FILE_NAME: &str = "policy.json";
pub const MODULES_DIR_NAME: &str = "modules";
pub const LOGS_DIR_NAME: &str = "logs";
pub const RUN_DIR_NAME: &str = "run";
pub const DISABLE_MODULES_FLAG_NAME: &str = "disable_modules";

// Legacy path constants for backward compatibility across modules
pub const RUSTDROID_BASE_DIR: &str = "/data/adb/rustdroid";
pub const POLICY_FILE_PATH: &str = "/data/adb/rustdroid/policy.json";
pub const MODULES_DIR_PATH: &str = "/data/adb/rustdroid/modules";
pub const LOGS_DIR_PATH: &str = "/data/adb/rustdroid/logs";
pub const SOCKET_PATH: &str = "/data/adb/rustdroid/rustdroidd.sock";
pub const DISABLE_MODULES_FLAG: &str = "/data/adb/rustdroid/disable_modules";

/// Resolves the socket path dynamically, respecting host test overrides
pub fn get_socket_path() -> String {
    std::env::var("RUSTDROID_SOCKET_PATH").unwrap_or_else(|_| SOCKET_PATH_DEFAULT.to_string())
}

/// Resolves the base data directory dynamically, respecting host test overrides
pub fn get_data_dir() -> String {
    std::env::var("RUSTDROID_DATA_DIR").unwrap_or_else(|_| "/data/adb/rustdroid".to_string())
}

/// Detailed enum for workspace errors
#[derive(Error, Debug, Serialize, Deserialize, Clone, PartialEq)]
pub enum RustDroidError {
    #[error("I/O Error: {0}")]
    Io(String),

    #[error("Serialization Error: {0}")]
    Serialization(String),

    #[error("Daemon Connection Failed: {0}")]
    DaemonConnection(String),

    #[error("Permission Denied: {0}")]
    PermissionDenied(String),

    #[error("Boot Image Parsing Error: {0}")]
    BootImageInvalid(String),

    #[error("Module Error: {0}")]
    ModuleFailure(String),

    #[error("Mount Compatibility Error: {0}")]
    MountError(String),

    #[error("Unknown/Internal Error: {0}")]
    Internal(String),

    #[error("Protocol Error: {0}")]
    Protocol(String),
}

/// Decision state for su requests
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum SuDecision {
    Allow,
    Deny,
    Ask,
}

/// Execution mode for SU commands (v0.5 Real Execution)
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub enum ExecutionMode {
    DryRun,
    Execute,
}

/// Execution permission policy details (v0.5 Real Execution)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ExecutionPolicy {
    pub allow_shell: bool,
    pub allow_command: bool,
    pub require_tty: bool,
    pub max_runtime_ms: u64,
    pub capture_output: bool,
}

impl Default for ExecutionPolicy {
    fn default() -> Self {
        Self {
            allow_shell: false,
            allow_command: true,
            require_tty: false,
            max_runtime_ms: 10000, // Default 10 seconds timeout limit
            capture_output: true,
        }
    }
}

/// Claimed Identity of the calling client processes
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ClaimedClientIdentity {
    pub uid: u32,
    pub gid: u32,
    pub pid: i32,
    pub selinux_context: String,
    pub package_name: Option<String>,
}

/// Verified Identity derived from socket peer credentials
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct VerifiedClientIdentity {
    pub verified_uid: u32,
    pub verified_pid: i32,
    pub verified_gid: u32,
    pub claimed_package: Option<String>,
}

/// Details of command execution requests
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CommandRequest {
    pub args: Vec<String>,
    pub env: Vec<(String, String)>,
}

/// Request sent by `rustdroid-su` client to `rustdroid-daemon` over socket FFI
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct SuRequest {
    pub protocol_version: u32,
    pub identity: ClaimedClientIdentity,
    pub command: CommandRequest,
    pub execution_mode: ExecutionMode,
}

/// Response returned by `rustdroid-daemon` to `rustdroid-su` client
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct SuResponse {
    pub protocol_version: u32,
    pub allowed: bool,
    pub decision: SuDecision,
    pub reason: String,
    pub session_id: Option<String>,
    pub error: Option<RustDroidError>,
    // v0.5 real execution response fields
    pub execution_started: bool,
    pub exit_code: Option<i32>,
    pub stdout_preview: Option<String>,
    pub stderr_preview: Option<String>,
    pub execution_error: Option<String>,
}

/// Policy rule durability types
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum PolicyRuleType {
    Once,
    UntilReboot,
    ForDuration { seconds: u64 },
    Always,
}

/// Standardized policy entry representation (extended for v0.5 with ExecutionPolicy)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PolicyEntry {
    pub uid: u32,
    pub package_name: String,
    pub state: SuDecision,
    pub rule_type: PolicyRuleType,
    pub created_at: u64,
    pub expires_at: Option<u64>,
    pub execution_policy: Option<ExecutionPolicy>,
}

/// Pending root permission request ID
pub type PendingRequestId = String;

/// Standardized representation of pending root authorization requests
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PendingRootRequest {
    pub request_id: PendingRequestId,
    pub verified_identity: VerifiedClientIdentity,
    pub command: CommandRequest,
    pub created_at: u64,
    pub timeout_secs: u64,
}

/// Decisions returned by Android Manager UI
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ManagerDecision {
    Approve { rule_type: PolicyRuleType },
    Deny,
}

/// Source of policy validation decisions
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum PolicyDecisionSource {
    PersistentPolicy,
    SessionPolicy,
    ManagerApproval,
    DefaultDeny,
    TimeoutDeny,
}

/// Manager request IPC payload
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ManagerRequest {
    GetRootStatus,
    ListPolicies,
    SetPolicy {
        uid: u32,
        package_name: String,
        state: SuDecision,
        rule_type: PolicyRuleType,
        execution_policy: Option<ExecutionPolicy>,
    },
    RemovePolicy {
        uid: u32,
    },
    ListPendingRequests,
    ApprovePendingRequest {
        request_id: String,
        rule_type: PolicyRuleType,
    },
    DenyPendingRequest {
        request_id: String,
    },
    GetAuditLogTail {
        log_name: String,
        tail_lines: usize,
    },
    AuditBootImage {
        image_path: String,
    },
}

/// Manager response IPC payload
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ManagerResponse {
    RootStatus {
        is_patched: bool,
        selinux_mode: String,
        version: String,
    },
    Policies(Vec<PolicyEntry>),
    PendingRequests(Vec<PendingRootRequest>),
    LogsTail(String),
    BootAudit(String),
    Success,
    Error(String),
}

/// Combined IPC payload containing explicit message variants
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum IpcMessage {
    Su(SuRequest),
    Manager(ManagerRequest),
}

/// Combined IPC response containing explicit message variants
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum IpcResponse {
    Su(SuResponse),
    Manager(ManagerResponse),
}

/// Redact full session token to only include first 4 characters for security auditing
pub fn redact_token(token: &str) -> String {
    if token.len() <= 4 {
        token.to_string()
    } else {
        format!("{}...", &token[..4])
    }
}

/// Retrieve verified Unix socket peer process credentials (UID/PID/GID)
pub fn get_peer_credentials(stream: &std::os::unix::net::UnixStream) -> Result<(u32, i32, u32), RustDroidError> {
    if let Ok(uid_str) = std::env::var("RUSTDROID_MOCK_PEER_UID") {
        let uid = uid_str.parse().unwrap_or(10001);
        let pid = std::env::var("RUSTDROID_MOCK_PEER_PID").ok().and_then(|s| s.parse().ok()).unwrap_or(1234);
        let gid = std::env::var("RUSTDROID_MOCK_PEER_GID").ok().and_then(|s| s.parse().ok()).unwrap_or(10001);
        return Ok((uid, pid, gid));
    }

    #[cfg(any(target_os = "linux", target_os = "android"))]
    {
        use std::os::unix::io::AsRawFd;
        let fd = stream.as_raw_fd();
        let mut ucred = libc::ucred {
            pid: 0,
            uid: 0,
            gid: 0,
        };
        let mut len = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
        let res = unsafe {
            libc::getsockopt(
                fd,
                libc::SOL_SOCKET,
                libc::SO_PEERCRED,
                &mut ucred as *mut _ as *mut libc::c_void,
                &mut len,
            )
        };
        if res == 0 {
            return Ok((ucred.uid as u32, ucred.pid as i32, ucred.gid as u32));
        }
    }
    
    // Fallback/Default
    Ok((10001, 1234, 10001))
}

/// Write serializable structures into stream using length-prefix framing
pub fn write_prefixed_message<W: Write, T: Serialize>(writer: &mut W, value: &T) -> Result<(), RustDroidError> {
    let json_bytes = serde_json::to_vec(value)
        .map_err(|e| RustDroidError::Serialization(e.to_string()))?;
    
    let len = json_bytes.len() as u32;
    writer.write_all(&len.to_be_bytes())
        .map_err(|e| RustDroidError::Io(e.to_string()))?;
    writer.write_all(&json_bytes)
        .map_err(|e| RustDroidError::Io(e.to_string()))?;
    writer.flush().map_err(|e| RustDroidError::Io(e.to_string()))?;
    Ok(())
}

/// Read structures from stream decoding length-prefix framing and enforcing bounds check
pub fn read_prefixed_message<R: Read, T: for<'a> Deserialize<'a>>(reader: &mut R) -> Result<T, RustDroidError> {
    let mut len_bytes = [0u8; 4];
    reader.read_exact(&mut len_bytes)
        .map_err(|e| RustDroidError::Io(e.to_string()))?;
    
    let len = u32::from_be_bytes(len_bytes);
    if len > MAX_MESSAGE_SIZE {
        return Err(RustDroidError::Protocol(format!(
            "Oversized message rejected: {} bytes (max limit {})",
            len, MAX_MESSAGE_SIZE
        )));
    }
    
    let mut buf = vec![0u8; len as usize];
    reader.read_exact(&mut buf)
        .map_err(|e| RustDroidError::Io(e.to_string()))?;
    
    let value = serde_json::from_slice(&buf)
        .map_err(|e| RustDroidError::Serialization(e.to_string()))?;
    Ok(value)
}

// ==========================================
// v1.5 Device Compatibility Matrix Data Model
// ==========================================

/// Compatibility level for device/image combinations
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum CompatibilityLevel {
    SupportedForOfflinePatch,
    SupportedForAdbValidation,
    ManualBootValidationPossible,
    PartialSupport,
    Blocked,
    Unknown,
}

/// Blocker preventing compatibility
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CompatibilityBlocker {
    pub code: String,
    pub message: String,
    pub severity: String,
    pub remediation_hint: String,
}

/// Warning about compatibility
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CompatibilityWarning {
    pub code: String,
    pub message: String,
    pub severity: String,
}

/// Safety scope fields for compatibility reports
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct SafetyScope {
    pub auto_flash: bool,
    pub auto_reboot: bool,
    pub block_device_write: bool,
    pub bypass_enabled: bool,
    pub hiding_enabled: bool,
    pub module_mounting_enabled: bool,
    pub script_execution_enabled: bool,
    pub manual_validation_only: bool,
}

impl Default for SafetyScope {
    fn default() -> Self {
        Self {
            auto_flash: false,
            auto_reboot: false,
            block_device_write: false,
            bypass_enabled: false,
            hiding_enabled: false,
            module_mounting_enabled: false,
            script_execution_enabled: false,
            manual_validation_only: true,
        }
    }
}

/// Full device compatibility report
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct DeviceCompatibilityReport {
    pub report_version: u32,
    pub generated_at: u64,
    pub device_source: String,
    pub android_release: String,
    pub sdk_version: u32,
    pub device_model: String,
    pub device_brand: String,
    pub device_product: String,
    pub device_codename: String,
    pub cpu_arch: String,
    pub abi_list: String,
    pub kernel_release: String,
    pub boot_image_header_version: u32,
    pub image_type: String,
    pub ramdisk_compression: String,
    pub ramdisk_roundtrip_supported: bool,
    pub cpio_valid: bool,
    pub init_import_supported: bool,
    pub payload_arch_supported: bool,
    pub selinux_context_readable: bool,
    pub runtime_layout_supported: bool,
    pub adb_validation_supported: bool,
    pub manual_boot_validation_supported: bool,
    pub cloud_phone_limited: bool,
    pub compatibility_level: CompatibilityLevel,
    pub blockers: Vec<CompatibilityBlocker>,
    pub warnings: Vec<CompatibilityWarning>,
    pub recommendations: Vec<String>,
    pub safety_scope: SafetyScope,
}

/// Runtime compatibility report
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct RuntimeCompatibilityReport {
    pub report_version: u32,
    pub generated_at: u64,
    pub runtime_layout_exists: bool,
    pub config_exists: bool,
    pub install_state_exists: bool,
    pub logs_dir_exists: bool,
    pub daemon_self_check_passed: Option<bool>,
    pub su_self_check_passed: Option<bool>,
    pub execution_enabled: bool,
    pub module_mounting_enabled: bool,
    pub bypass_enabled: bool,
    pub hiding_enabled: bool,
    pub c_glue_audit_status: String,
    pub static_safety_status: String,
    pub safety_scope: SafetyScope,
}

/// Release readiness level
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ReleaseReadinessLevel {
    ReadyForInternalAlpha,
    BlockedByTests,
    BlockedBySecurityScan,
    BlockedByBuild,
    BlockedBySafetyScope,
    Unknown,
}

/// Release gate report
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ReleaseGateReport {
    pub report_version: u32,
    pub generated_at: u64,
    pub tests_passed: bool,
    pub warnings_zero: bool,
    pub security_scan_clean: bool,
    pub c_glue_audit_clean: bool,
    pub android_arm64_build_passed: bool,
    pub android_manager_build_passed: bool,
    pub payload_packaged: bool,
    pub metadata_hashes_present: bool,
    pub safety_scope_valid: bool,
    pub no_auto_flash: bool,
    pub no_auto_reboot: bool,
    pub no_block_device_write: bool,
    pub no_bypass: bool,
    pub no_root_hiding: bool,
    pub no_module_mounting: bool,
    pub no_script_execution: bool,
    pub readiness_level: ReleaseReadinessLevel,
    pub blockers: Vec<String>,
    pub safety_scope: SafetyScope,
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn test_ipc_serialization_deserialization() {
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10001,
                gid: 10001,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test".to_string()),
            },
            command: CommandRequest {
                args: vec!["id".to_string()],
                env: vec![("PATH".to_string(), "/system/bin".to_string())],
            },
            execution_mode: ExecutionMode::DryRun,
        });

        let mut buffer = Vec::new();
        write_prefixed_message(&mut buffer, &request).unwrap();

        let mut reader = Cursor::new(buffer);
        let decoded: IpcMessage = read_prefixed_message(&mut reader).unwrap();

        if let IpcMessage::Su(su_req) = decoded {
            assert_eq!(su_req.protocol_version, RUSTDROID_IPC_VERSION);
            assert_eq!(su_req.identity.uid, 10001);
            assert_eq!(su_req.command.args[0], "id");
            assert_eq!(su_req.execution_mode, ExecutionMode::DryRun);
        } else {
            panic!("Expected Su variant");
        }
    }

    #[test]
    fn test_schema_safe_serialization() {
        let pending = PendingRootRequest {
            request_id: "req_123".to_string(),
            verified_identity: VerifiedClientIdentity {
                verified_uid: 10001,
                verified_pid: 1234,
                verified_gid: 10001,
                claimed_package: Some("com.example".to_string()),
            },
            command: CommandRequest {
                args: vec!["ls".to_string()],
                env: vec![],
            },
            created_at: 100000,
            timeout_secs: 30,
        };
        let serialized = serde_json::to_string(&pending).unwrap();
        let deserialized: PendingRootRequest = serde_json::from_str(&serialized).unwrap();
        assert_eq!(pending, deserialized);

        let policy = PolicyEntry {
            uid: 10001,
            package_name: "com.example".to_string(),
            state: SuDecision::Allow,
            rule_type: PolicyRuleType::ForDuration { seconds: 300 },
            created_at: 100000,
            expires_at: Some(100300),
            execution_policy: Some(ExecutionPolicy {
                allow_shell: false,
                allow_command: true,
                require_tty: false,
                max_runtime_ms: 5000,
                capture_output: true,
            }),
        };
        let serialized_policy = serde_json::to_string(&policy).unwrap();
        let deserialized_policy: PolicyEntry = serde_json::from_str(&serialized_policy).unwrap();
        assert_eq!(policy, deserialized_policy);
    }

    #[test]
    fn test_token_redaction() {
        assert_eq!(redact_token("123"), "123");
        assert_eq!(redact_token("abcdefgh"), "abcd...");
    }

    #[test]
    fn test_reject_oversized_message() {
        let mut buffer = Vec::new();
        // Construct fake header indicating a huge message (2MB)
        let huge_len = 2000000u32;
        buffer.extend_from_slice(&huge_len.to_be_bytes());
        buffer.extend_from_slice(&vec![0u8; 100]); // dummy payload bytes

        let mut reader = Cursor::new(buffer);
        let res: Result<IpcMessage, _> = read_prefixed_message(&mut reader);
        assert!(res.is_err());
        if let Err(RustDroidError::Protocol(msg)) = res {
            assert!(msg.contains("Oversized message rejected"));
        } else {
            panic!("Expected Protocol error");
        }
    }

    #[test]
    fn test_v15_compatibility_report_serialization() {
        let report = DeviceCompatibilityReport {
            report_version: 1,
            generated_at: 1716000000,
            device_source: "offline_analysis".to_string(),
            android_release: "14".to_string(),
            sdk_version: 34,
            device_model: "Pixel 8".to_string(),
            device_brand: "Google".to_string(),
            device_product: "shiba".to_string(),
            device_codename: "shiba".to_string(),
            cpu_arch: "aarch64".to_string(),
            abi_list: "arm64-v8a,armeabi-v7a,armeabi".to_string(),
            kernel_release: "6.1.25-android14-4-gxxxx".to_string(),
            boot_image_header_version: 4,
            image_type: "init_boot".to_string(),
            ramdisk_compression: "LZ4".to_string(),
            ramdisk_roundtrip_supported: true,
            cpio_valid: true,
            init_import_supported: true,
            payload_arch_supported: true,
            selinux_context_readable: true,
            runtime_layout_supported: true,
            adb_validation_supported: true,
            manual_boot_validation_supported: true,
            cloud_phone_limited: false,
            compatibility_level: CompatibilityLevel::SupportedForOfflinePatch,
            blockers: vec![],
            warnings: vec![],
            recommendations: vec!["Use fastboot to flash init_boot to apply patch".to_string()],
            safety_scope: SafetyScope::default(),
        };
        let json = serde_json::to_string(&report).unwrap();
        let decoded: DeviceCompatibilityReport = serde_json::from_str(&json).unwrap();
        assert_eq!(report, decoded);
        assert_eq!(decoded.safety_scope.auto_flash, false);
        assert_eq!(decoded.safety_scope.manual_validation_only, true);
    }

    #[test]
    fn test_v15_release_gate_report_serialization() {
        let report = ReleaseGateReport {
            report_version: 1,
            generated_at: 1716000000,
            tests_passed: true,
            warnings_zero: true,
            security_scan_clean: true,
            c_glue_audit_clean: true,
            android_arm64_build_passed: true,
            android_manager_build_passed: true,
            payload_packaged: true,
            metadata_hashes_present: true,
            safety_scope_valid: true,
            no_auto_flash: true,
            no_auto_reboot: true,
            no_block_device_write: true,
            no_bypass: true,
            no_root_hiding: true,
            no_module_mounting: true,
            no_script_execution: true,
            readiness_level: ReleaseReadinessLevel::ReadyForInternalAlpha,
            blockers: vec![],
            safety_scope: SafetyScope::default(),
        };
        let json = serde_json::to_string(&report).unwrap();
        let decoded: ReleaseGateReport = serde_json::from_str(&json).unwrap();
        assert_eq!(report, decoded);
    }

    #[test]
    fn test_v15_safety_scope_defaults() {
        let scope = SafetyScope::default();
        assert_eq!(scope.auto_flash, false);
        assert_eq!(scope.auto_reboot, false);
        assert_eq!(scope.block_device_write, false);
        assert_eq!(scope.bypass_enabled, false);
        assert_eq!(scope.hiding_enabled, false);
        assert_eq!(scope.module_mounting_enabled, false);
        assert_eq!(scope.script_execution_enabled, false);
        assert_eq!(scope.manual_validation_only, true);
    }
}
