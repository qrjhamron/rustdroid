use std::process::{Command, Stdio};
use std::time::{Instant, Duration};
use std::io::Read;
use rustdroid_common::{CommandRequest, ExecutionPolicy};

#[derive(Debug)]
pub struct ExecResult {
    pub exit_code: Option<i32>,
    pub stdout: String,
    pub stderr: String,
    pub error: Option<String>,
}

pub fn execute_command(
    req: &CommandRequest,
    policy: &ExecutionPolicy,
) -> ExecResult {
    // 1. Sanitize arguments and resolve binary
    if req.args.is_empty() {
        return ExecResult {
            exit_code: None,
            stdout: String::new(),
            stderr: String::new(),
            error: Some("Empty command arguments".to_string()),
        };
    }

    let binary = &req.args[0];
    let args = &req.args[1..];

    // 2. Prepare sanitised minimal safe environment
    let mut cmd = Command::new(binary);
    cmd.args(args);
    cmd.env_clear(); // Remove all sensitive manager/daemon environment variables
    
    // Add safe environment variables
    cmd.env("PATH", "/system/bin:/system/xbin:/vendor/bin:/bin:/usr/bin"); // include standard paths for host test support
    cmd.env("HOME", "/");
    cmd.env("RUSTDROID", "1");

    // 3. Set standard input/output redirection
    cmd.stdin(Stdio::null());
    if policy.capture_output {
        cmd.stdout(Stdio::piped());
        cmd.stderr(Stdio::piped());
    } else {
        cmd.stdout(Stdio::null());
        cmd.stderr(Stdio::null());
    }

    // 4. Spawn child process
    let mut child = match cmd.spawn() {
        Ok(c) => c,
        Err(e) => {
            return ExecResult {
                exit_code: None,
                stdout: String::new(),
                stderr: String::new(),
                error: Some(format!("Failed to spawn command: {}", e)),
            };
        }
    };

    // 5. Handle timeout monitoring using a polling loop
    let start_time = Instant::now();
    let max_duration = Duration::from_millis(policy.max_runtime_ms);
    let poll_interval = Duration::from_millis(50);
    let mut exit_status = None;

    while start_time.elapsed() < max_duration {
        match child.try_wait() {
            Ok(Some(status)) => {
                exit_status = Some(status);
                break;
            }
            Ok(None) => {
                // Child is still running, sleep and retry
                std::thread::sleep(poll_interval);
            }
            Err(e) => {
                // Error waiting, kill child and return error
                let _ = child.kill();
                let _ = child.wait();
                return ExecResult {
                    exit_code: None,
                    stdout: String::new(),
                    stderr: String::new(),
                    error: Some(format!("Error waiting for process: {}", e)),
                };
            }
        }
    }

    // 6. Handle timeout: Kill child if it exceeded runtime limits
    if exit_status.is_none() {
        let _ = child.kill();
        // Await the killed process to reclaim resources
        let _ = child.wait();
        return ExecResult {
            exit_code: None,
            stdout: String::new(),
            stderr: String::new(),
            error: Some("Command execution timed out".to_string()),
        };
    }

    // 7. Capture stdout and stderr up to a limited preview size (e.g. 4KB)
    let mut stdout_buf = String::new();
    let mut stderr_buf = String::new();
    let limit = 4096; // 4KB limit to prevent massive output buffer memory bloat

    if policy.capture_output {
        if let Some(out) = child.stdout.take() {
            let mut handle = out.take(limit as u64);
            let _ = handle.read_to_string(&mut stdout_buf);
        }
        if let Some(err) = child.stderr.take() {
            let mut handle = err.take(limit as u64);
            let _ = handle.read_to_string(&mut stderr_buf);
        }
    }

    let status = exit_status.unwrap();
    ExecResult {
        exit_code: status.code(),
        stdout: stdout_buf,
        stderr: stderr_buf,
        error: None,
    }
}
