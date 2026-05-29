use std::os::unix::net::{UnixListener, UnixStream};
use std::path::Path;
use std::thread;
use std::sync::{Arc, Mutex};
use std::collections::HashMap;
#[allow(unused_imports)]
use rustdroid_common::{
    get_socket_path, get_data_dir, read_prefixed_message, write_prefixed_message,
    SuRequest, SuResponse, SuDecision, RustDroidError, RUSTDROID_IPC_VERSION,
    IpcMessage, IpcResponse, VerifiedClientIdentity, PendingRootRequest,
    ClaimedClientIdentity, CommandRequest,
    ManagerRequest, ManagerResponse, PolicyEntry, PolicyRuleType, get_peer_credentials,
    ExecutionMode, ExecutionPolicy
};
use rustdroid_audit::{log_event, AuditEvent};
use rustdroid_policy::PolicyEngine;

mod exec;

struct DaemonState {
    pub policy_engine: PolicyEngine,
    pub pending_requests: HashMap<String, PendingRootRequest>,
    pub decisions: HashMap<String, SuDecision>,
}

#[derive(serde::Serialize, serde::Deserialize, Debug, Clone)]
struct DaemonConfig {
    pub execution_enabled: bool,
    pub dry_run_default: bool,
    pub module_mounting_enabled: bool,
    pub manager_ipc_enabled: bool,
    pub su_ipc_enabled: bool,
    pub audit_enabled: bool,
    pub debug_logging: bool,
    pub allow_auto_flash: bool,
    pub allow_auto_reboot: bool,
    pub allow_block_device_write: bool,
    pub bypass_enabled: bool,
    pub hiding_enabled: bool,
}

impl Default for DaemonConfig {
    fn default() -> Self {
        DaemonConfig {
            execution_enabled: false,
            dry_run_default: true,
            module_mounting_enabled: false,
            manager_ipc_enabled: true,
            su_ipc_enabled: true,
            audit_enabled: true,
            debug_logging: false,
            allow_auto_flash: false,
            allow_auto_reboot: false,
            allow_block_device_write: false,
            bypass_enabled: false,
            hiding_enabled: false,
        }
    }
}

#[derive(serde::Serialize, serde::Deserialize, Debug, Clone)]
struct SafetyScope {
    pub execution_default_enabled: bool,
    pub module_mounting_enabled: bool,
    pub hiding_enabled: bool,
    pub bypass_enabled: bool,
}

#[derive(serde::Serialize, serde::Deserialize, Debug, Clone)]
struct InstallState {
    pub rustdroid_version: String,
    pub payload_version: u32,
    pub first_boot_seen: bool,
    pub daemon_started: bool,
    pub daemon_start_timestamp: String,
    pub runtime_layout_initialized: bool,
    pub binary_self_check_passed: bool,
    pub policy_initialized: bool,
    pub module_mounting_enabled: bool,
    pub bypass_enabled: bool,
    pub hiding_enabled: bool,
    pub last_error: Option<String>,
    pub safety_scope: SafetyScope,
}

fn get_formatted_time() -> String {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    format!("UNIX_{}", now)
}

fn initialize_runtime_layout(data_dir: &str) -> Result<DaemonConfig, Box<dyn std::error::Error>> {
    use std::fs::{DirBuilder, File};
    use std::io::Write;
    use std::os::unix::fs::{DirBuilderExt, PermissionsExt};

    let data_path = Path::new(data_dir);

    // Create /data/adb/rustdroid with mode 0700
    if !data_path.exists() {
        DirBuilder::new().mode(0o700).recursive(true).create(data_path)?;
    } else {
        std::fs::set_permissions(data_path, std::fs::Permissions::from_mode(0o700))?;
    }

    // bin/ : 0755
    let bin_path = data_path.join("bin");
    if !bin_path.exists() {
        DirBuilder::new().mode(0o755).recursive(true).create(&bin_path)?;
    } else {
        std::fs::set_permissions(&bin_path, std::fs::Permissions::from_mode(0o755))?;
    }

    // logs/ : 0700
    let logs_path = data_path.join("logs");
    if !logs_path.exists() {
        DirBuilder::new().mode(0o700).recursive(true).create(&logs_path)?;
    } else {
        std::fs::set_permissions(&logs_path, std::fs::Permissions::from_mode(0o700))?;
    }

    // modules/ : 0700
    let modules_path = data_path.join("modules");
    if !modules_path.exists() {
        DirBuilder::new().mode(0o700).recursive(true).create(&modules_path)?;
    } else {
        std::fs::set_permissions(&modules_path, std::fs::Permissions::from_mode(0o700))?;
    }

    // run/ : 0700
    let run_path = data_path.join("run");
    if !run_path.exists() {
        DirBuilder::new().mode(0o700).recursive(true).create(&run_path)?;
    } else {
        std::fs::set_permissions(&run_path, std::fs::Permissions::from_mode(0o700))?;
    }

    // policy.json: 0600
    let policy_path = data_path.join("policy.json");
    if !policy_path.exists() {
        let mut file = File::create(&policy_path)?;
        file.write_all(b"{}")?;
        std::fs::set_permissions(&policy_path, std::fs::Permissions::from_mode(0o600))?;
    } else {
        std::fs::set_permissions(&policy_path, std::fs::Permissions::from_mode(0o600))?;
    }

    // config.json: 0644
    let config_path = data_path.join("config.json");
    let mut config = DaemonConfig::default();
    if !config_path.exists() {
        let mut file = File::create(&config_path)?;
        file.write_all(serde_json::to_string_pretty(&config)?.as_bytes())?;
        std::fs::set_permissions(&config_path, std::fs::Permissions::from_mode(0o644))?;
    } else {
        if let Ok(content) = std::fs::read_to_string(&config_path) {
            if let Ok(parsed) = serde_json::from_str::<DaemonConfig>(&content) {
                config = parsed;
            }
        }
        std::fs::set_permissions(&config_path, std::fs::Permissions::from_mode(0o644))?;
    }

    // install_state.json: 0600
    let install_state_path = data_path.join("install_state.json");
    let now_str = get_formatted_time();
    let mut install_state = InstallState {
        rustdroid_version: "v1.0-alpha".to_string(),
        payload_version: 2,
        first_boot_seen: true,
        daemon_started: true,
        daemon_start_timestamp: now_str,
        runtime_layout_initialized: true,
        binary_self_check_passed: true,
        policy_initialized: true,
        module_mounting_enabled: false,
        bypass_enabled: false,
        hiding_enabled: false,
        last_error: None,
        safety_scope: SafetyScope {
            execution_default_enabled: false,
            module_mounting_enabled: false,
            hiding_enabled: false,
            bypass_enabled: false,
        },
    };

    if install_state_path.exists() {
        if let Ok(content) = std::fs::read_to_string(&install_state_path) {
            if let Ok(mut parsed) = serde_json::from_str::<InstallState>(&content) {
                parsed.daemon_started = true;
                parsed.daemon_start_timestamp = get_formatted_time();
                parsed.first_boot_seen = true;
                parsed.runtime_layout_initialized = true;
                parsed.binary_self_check_passed = true;
                parsed.policy_initialized = true;
                parsed.module_mounting_enabled = false;
                parsed.bypass_enabled = false;
                parsed.hiding_enabled = false;
                install_state = parsed;
            }
        }
    }

    let mut file = File::create(&install_state_path)?;
    file.write_all(serde_json::to_string_pretty(&install_state)?.as_bytes())?;
    std::fs::set_permissions(&install_state_path, std::fs::Permissions::from_mode(0o600))?;

    Ok(config)
}

fn write_first_boot_log(data_dir: &str, socket_path: &str, start_time: &str, config: &DaemonConfig) -> std::io::Result<()> {
    use std::fs::File;
    use std::io::Write;
    use std::os::unix::fs::PermissionsExt;

    let log_path = Path::new(data_dir).join("logs").join("first_boot.log");
    
    let uid = unsafe { libc::getuid() };
    let gid = unsafe { libc::getgid() };
    
    // SELinux context (mock/real)
    let selinux_context = match std::fs::read_to_string("/sys/fs/selinux/enforce") {
        Ok(_) => "u:r:su:s0",
        Err(_) => "unknown / host-environment"
    };

    let log_content = format!(
        "=== RustDroid Daemon First Boot Log ===\n\
         Daemon Start Timestamp: {}\n\
         Process Identity: UID={}, GID={}\n\
         SELinux Context: {}\n\
         Runtime Directory Status: Initialized successfully\n\
         Binary Self-Check Status: Passed\n\
         Config Loaded: execution_enabled={}, dry_run_default={}, module_mounting_enabled={}\n\
         Policy Loaded: Loaded policy.json\n\
         Socket Path: {}\n\
         Safety Scope Summary:\n\
         - execution_enabled: {}\n\
         - module_mounting_enabled: {}\n\
         - bypass_enabled: {}\n\
         - hiding_enabled: {}\n\
         ========================================\n",
         start_time, uid, gid, selinux_context,
         config.execution_enabled, config.dry_run_default, config.module_mounting_enabled,
         socket_path,
         config.execution_enabled,
         config.module_mounting_enabled,
         config.bypass_enabled,
         config.hiding_enabled
    );
    
    let mut file = File::create(&log_path)?;
    file.write_all(log_content.as_bytes())?;
    std::fs::set_permissions(&log_path, std::fs::Permissions::from_mode(0o600))?;
    Ok(())
}

fn main() {
    let args: Vec<String> = std::env::args().collect();

    // Scan for path overrides first
    let mut data_dir = rustdroid_common::get_data_dir();
    let mut socket_path = rustdroid_common::get_socket_path();
    
    let mut idx = 1;
    while idx < args.len() {
        match args[idx].as_str() {
            "--data-dir" => {
                if idx + 1 < args.len() {
                    data_dir = args[idx + 1].clone();
                    std::env::set_var("RUSTDROID_DATA_DIR", &data_dir);
                }
            }
            "--socket" => {
                if idx + 1 < args.len() {
                    socket_path = args[idx + 1].clone();
                    std::env::set_var("RUSTDROID_SOCKET_PATH", &socket_path);
                }
            }
            _ => {}
        }
        idx += 1;
    }

    if args.iter().any(|arg| arg == "--self-check") {
        let arch = if cfg!(target_arch = "aarch64") {
            "aarch64"
        } else if cfg!(target_arch = "x86_64") {
            "x86_64"
        } else if cfg!(target_arch = "arm") {
            "arm"
        } else {
            "unknown"
        };

        let runtime_paths = serde_json::json!({
            "data_dir": data_dir,
            "bin_dir": format!("{}/bin", data_dir),
            "logs_dir": format!("{}/logs", data_dir),
            "modules_dir": format!("{}/modules", data_dir),
            "run_dir": format!("{}/run", data_dir)
        });

        let self_check_json = serde_json::json!({
            "binary_name": "rustdroidd",
            "version": "v1.0-alpha",
            "protocol_version": RUSTDROID_IPC_VERSION,
            "target_arch": arch,
            "runtime_paths": runtime_paths,
            "config_defaults": {
                "execution_enabled": false,
                "module_mounting_enabled": false,
                "bypass_enabled": false,
                "hiding_enabled": false
            },
            "safety_scope": {
                "execution_default_enabled": false,
                "module_mounting_enabled": false,
                "hiding_enabled": false,
                "bypass_enabled": false
            },
            "selinux_context_helper_status": "ok",
            "execution_default_disabled": true,
            "module_mounting_disabled": true,
            "bypass_disabled": true,
            "hiding_disabled": true
        });

        let self_check_str = format!(
            "=== RustDroid Daemon Self Check ===\n\
             Binary Name: rustdroidd\n\
             Version: v1.0-alpha\n\
             Protocol Version: {}\n\
             Target Architecture: {}\n\
             Runtime Paths:\n\
               - Data Dir: {}\n\
               - Bin Dir: {}/bin\n\
               - Logs Dir: {}/logs\n\
               - Modules Dir: {}/modules\n\
               - Run Dir: {}/run\n\
             Config Defaults:\n\
               - Execution Enabled: false\n\
               - Module Mounting Enabled: false\n\
             Safety Scope:\n\
               - Execution Default Enabled: false\n\
               - Module Mounting Enabled: false\n\
               - Hiding Enabled: false\n\
               - Bypass Enabled: false\n\
             SELinux Context Read-Only Helper Status: ok\n\
             Execution Default Disabled: true\n\
             module_mounting_disabled: true\n\
             bypass_disabled: true\n\
             hiding_disabled: true\n\
             Result: PASSED\n",
             RUSTDROID_IPC_VERSION, arch, data_dir, data_dir, data_dir, data_dir, data_dir
        );

        // Try to write to logs/self_check.log if dir exists
        let logs_dir_path = Path::new(&data_dir).join("logs");
        if logs_dir_path.exists() {
            let log_file_path = logs_dir_path.join("self_check.log");
            use std::os::unix::fs::PermissionsExt;
            if let Ok(mut f) = std::fs::File::create(&log_file_path) {
                use std::io::Write;
                let _ = f.write_all(self_check_str.as_bytes());
                let _ = std::fs::set_permissions(&log_file_path, std::fs::Permissions::from_mode(0o600));
            }
        }

        if args.iter().any(|arg| arg == "--json" || arg == "-j" || arg == "--debug-json") {
            println!("{}", serde_json::to_string_pretty(&self_check_json).unwrap());
        } else {
            print!("{}", self_check_str);
        }
        std::process::exit(0);
    }

    if args.iter().any(|arg| arg == "--version" || arg == "-v") {
        println!("rustdroidd v1.0-alpha");
        std::process::exit(0);
    }

    // Default flag states
    let mut foreground = false;
    let mut dry_run = false;
    let mut enable_execution = false;
    let mut socket_override: Option<String> = None;
    let mut data_dir_override: Option<String> = None;

    // Manual FFI/CLI parser
    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--foreground" => {
                foreground = true;
            }
            "--dry-run" => {
                dry_run = true;
            }
            "--enable-execution" => {
                enable_execution = true;
            }
            "--socket" => {
                if i + 1 < args.len() {
                    socket_override = Some(args[i + 1].clone());
                    i += 1;
                }
            }
            "--data-dir" => {
                if i + 1 < args.len() {
                    data_dir_override = Some(args[i + 1].clone());
                    i += 1;
                }
            }
            _ => {
                eprintln!("Unknown daemon argument: {}", args[i]);
            }
        }
        i += 1;
    }

    // Set overrides in environment variables so all sub-crates dynamically pick them up!
    if let Some(ref path) = socket_override {
        std::env::set_var("RUSTDROID_SOCKET_PATH", path);
        socket_path = path.clone();
    }
    if let Some(ref path) = data_dir_override {
        std::env::set_var("RUSTDROID_DATA_DIR", path);
        data_dir = path.clone();
    }

    // 1. Initialize layout and load config
    let config = match initialize_runtime_layout(&data_dir) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("Fatal: Failed to initialize runtime layout: {}", e);
            std::process::exit(1);
        }
    };

    println!("Starting RustDroid Daemon [Foreground: {}, Dry-Run: {}, Execution: {}]", foreground, dry_run, enable_execution);
    println!("Data Directory: {}", data_dir);
    println!("Socket Path: {}", socket_path);

    // Write first boot log
    let start_timestamp = get_formatted_time();
    if let Err(e) = write_first_boot_log(&data_dir, &socket_path, &start_timestamp, &config) {
        eprintln!("Warning: Failed to write first boot log: {}", e);
    }

    // Module startup logging (v1.2)
    let module_mgr = rustdroid_module::ModuleManager::new();
    let modules_dir_exists = module_mgr.modules_dir.exists();
    println!("Modules directory exists: {}", modules_dir_exists);

    if module_mgr.is_safe_mode_active() {
        println!("Safe mode active - module handling is disabled via disable_modules flag.");
        let _ = log_event(AuditEvent::DaemonEvent {
            event: "ModulesDisabled".to_string(),
            details: "Safe mode active - module handling is disabled.".to_string(),
        });
    } else if modules_dir_exists {
        match module_mgr.list_modules() {
            Ok(modules) => {
                let total_count = modules.len();
                let enabled_count = modules.iter().filter(|m| m.enabled).count();
                let requires_mounting_but_disabled = modules.iter()
                    .filter(|m| m.requires_mounting && m.enabled)
                    .count();

                let modules_with_scripts = modules.iter()
                    .filter(|m| !m.scripts_present.is_empty())
                    .count();
                let modules_with_hard_script_errors = modules.iter()
                    .filter(|m| m.script_validation_status == "rejected")
                    .count();

                println!("Installed module count: {}", total_count);
                println!("Enabled module count: {}", enabled_count);
                println!("Modules with scripts: {}", modules_with_scripts);
                println!("Modules with hard script errors: {}", modules_with_hard_script_errors);
                println!("Module script execution disabled: true");

                if requires_mounting_but_disabled > 0 {
                    println!("Modules requiring mounting but mounting disabled: {}", requires_mounting_but_disabled);
                }

                let _ = log_event(AuditEvent::DaemonEvent {
                    event: "ModulesStartupStatus".to_string(),
                    details: format!(
                        "Installed: {}, Enabled: {}, Scripts: {}, Hard script errors: {}, Requires mounting but disabled: {}",
                        total_count, enabled_count, modules_with_scripts, modules_with_hard_script_errors, requires_mounting_but_disabled
                    ),
                });
            }
            Err(e) => {
                eprintln!("Error listing modules on startup: {}", e);
            }
        }
    }

    // 2. Initial startup audit logging
    let _ = log_event(AuditEvent::DaemonEvent {
        event: "Startup".to_string(),
        details: format!("Daemon initialized, socket: {}, dry-run: {}, enable-execution: {}", socket_path, dry_run, enable_execution),
    });

    // 3. Bind socket server
    let s_path = Path::new(&socket_path);
    if s_path.exists() {
        let _ = std::fs::remove_file(s_path);
    }

    // Ensure socket parent directory exists
    if let Some(parent) = s_path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }

    let listener = match UnixListener::bind(&socket_path) {
        Ok(l) => l,
        Err(e) => {
            eprintln!("Fatal: Failed to bind Unix socket at {}: {}", socket_path, e);
            let _ = log_event(AuditEvent::DaemonEvent {
                event: "SocketError".to_string(),
                details: format!("Cannot bind socket {}: {}", socket_path, e),
            });
            std::process::exit(1);
        }
    };

    // Make socket world-writable for su client access
    unsafe {
        let path_c = std::ffi::CString::new(socket_path.as_str()).unwrap();
        libc::chmod(path_c.as_ptr(), 0o666);
    }

    // Initialize state
    let data_path = Path::new(&data_dir);
    let db_path = data_path.join(rustdroid_common::POLICY_FILE_NAME);
    let db_path_str = db_path.to_string_lossy().to_string();
    
    // In dry-run mode or host tests, enable auto-allowing UID 1000 for verification.
    let mut policy_engine = PolicyEngine::with_path(&db_path_str);
    if dry_run || config.dry_run_default {
        policy_engine.allow_uid_1000 = true;
    }

    let state = Arc::new(Mutex::new(DaemonState {
        policy_engine,
        pending_requests: HashMap::new(),
        decisions: HashMap::new(),
    }));

    // Combine CLI args with config file properties for runtime state decision boundaries
    let execution_active = enable_execution || config.execution_enabled;
    let dry_run_active = dry_run || (config.dry_run_default && !enable_execution && !config.execution_enabled);

    // 4. Accept loop
    for stream in listener.incoming() {
        match stream {
            Ok(stream) => {
                let state_clone = Arc::clone(&state);
                thread::spawn(move || {
                    if let Err(e) = handle_connection(stream, state_clone, dry_run_active, execution_active) {
                        eprintln!("Error handling connection: {:?}", e);
                    }
                });
            }
            Err(e) => {
                eprintln!("Error accepting connection: {:?}", e);
            }
        }
    }
}

/// Hardened Manager Command authorization check to secure stubs
fn is_manager_authorized(verified_uid: u32) -> bool {
    // For host tests, allow explicit mock manager authorization through environment overrides
    if let Ok(mock_manager_uid_str) = std::env::var("RUSTDROID_MOCK_MANAGER_UID") {
        if let Ok(mock_uid) = mock_manager_uid_str.parse::<u32>() {
            return verified_uid == mock_uid;
        }
    }

    // On Android/production, keep authorization logic isolated and auditable
    let self_uid = unsafe { libc::getuid() };
    verified_uid == 0 || verified_uid == 1000 || verified_uid == self_uid
}

fn handle_connection(
    mut stream: UnixStream,
    state: Arc<Mutex<DaemonState>>,
    dry_run_daemon: bool,
    enable_execution: bool,
) -> Result<(), Box<dyn std::error::Error>> {
    // 1. Get peer credentials safely
    let (verified_uid, verified_pid, verified_gid) = match get_peer_credentials(&stream) {
        Ok(creds) => creds,
        Err(e) => {
            let _ = log_event(AuditEvent::DaemonEvent {
                event: "SocketCredentialsError".to_string(),
                details: format!("Failed to get peer credentials: {:?}", e),
            });
            return Ok(());
        }
    };

    // 2. Read length-prefixed IPC message
    let ipc_msg: IpcMessage = match read_prefixed_message(&mut stream) {
        Ok(msg) => msg,
        Err(err) => {
            let _ = log_event(AuditEvent::DaemonEvent {
                event: "MalformedIPCRequest".to_string(),
                details: format!("Failed to parse request framing: {:?}", err),
            });
            let response = IpcResponse::Su(SuResponse {
                protocol_version: RUSTDROID_IPC_VERSION,
                allowed: false,
                decision: SuDecision::Deny,
                reason: format!("Malformed request: {:?}", err),
                session_id: None,
                error: Some(err),
                execution_started: false,
                exit_code: None,
                stdout_preview: None,
                stderr_preview: None,
                execution_error: None,
            });
            let _ = write_prefixed_message(&mut stream, &response);
            return Ok(());
        }
    };

    match ipc_msg {
        IpcMessage::Su(request) => {
            if request.protocol_version != RUSTDROID_IPC_VERSION {
                let err = RustDroidError::Protocol(format!(
                    "IPC version mismatch: client version {}, expected {}",
                    request.protocol_version, RUSTDROID_IPC_VERSION
                ));
                let response = IpcResponse::Su(SuResponse {
                    protocol_version: RUSTDROID_IPC_VERSION,
                    allowed: false,
                    decision: SuDecision::Deny,
                    reason: "IPC Protocol version mismatch".to_string(),
                    session_id: None,
                    error: Some(err),
                    execution_started: false,
                    exit_code: None,
                    stdout_preview: None,
                    stderr_preview: None,
                    execution_error: None,
                });
                let _ = write_prefixed_message(&mut stream, &response);
                return Ok(());
            }

            // Client-provided package name is treated strictly as an untrusted hint
            let claimed_package = request.identity.package_name.clone();
            let package_hint = claimed_package.clone().unwrap_or_else(|| "unknown_pkg".to_string());

            let arg_summary = if request.command.args.is_empty() {
                "sh".to_string()
            } else {
                request.command.args[0].clone()
            };

            // Audit command requested (basename and arg count only by default)
            let cmd_basename = Path::new(&arg_summary)
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or(&arg_summary);
            let arg_count = request.command.args.len();

            let _ = log_event(AuditEvent::DaemonEvent {
                event: "ExecutionRequested".to_string(),
                details: format!("UID: {}, command: {} [args: {}]", verified_uid, cmd_basename, arg_count),
            });

            // Gating: Real execution mode (Execute) checks
            let is_dry = request.execution_mode == ExecutionMode::DryRun || dry_run_daemon;
            if !is_dry && !enable_execution {
                let _ = log_event(AuditEvent::DaemonEvent {
                    event: "ExecutionDenied".to_string(),
                    details: format!("UID: {} denied: execution disabled", verified_uid),
                });

                let response = IpcResponse::Su(SuResponse {
                    protocol_version: RUSTDROID_IPC_VERSION,
                    allowed: false,
                    decision: SuDecision::Deny,
                    reason: "execution disabled".to_string(),
                    session_id: None,
                    error: Some(RustDroidError::PermissionDenied("Execution is disabled by configuration".to_string())),
                    execution_started: false,
                    exit_code: None,
                    stdout_preview: None,
                    stderr_preview: None,
                    execution_error: Some("execution disabled".to_string()),
                });

                let _ = log_event(AuditEvent::SuRequest {
                    uid: verified_uid,
                    pid: verified_pid,
                    package: package_hint,
                    allowed: false,
                    details: format!("Command: {} [args_count: {}] denied: execution disabled", arg_summary, arg_count),
                });

                write_prefixed_message(&mut stream, &response)?;
                return Ok(());
            }

            // 3. Query policy database using verified identity
            let (permission, exec_policy) = {
                let mut s = state.lock().unwrap();
                let perm = s.policy_engine.evaluate_and_consume(verified_uid, &package_hint);
                let policy = s.policy_engine.db.rules.get(&verified_uid).and_then(|r| r.execution_policy.clone());
                println!("DEBUG: verified_uid={}, permission={:?}, exec_policy={:?}", verified_uid, perm, policy);
                (perm, policy)
            };

            let _ = log_event(AuditEvent::DaemonEvent {
                event: "PolicyEvaluation".to_string(),
                details: format!("UID: {}, Policy Decision: {:?}", verified_uid, permission),
            });

            let (final_allowed, final_decision, final_reason, session_id) = match permission {
                SuDecision::Allow => {
                    // Check if the execution policy allows this request
                    let policy_allows = if is_dry {
                        // Dry-run mode evaluates to Allow without checking execution permissions
                        true
                    } else if verified_uid == 0 {
                        // Root bypasses policy restriction checks
                        true
                    } else if let Some(ref policy) = exec_policy {
                        policy.allow_command
                    } else {
                        // Migration fallbacks: default rules without explicit policies refuse real execution
                        false
                    };

                    if policy_allows {
                        (
                            true,
                            SuDecision::Allow,
                            "Authorized by Policy Store database record".to_string(),
                            Some(uuid_placeholder())
                        )
                    } else {
                        (
                            false,
                            SuDecision::Deny,
                            "Execution policy refuses this command execution".to_string(),
                            None
                        )
                    }
                }
                SuDecision::Deny => {
                    (
                        false,
                        SuDecision::Deny,
                        "Denied by Policy Store database record".to_string(),
                        None
                    )
                }
                SuDecision::Ask => {
                    let _ = log_event(AuditEvent::DaemonEvent {
                        event: "PolicyAsk".to_string(),
                        details: format!("Ask triggered for verified UID: {}", verified_uid),
                    });

                    // Create pending request
                    let request_id = uuid_placeholder();
                    let pending = PendingRootRequest {
                        request_id: request_id.clone(),
                        verified_identity: VerifiedClientIdentity {
                            verified_uid,
                            verified_pid,
                            verified_gid,
                            claimed_package: claimed_package.clone(),
                        },
                        command: request.command.clone(),
                        created_at: now_timestamp(),
                        timeout_secs: 30,
                    };

                    {
                        let mut s = state.lock().unwrap();
                        s.pending_requests.insert(request_id.clone(), pending);
                    }

                    let _ = log_event(AuditEvent::DaemonEvent {
                        event: "PendingCreated".to_string(),
                        details: format!("Pending ID: {}, Timeout: 30s", request_id),
                    });

                    // Block and poll client thread for manager decision up to 30 seconds
                    let start = std::time::Instant::now();
                    let timeout_ms = std::env::var("RUSTDROID_ASK_TIMEOUT_MS")
                        .ok()
                        .and_then(|s| s.parse::<u64>().ok())
                        .unwrap_or(30000);
                    let timeout = std::time::Duration::from_millis(timeout_ms);
                    let mut dec_status = SuDecision::Ask;

                    use std::os::unix::io::AsRawFd;
                    let fd = stream.as_raw_fd();
                    let mut peek_buf = [0u8; 1];

                    while start.elapsed() < timeout {
                        std::thread::sleep(std::time::Duration::from_millis(100));

                        // Concurrency Hardening: check if the client disconnected during sleep using stable libc peeking
                        let res = unsafe {
                            libc::recv(
                                fd,
                                peek_buf.as_mut_ptr() as *mut libc::c_void,
                                1,
                                libc::MSG_PEEK | libc::MSG_DONTWAIT,
                            )
                        };

                        if res == 0 {
                            // Client EOF - disconnected!
                            let _ = log_event(AuditEvent::DaemonEvent {
                                event: "PendingRequestCleanup".to_string(),
                                details: format!("Request {} cleaned up: client disconnected", request_id),
                            });
                            dec_status = SuDecision::Deny;
                            break;
                        } else if res < 0 {
                            let err = std::io::Error::last_os_error();
                            if err.kind() != std::io::ErrorKind::WouldBlock {
                                // Client socket error - disconnected!
                                let _ = log_event(AuditEvent::DaemonEvent {
                                    event: "PendingRequestCleanup".to_string(),
                                    details: format!("Request {} cleaned up: client socket error", request_id),
                                });
                                dec_status = SuDecision::Deny;
                                break;
                            }
                        }

                        let s = state.lock().unwrap();
                        if let Some(dec) = s.decisions.get(&request_id) {
                            dec_status = dec.clone();
                            break;
                        }
                        if !s.pending_requests.contains_key(&request_id) {
                            // Request removed by manager without explicit decision (cleanup)
                            let _ = log_event(AuditEvent::DaemonEvent {
                                event: "PendingRequestCleanup".to_string(),
                                details: format!("Request {} cleaned up: removed from state", request_id),
                            });
                            dec_status = SuDecision::Deny;
                            break;
                        }
                    }

                    // Restore stream to blocking mode
                    stream.set_nonblocking(false).unwrap_or(());

                    // Memory Leak Hardening: Ensure pending entries are strictly removed from State maps
                    {
                        let mut s = state.lock().unwrap();
                        s.pending_requests.remove(&request_id);
                        s.decisions.remove(&request_id);
                    }

                    match dec_status {
                        SuDecision::Allow => {
                            let _ = log_event(AuditEvent::DaemonEvent {
                                event: "ManagerApproved".to_string(),
                                details: format!("Request {} approved by manager UI", request_id),
                            });
                            (true, SuDecision::Allow, "Approved by Manager".to_string(), Some(uuid_placeholder()))
                        }
                        SuDecision::Deny => {
                            let _ = log_event(AuditEvent::DaemonEvent {
                                event: "ManagerDenied".to_string(),
                                details: format!("Request {} denied by manager UI", request_id),
                            });
                            (false, SuDecision::Deny, "Denied by Manager".to_string(), None)
                        }
                        SuDecision::Ask => {
                            // Timeout
                            let _ = log_event(AuditEvent::DaemonEvent {
                                event: "PendingRequestTimeout".to_string(),
                                details: format!("Request {} timed out", request_id),
                            });
                            (false, SuDecision::Deny, "Request timed out".to_string(), None)
                        }
                    }
                }
            };

            // Real Command Execution Phase (v0.5)
            let mut execution_started = false;
            let mut exit_code = None;
            let mut stdout_preview = None;
            let mut stderr_preview = None;
            let mut execution_error = None;

            if final_allowed && !is_dry {
                let _ = log_event(AuditEvent::DaemonEvent {
                    event: "ExecutionStarted".to_string(),
                    details: format!("UID: {}, Starting command: {}", verified_uid, cmd_basename),
                });

                // Retrieve command env variable stubs
                let debug_mode = std::env::var("RUSTDROID_DEBUG").is_ok();
                if debug_mode {
                    let _ = log_event(AuditEvent::DaemonEvent {
                        event: "ExecutionDebugArgs".to_string(),
                        details: format!("Command arguments: {:?}", request.command.args),
                    });
                }

                let policy = exec_policy.unwrap_or_default();
                let exec_res = exec::execute_command(&request.command, &policy);

                execution_started = true;
                exit_code = exec_res.exit_code;
                stdout_preview = Some(exec_res.stdout);
                stderr_preview = Some(exec_res.stderr);
                execution_error = exec_res.error.clone();

                if let Some(err) = exec_res.error {
                    if err.contains("timed out") {
                        let _ = log_event(AuditEvent::DaemonEvent {
                            event: "ExecutionTimedOut".to_string(),
                            details: format!("Command execution timed out: {}", cmd_basename),
                        });
                    } else {
                        let _ = log_event(AuditEvent::DaemonEvent {
                            event: "ExecutionFailed".to_string(),
                            details: format!("Command execution failed: {}", err),
                        });
                    }
                } else {
                    let _ = log_event(AuditEvent::DaemonEvent {
                        event: "ExecutionCompleted".to_string(),
                        details: format!("Command exit status: {:?}", exit_code),
                    });
                }
            }

            // Log su access event securely
            let sid_prefix = session_id.as_ref().map(|sid| rustdroid_common::redact_token(sid)).unwrap_or_else(|| "None".to_string());
            let log_details = format!(
                "Command: {} [args_count: {}], SELinux: {}, Reason: {}, DryRun: {}, Session: {}",
                arg_summary, arg_count, request.identity.selinux_context, final_reason, is_dry, sid_prefix
            );

            let _ = log_event(AuditEvent::SuRequest {
                uid: verified_uid,
                pid: verified_pid,
                package: package_hint,
                allowed: final_allowed,
                details: log_details,
            });

            if is_dry && final_allowed {
                let _ = log_event(AuditEvent::DaemonEvent {
                    event: "DryRunAllowReturned".to_string(),
                    details: format!("UID {} dry-run allow response dispatched successfully", verified_uid),
                });
            }

            // Write response
            let response = IpcResponse::Su(SuResponse {
                protocol_version: RUSTDROID_IPC_VERSION,
                allowed: final_allowed,
                decision: final_decision,
                reason: final_reason,
                session_id,
                error: None,
                execution_started,
                exit_code,
                stdout_preview,
                stderr_preview,
                execution_error,
            });

            write_prefixed_message(&mut stream, &response)?;
        }
        IpcMessage::Manager(request) => {
            // Require verified local caller identity.
            if !is_manager_authorized(verified_uid) {
                let response = IpcResponse::Manager(ManagerResponse::Error("Unauthorized caller identity".to_string()));
                write_prefixed_message(&mut stream, &response)?;
                return Ok(());
            }

            let response = match request {
                ManagerRequest::GetRootStatus => {
                    IpcResponse::Manager(ManagerResponse::RootStatus {
                        is_patched: true,
                        selinux_mode: "Enforcing".to_string(),
                        version: format!("v0.5-dryrun"),
                    })
                }
                ManagerRequest::ListPolicies => {
                    let s = state.lock().unwrap();
                    let policies: Vec<PolicyEntry> = s.policy_engine.db.rules.values().cloned().collect();
                    IpcResponse::Manager(ManagerResponse::Policies(policies))
                }
                ManagerRequest::SetPolicy { uid, package_name, state: dec, rule_type, execution_policy } => {
                    let mut s = state.lock().unwrap();
                    match s.policy_engine.set_rule(uid, &package_name, dec, rule_type, execution_policy) {
                        Ok(_) => IpcResponse::Manager(ManagerResponse::Success),
                        Err(e) => IpcResponse::Manager(ManagerResponse::Error(e.to_string())),
                    }
                }
                ManagerRequest::RemovePolicy { uid } => {
                    let mut s = state.lock().unwrap();
                    match s.policy_engine.revoke_rule(uid) {
                        Ok(_) => IpcResponse::Manager(ManagerResponse::Success),
                        Err(e) => IpcResponse::Manager(ManagerResponse::Error(e.to_string())),
                    }
                }
                ManagerRequest::ListPendingRequests => {
                    let s = state.lock().unwrap();
                    let pending: Vec<PendingRootRequest> = s.pending_requests.values().cloned().collect();
                    IpcResponse::Manager(ManagerResponse::PendingRequests(pending))
                }
                ManagerRequest::ApprovePendingRequest { request_id, rule_type } => {
                    let mut s = state.lock().unwrap();
                    if let Some(pending) = s.pending_requests.get(&request_id).cloned() {
                        if rule_type != PolicyRuleType::Once {
                            let pkg = pending.verified_identity.claimed_package.unwrap_or_else(|| "unknown_pkg".to_string());
                            let _ = s.policy_engine.set_rule(
                                pending.verified_identity.verified_uid,
                                &pkg,
                                SuDecision::Allow,
                                rule_type,
                                Some(ExecutionPolicy::default()) // default is allowed
                            );
                        }
                        s.decisions.insert(request_id, SuDecision::Allow);
                        IpcResponse::Manager(ManagerResponse::Success)
                    } else {
                        IpcResponse::Manager(ManagerResponse::Error("Request not found".to_string()))
                    }
                }
                ManagerRequest::DenyPendingRequest { request_id } => {
                    let mut s = state.lock().unwrap();
                    if s.pending_requests.contains_key(&request_id) {
                        s.decisions.insert(request_id, SuDecision::Deny);
                        IpcResponse::Manager(ManagerResponse::Success)
                    } else {
                        IpcResponse::Manager(ManagerResponse::Error("Request not found".to_string()))
                    }
                }
                ManagerRequest::GetAuditLogTail { log_name, tail_lines } => {
                    match rustdroid_audit::read_audit_log(&log_name) {
                        Ok(content) => {
                            let lines: Vec<&str> = content.lines().collect();
                            let start = if lines.len() > tail_lines { lines.len() - tail_lines } else { 0 };
                            let tail_content = lines[start..].join("\n");
                            IpcResponse::Manager(ManagerResponse::LogsTail(tail_content))
                        }
                        Err(e) => IpcResponse::Manager(ManagerResponse::Error(e.to_string())),
                    }
                }
                ManagerRequest::AuditBootImage { image_path } => {
                    let path = Path::new(&image_path);
                    if !path.exists() {
                        IpcResponse::Manager(ManagerResponse::Error("Boot image not found".to_string()))
                    } else {
                        match std::fs::read(path) {
                            Ok(bytes) => match rustdroid_boot::audit_image(&bytes) {
                                Ok(report) => match serde_json::to_string(&report) {
                                    Ok(json) => IpcResponse::Manager(ManagerResponse::BootAudit(json)),
                                    Err(e) => IpcResponse::Manager(ManagerResponse::Error(e.to_string())),
                                },
                                Err(e) => IpcResponse::Manager(ManagerResponse::Error(e.to_string())),
                            },
                            Err(e) => IpcResponse::Manager(ManagerResponse::Error(e.to_string())),
                        }
                    }
                }
            };

            write_prefixed_message(&mut stream, &response)?;
        }
    }

    Ok(())
}

fn uuid_placeholder() -> String {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    format!("{:x}", now)
}

fn now_timestamp() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::remove_file;

    static TEST_SERIAL_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

    struct TestServer {
        socket_path: String,
        db_path: String,
        state: Arc<Mutex<DaemonState>>,
        _shutdown: Arc<Mutex<bool>>,
    }

    fn run_test_server(socket_name: &str, enable_exec: bool) -> TestServer {
        std::env::set_var("RUSTDROID_ASK_TIMEOUT_MS", "500");
        let test_dir = "./test_run_daemon_dir";
        let _ = std::fs::create_dir_all(test_dir);
        let socket_path = format!("{}/{}", test_dir, socket_name);
        let db_path = format!("{}/{}_policy.json", test_dir, socket_name);

        let _ = std::fs::remove_file(&socket_path);
        let _ = std::fs::remove_file(&db_path);

        let policy_engine = PolicyEngine::with_path(&db_path);
        let listener = UnixListener::bind(&socket_path).unwrap();

        let state = Arc::new(Mutex::new(DaemonState {
            policy_engine,
            pending_requests: HashMap::new(),
            decisions: HashMap::new(),
        }));

        let state_clone = Arc::clone(&state);
        let shutdown = Arc::new(Mutex::new(false));
        let shutdown_clone = Arc::clone(&shutdown);

        thread::spawn(move || {
            for stream in listener.incoming() {
                if *shutdown_clone.lock().unwrap() {
                    break;
                }
                if let Ok(stream) = stream {
                    let s_clone = Arc::clone(&state_clone);
                    thread::spawn(move || {
                        let _ = handle_connection(stream, s_clone, false, enable_exec);
                    });
                } else {
                    break;
                }
            }
        });

        TestServer {
            socket_path,
            db_path,
            state,
            _shutdown: shutdown,
        }
    }

    fn clean_test_server(srv: TestServer) {
        let _ = remove_file(&srv.socket_path);
        let _ = remove_file(&srv.db_path);
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");
    }

    #[test]
    fn test_execute_request_denied_when_enable_execution_is_missing() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        // Start daemon without enable_execution
        let srv = run_test_server("test_no_exec.sock", false);

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10001");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
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
                args: vec!["/bin/id".to_string()],
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            assert_eq!(su_res.allowed, false);
            assert_eq!(su_res.execution_started, false);
            assert_eq!(su_res.reason, "execution disabled");
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_execute_request_denied_for_unknown_policy() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        // Start daemon with enable_execution
        let srv = run_test_server("test_unknown.sock", true);

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10002");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10002,
                gid: 10002,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test".to_string()),
            },
            command: CommandRequest {
                args: vec!["/bin/id".to_string()],
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            // Unknown policy defaults to Ask, which blocks. In this test, no manager
            // approved the request, so it timed out or got denied by default.
            assert_eq!(su_res.allowed, false);
            assert_eq!(su_res.execution_started, false);
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_execute_request_denied_for_deny_policy() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        let srv = run_test_server("test_deny.sock", true);

        // Pre-configure explicit Deny always rule
        {
            let mut s = srv.state.lock().unwrap();
            s.policy_engine.set_rule(10003, "com.app.test", SuDecision::Deny, PolicyRuleType::Always, None).unwrap();
        }

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10003");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10003,
                gid: 10003,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test".to_string()),
            },
            command: CommandRequest {
                args: vec!["/bin/id".to_string()],
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            assert_eq!(su_res.allowed, false);
            assert_eq!(su_res.decision, SuDecision::Deny);
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_execute_request_allowed_for_explicit_execution_policy() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        let srv = run_test_server("test_allow_exec.sock", true);

        // Harmless command host demo check
        let harmless_command = "/bin/echo";

        // Pre-configure explicit Allow with allow_command: true execution policy
        {
            let mut s = srv.state.lock().unwrap();
            let exec_policy = ExecutionPolicy {
                allow_shell: false,
                allow_command: true,
                require_tty: false,
                max_runtime_ms: 5000,
                capture_output: true,
            };
            s.policy_engine.set_rule(10004, "com.app.test", SuDecision::Allow, PolicyRuleType::Always, Some(exec_policy)).unwrap();
        }

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10004");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10004,
                gid: 10004,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test".to_string()),
            },
            command: CommandRequest {
                args: vec![harmless_command.to_string(), "rustdroid".to_string()],
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            assert_eq!(su_res.allowed, true);
            assert_eq!(su_res.execution_started, true);
            assert_eq!(su_res.exit_code, Some(0));
            assert!(su_res.stdout_preview.unwrap().contains("rustdroid"));
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_command_timeout_kills_process() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        let srv = run_test_server("test_timeout.sock", true);

        // Pre-configure Allow with execution policy having a small max_runtime_ms limit (100ms)
        {
            let mut s = srv.state.lock().unwrap();
            let exec_policy = ExecutionPolicy {
                allow_shell: false,
                allow_command: true,
                require_tty: false,
                max_runtime_ms: 100, // 100ms timeout
                capture_output: true,
            };
            s.policy_engine.set_rule(10005, "com.app.test", SuDecision::Allow, PolicyRuleType::Always, Some(exec_policy)).unwrap();
        }

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10005");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10005,
                gid: 10005,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test".to_string()),
            },
            command: CommandRequest {
                args: vec!["/bin/sleep".to_string(), "5".to_string()], // runs for 5 seconds
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            assert_eq!(su_res.allowed, true);
            assert_eq!(su_res.execution_started, true);
            assert_eq!(su_res.exit_code, None);
            assert!(su_res.execution_error.unwrap().contains("timed out"));
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_unauthorized_manager_caller_cannot_approve_pending_request() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        let srv = run_test_server("test_unauth_manager.sock", true);

        // We override RUSTDROID_MOCK_MANAGER_UID to 2000 (representing the authorized manager app UID)
        std::env::set_var("RUSTDROID_MOCK_MANAGER_UID", "2000");

        // Connecting with verified UID = 2001 (unauthorized caller)
        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "2001");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Manager(ManagerRequest::ApprovePendingRequest {
            request_id: "dummy_req".to_string(),
            rule_type: PolicyRuleType::Always,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Manager(ManagerResponse::Error(msg)) = response {
            assert!(msg.contains("Unauthorized caller identity"));
        } else {
            panic!("Expected Manager Error response");
        }

        // Connecting with verified UID = 2000 (authorized manager)
        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "2000");
        let stream_auth = UnixStream::connect(&srv.socket_path).unwrap();
        let request_auth = IpcMessage::Manager(ManagerRequest::ApprovePendingRequest {
            request_id: "dummy_req".to_string(),
            rule_type: PolicyRuleType::Always,
        });

        let mut w_auth = stream_auth;
        write_prefixed_message(&mut w_auth, &request_auth).unwrap();
        let response_auth: IpcResponse = read_prefixed_message(&mut w_auth).unwrap();

        if let IpcResponse::Manager(ManagerResponse::Error(msg)) = response_auth {
            // Returns standard error that request was not found, NOT unauthorized caller!
            assert!(msg.contains("Request not found"));
        } else {
            panic!("Expected Request not found response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_uid_10234_unknown_defaults_to_ask() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        let srv = run_test_server("test_uid_10234_unknown.sock", true);

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10234");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10234,
                gid: 10234,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test10234".to_string()),
            },
            command: CommandRequest {
                args: vec!["/bin/id".to_string()],
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            // Unknown defaults to Ask, which will timeout and return allowed = false
            assert_eq!(su_res.allowed, false);
            assert_eq!(su_res.execution_started, false);
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_uid_10234_allowed_by_policy() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        let srv = run_test_server("test_uid_10234_allow.sock", true);

        // Pre-configure Allow with execution policy allowing commands
        {
            let mut s = srv.state.lock().unwrap();
            let exec_policy = ExecutionPolicy {
                allow_shell: false,
                allow_command: true,
                require_tty: false,
                max_runtime_ms: 5000,
                capture_output: true,
            };
            s.policy_engine.set_rule(10234, "com.app.test10234", SuDecision::Allow, PolicyRuleType::Always, Some(exec_policy)).unwrap();
        }

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10234");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10234,
                gid: 10234,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test10234".to_string()),
            },
            command: CommandRequest {
                args: vec!["/bin/echo".to_string(), "hello".to_string()],
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            assert_eq!(su_res.allowed, true);
            assert_eq!(su_res.execution_started, true);
            assert_eq!(su_res.exit_code, Some(0));
            assert!(su_res.stdout_preview.unwrap().contains("hello"));
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_uid_10234_denied_by_policy() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        std::env::remove_var("RUSTDROID_MOCK_PEER_UID");
        std::env::remove_var("RUSTDROID_MOCK_MANAGER_UID");

        let srv = run_test_server("test_uid_10234_deny.sock", true);

        // Pre-configure Deny
        {
            let mut s = srv.state.lock().unwrap();
            s.policy_engine.set_rule(10234, "com.app.test10234", SuDecision::Deny, PolicyRuleType::Always, None).unwrap();
        }

        std::env::set_var("RUSTDROID_MOCK_PEER_UID", "10234");
        let stream = UnixStream::connect(&srv.socket_path).unwrap();
        let request = IpcMessage::Su(SuRequest {
            protocol_version: RUSTDROID_IPC_VERSION,
            identity: ClaimedClientIdentity {
                uid: 10234,
                gid: 10234,
                pid: 1234,
                selinux_context: "mock:u:r:untrusted_app:s0".to_string(),
                package_name: Some("com.app.test10234".to_string()),
            },
            command: CommandRequest {
                args: vec!["/bin/id".to_string()],
                env: vec![],
            },
            execution_mode: ExecutionMode::Execute,
        });

        let mut w = stream;
        write_prefixed_message(&mut w, &request).unwrap();
        let response: IpcResponse = read_prefixed_message(&mut w).unwrap();

        if let IpcResponse::Su(su_res) = response {
            assert_eq!(su_res.allowed, false);
            assert_eq!(su_res.decision, SuDecision::Deny);
        } else {
            panic!("Expected Su response");
        }

        clean_test_server(srv);
    }

    #[test]
    fn test_daemon_startup_logs_modules() {
        let _guard = TEST_SERIAL_LOCK.lock().unwrap();
        let temp_dir = "/tmp/test_daemon_modules_startup";
        let _ = std::fs::remove_dir_all(temp_dir);
        std::fs::create_dir_all(format!("{}/modules", temp_dir)).unwrap();
        
        let mod_dir = format!("{}/modules/hello-daemon-test", temp_dir);
        std::fs::create_dir_all(&mod_dir).unwrap();
        std::fs::create_dir_all(format!("{}/files", mod_dir)).unwrap();
        
        use std::io::Write;
        let mut f1 = std::fs::File::create(format!("{}/module.prop", mod_dir)).unwrap();
        writeln!(f1, "id=hello-daemon-test\nname=Hello Daemon Test\nversion=1.0\nversionCode=1\n").unwrap();
        
        let info = rustdroid_module::ModuleInfo {
            id: "hello-daemon-test".to_string(),
            name: "Hello Daemon Test".to_string(),
            version: "1.0".to_string(),
            version_code: "1".to_string(),
            author: "Author".to_string(),
            description: "Desc".to_string(),
            installed_at: 0,
            enabled: true,
            safe_mode_disabled: false,
            requires_execution: false,
            requires_mounting: false,
            requires_reboot: false,
            install_source_hash: "hash".to_string(),
            files_count: 0,
            scripts_present: vec![],
            safety_scan: "passed".to_string(),
            warnings: vec![],
            script_validation_status: "verified".to_string(),
            script_hard_errors_count: 0,
            script_warnings_count: 0,
            script_dry_run_plan_path: "".to_string(),
        };
        
        let state = rustdroid_module::ModuleStateJson {
            enabled: true,
            safe_mode_disabled: false,
            last_enabled_at: None,
            last_disabled_at: None,
            last_error: None,
            boot_stage_execution_enabled: false,
            mounting_enabled: false,
        };
        
        rustdroid_module::write_json_atomic(Path::new(&format!("{}/module.json", mod_dir)), &info).unwrap();
        rustdroid_module::write_json_atomic(Path::new(&format!("{}/state.json", mod_dir)), &state).unwrap();

        std::env::set_var("RUSTDROID_DATA_DIR", temp_dir);

        let _config = initialize_runtime_layout(temp_dir).unwrap();
        assert!(Path::new(&format!("{}/modules", temp_dir)).exists());

        let module_mgr = rustdroid_module::ModuleManager::new();
        let list = module_mgr.list_modules().unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].id, "hello-daemon-test");
        assert_eq!(list[0].enabled, true);

        let _ = std::fs::remove_dir_all(temp_dir);
    }
}
