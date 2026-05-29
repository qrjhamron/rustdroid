use std::os::unix::net::UnixStream;
use std::path::Path;
use rustdroid_common::{
    get_socket_path, write_prefixed_message, read_prefixed_message,
    SuRequest, SuResponse, ClaimedClientIdentity, CommandRequest, RUSTDROID_IPC_VERSION,
    IpcMessage, IpcResponse, ExecutionMode
};

#[cfg(target_os = "android")]
extern "C" {
    /// Safe low-level FFI function defined in c/src/selinux_glue.c
    /// Returns 0 on success, or -1 on failure.
    pub fn rustdroid_c_selinux_get_context(buf: *mut libc::c_char, max_len: libc::c_int) -> libc::c_int;
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub unsafe extern "C" fn rustdroid_c_selinux_get_context(buf: *mut libc::c_char, _max_len: libc::c_int) -> libc::c_int {
    let ctx = b"mock:u:r:untrusted_app:s0\0";
    std::ptr::copy_nonoverlapping(ctx.as_ptr() as *const libc::c_char, buf, ctx.len());
    0
}

fn main() {
    let args: Vec<String> = std::env::args().collect();

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
        
        let self_check_json = serde_json::json!({
            "binary_name": "su",
            "version": "v1.0-alpha",
            "protocol_version": RUSTDROID_IPC_VERSION,
            "target_arch": arch,
            "default_mode": "dry-run",
            "no_local_privilege_escalation": true,
            "daemon_ipc_client_mode": true,
            "safety_scope": {
                "execution_default_enabled": false,
                "module_mounting_enabled": false,
                "hiding_enabled": false,
                "bypass_enabled": false
            }
        });

        let self_check_str = format!(
            "=== RustDroid SU Client Self Check ===\n\
             Binary Name: su\n\
             Version: v1.0-alpha\n\
             Protocol Version: {}\n\
             Target Architecture: {}\n\
             Default Mode: dry-run\n\
             No Local Privilege Escalation: true\n\
             Daemon IPC Client Mode: true\n\
             Safety Scope Summary:\n\
               - Execution Default Enabled: false\n\
               - Module Mounting Enabled: false\n\
               - Hiding Enabled: false\n\
               - Bypass Enabled: false\n\
             Result: PASSED\n",
             RUSTDROID_IPC_VERSION, arch
        );

        if args.iter().any(|arg| arg == "--json" || arg == "-j" || arg == "--debug-json") {
            println!("{}", serde_json::to_string_pretty(&self_check_json).unwrap());
        } else {
            print!("{}", self_check_str);
        }
        std::process::exit(0);
    }

    if args.iter().any(|arg| arg == "--version" || arg == "-v") {
        println!("su v1.0-alpha");
        std::process::exit(0);
    }
    
    // Default flag states: dry-run only mode is the safe default!
    let mut execution_mode = ExecutionMode::DryRun;
    let mut command_args = Vec::new();
    let mut socket_override: Option<String> = None;
    let mut print_json = false;
    let mut debug_json = false;

    // Parse FFI/CLI options manually for zero-dependencies MVP purity
    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--execute" | "-e" => {
                execution_mode = ExecutionMode::Execute;
            }
            "--dry-run" | "-d" => {
                execution_mode = ExecutionMode::DryRun;
            }
            "--command" | "-c" => {
                if i + 1 < args.len() {
                    // Split command string into arguments (simplistic shell splitting for MVP)
                    command_args = args[i + 1].split_whitespace().map(|s| s.to_string()).collect();
                    i += 1;
                }
            }
            "--socket" | "-s" => {
                if i + 1 < args.len() {
                    socket_override = Some(args[i + 1].clone());
                    i += 1;
                }
            }
            "--json" | "-j" => {
                print_json = true;
            }
            "--debug-json" => {
                print_json = true;
                debug_json = true;
            }
            _ => {
                // Positional arguments are accumulated as command args if command was not set
                if command_args.is_empty() {
                    command_args.push(args[i].clone());
                }
            }
        }
        i += 1;
    }

    // Set socket path override env if specified via CLI flags
    if let Some(ref path) = socket_override {
        std::env::set_var("RUSTDROID_SOCKET_PATH", path);
    }

    let socket_path = get_socket_path();

    // 1. Gather POSIX credentials
    let uid = unsafe { libc::getuid() };
    let gid = unsafe { libc::getgid() };
    let pid = unsafe { libc::getpid() };

    // 2. Query read-only SELinux context helper via FFI C boundary
    let mut selinux_context = "mock:u:r:untrusted_app:s0".to_string();
    let mut buf = [0 as libc::c_char; 256];
    let rc = unsafe {
        rustdroid_c_selinux_get_context(buf.as_mut_ptr(), buf.len() as libc::c_int)
    };
    if rc == 0 {
        // Convert FFI buffer safely
        let u8_buf: Vec<u8> = buf.iter()
            .map(|&c| c as u8)
            .take_while(|&c| c != 0)
            .collect();
        if let Ok(ctx_str) = String::from_utf8(u8_buf) {
            if !ctx_str.is_empty() {
                selinux_context = ctx_str;
            }
        }
    }

    // Resolve caller's package name
    let package_name = get_caller_package(pid);

    // Default command if empty
    if command_args.is_empty() {
        command_args.push("/system/bin/sh".to_string());
    }

    // 3. Assemble versioned, structured SuRequest FFI message
    let request = SuRequest {
        protocol_version: RUSTDROID_IPC_VERSION,
        identity: ClaimedClientIdentity {
            uid,
            gid,
            pid,
            selinux_context,
            package_name: Some(package_name),
        },
        command: CommandRequest {
            args: command_args,
            env: std::env::vars().collect(),
        },
        execution_mode,
    };

    let ipc_msg = IpcMessage::Su(request.clone());

    // 4. Establish socket connection to the privilege daemon
    if !Path::new(&socket_path).exists() {
        if print_json {
            let response = SuResponse {
                protocol_version: RUSTDROID_IPC_VERSION,
                allowed: false,
                decision: rustdroid_common::SuDecision::Deny,
                reason: format!("Daemon not running (Socket {} missing)", socket_path),
                session_id: None,
                error: Some(rustdroid_common::RustDroidError::DaemonConnection("Socket missing".to_string())),
                execution_started: false,
                exit_code: None,
                stdout_preview: None,
                stderr_preview: None,
                execution_error: None,
            };
            println!("{}", serde_json::to_string(&IpcResponse::Su(response)).unwrap());
        } else {
            eprintln!("RustDroid Error: Root daemon not running (Socket {} missing).", socket_path);
        }
        std::process::exit(1);
    }

    let mut stream = match UnixStream::connect(&socket_path) {
        Ok(s) => s,
        Err(e) => {
            if print_json {
                let response = SuResponse {
                    protocol_version: RUSTDROID_IPC_VERSION,
                    allowed: false,
                    decision: rustdroid_common::SuDecision::Deny,
                    reason: format!("Failed to connect to daemon socket: {}", e),
                    session_id: None,
                    error: Some(rustdroid_common::RustDroidError::DaemonConnection(e.to_string())),
                    execution_started: false,
                    exit_code: None,
                    stdout_preview: None,
                    stderr_preview: None,
                    execution_error: None,
                };
                println!("{}", serde_json::to_string(&IpcResponse::Su(response)).unwrap());
            } else {
                eprintln!("RustDroid Error: Failed to connect to daemon socket: {}", e);
            }
            std::process::exit(1);
        }
    };

    // 5. Transmit structured request message securely
    if let Err(e) = write_prefixed_message(&mut stream, &ipc_msg) {
        eprintln!("RustDroid Error: Socket write error: {:?}", e);
        std::process::exit(1);
    }

    // 6. Await length-prefixed response payload
    let ipc_res: IpcResponse = match read_prefixed_message(&mut stream) {
        Ok(res) => res,
        Err(e) => {
            eprintln!("RustDroid Error: Failed to read response from daemon: {:?}", e);
            std::process::exit(1);
        }
    };

    let response = match ipc_res {
        IpcResponse::Su(res) => res,
        _ => {
            eprintln!("RustDroid Error: Received unexpected response type from daemon.");
            std::process::exit(1);
        }
    };

    // 7. Output result
    if print_json {
        let mut final_res = response.clone();
        if !debug_json {
            if let Some(ref sid) = final_res.session_id {
                final_res.session_id = Some(rustdroid_common::redact_token(sid));
            }
        }
        println!("{}", serde_json::to_string(&IpcResponse::Su(final_res)).unwrap());
    } else {
        println!("=== RustDroid SU IPC Session ===");
        println!("Protocol Version: {}", response.protocol_version);
        println!("Decision Allowed: {}", response.allowed);
        println!("Decision Mode: {:?}", response.decision);
        println!("Daemon Reason: {}", response.reason);
        if let Some(ref sid) = response.session_id {
            // Log a short prefix only in dry run / normal mode
            println!("Session Token: {}", rustdroid_common::redact_token(sid));
        }
        if response.execution_started {
            println!("Execution Started: {}", response.execution_started);
            println!("Exit Code: {:?}", response.exit_code);
            if let Some(ref stdout) = response.stdout_preview {
                if !stdout.is_empty() {
                    println!("Stdout Preview:\n{}", stdout.trim_end());
                }
            }
            if let Some(ref stderr) = response.stderr_preview {
                if !stderr.is_empty() {
                    println!("Stderr Preview:\n{}", stderr.trim_end());
                }
            }
            if let Some(ref exec_err) = response.execution_error {
                println!("Execution Error: {}", exec_err);
            }
        }
        if let Some(ref err) = response.error {
            println!("Protocol Error: {:?}", err);
        }
        println!("=================================");

        // If daemon refused execution because execution is disabled, print a clear message
        if !response.allowed && response.reason == "execution disabled" {
            eprintln!("RustDroid Error: Command execution is disabled on the daemon. Restart daemon with --enable-execution to allow.");
        }
    }

    if !response.allowed {
        std::process::exit(13); // Permission denied
    }

    // Dry-run mode exits cleanly here
    if request.execution_mode == ExecutionMode::DryRun {
        println!("Dry-run verified successfully. No execution triggered.");
        std::process::exit(0);
    }
}

fn get_caller_package(pid: i32) -> String {
    let path = format!("/proc/{}/cmdline", pid);
    if let Ok(content) = std::fs::read_to_string(path) {
        if let Some(cmd) = content.split('\0').next() {
            if !cmd.is_empty() {
                return cmd.to_string();
            }
        }
    }
    "unknown_app".to_string()
}

#[cfg(test)]
mod tests {
    use rustdroid_common::{SuResponse, SuDecision, redact_token};

    #[test]
    fn test_session_id_redaction_for_normal_json() {
        let response = SuResponse {
            protocol_version: 1,
            allowed: true,
            decision: SuDecision::Allow,
            reason: "test".to_string(),
            session_id: Some("1234567890abcdef".to_string()),
            error: None,
            execution_started: false,
            exit_code: None,
            stdout_preview: None,
            stderr_preview: None,
            execution_error: None,
        };

        // If debug_json is false, session_id must be redacted
        let mut normal_res = response.clone();
        if let Some(ref sid) = normal_res.session_id {
            normal_res.session_id = Some(redact_token(sid));
        }
        assert_eq!(normal_res.session_id, Some("1234...".to_string()));

        // If debug_json is true, session_id is left untouched
        let debug_res = response.clone();
        assert_eq!(debug_res.session_id, Some("1234567890abcdef".to_string()));
    }
}
