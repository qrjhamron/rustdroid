use std::fs::{create_dir_all, OpenOptions};
use std::io::Write;
use std::path::Path;
use rustdroid_common::RustDroidError;

/// Audit event categories
pub enum AuditEvent {
    SuRequest { uid: u32, pid: i32, package: String, allowed: bool, details: String },
    DaemonEvent { event: String, details: String },
    ModuleEvent { module_id: String, action: String, success: bool, details: String },
    PatchEvent { image_path: String, is_boot: bool, success: bool, details: String },
}

/// Log an event securely to the audit logfiles
pub fn log_event(event: AuditEvent) -> Result<(), RustDroidError> {
    // Ensure logs directory exists
    let log_dir_str = format!("{}/logs", rustdroid_common::get_data_dir());
    let log_dir = Path::new(&log_dir_str);
    if !log_dir.exists() {
        create_dir_all(log_dir).map_err(|e| RustDroidError::Io(e.to_string()))?;
    }

    let timestamp = format_timestamp();

    match event {
        AuditEvent::SuRequest { uid, pid, package, allowed, details } => {
            let log_file = log_dir.join("su.log");
            let line = format!(
                "[{}] UID: {}, PID: {}, Package: {}, Allowed: {}, Details: {}\n",
                timestamp, uid, pid, package, allowed, details
            );
            append_to_file(&log_file, &line)
        }
        AuditEvent::DaemonEvent { event, details } => {
            let log_file = log_dir.join("daemon.log");
            let line = format!(
                "[{}] Event: {}, Details: {}\n",
                timestamp, event, details
            );
            append_to_file(&log_file, &line)
        }
        AuditEvent::ModuleEvent { module_id, action, success, details } => {
            let log_file = log_dir.join("module.log");
            let line = format!(
                "[{}] Module: {}, Action: {}, Success: {}, Details: {}\n",
                timestamp, module_id, action, success, details
            );
            append_to_file(&log_file, &line)
        }
        AuditEvent::PatchEvent { image_path, is_boot, success, details } => {
            let log_file = log_dir.join("patch.log");
            let line = format!(
                "[{}] Image: {}, Type: {}, Success: {}, Details: {}\n",
                timestamp, image_path, if is_boot { "boot" } else { "init_boot" }, success, details
            );
            append_to_file(&log_file, &line)
        }
    }
}

/// Retrieve log contents safely for user review
pub fn read_audit_log(filename: &str) -> Result<String, RustDroidError> {
    let log_dir_str = format!("{}/logs", rustdroid_common::get_data_dir());
    let log_path = Path::new(&log_dir_str).join(filename);
    if !log_path.exists() {
        return Ok(format!("Log file {} does not exist yet.\n", filename));
    }
    std::fs::read_to_string(log_path).map_err(|e| RustDroidError::Io(e.to_string()))
}

/// Private helper to append content to files safely
fn append_to_file(path: &Path, content: &str) -> Result<(), RustDroidError> {
    let mut file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
        .map_err(|e| RustDroidError::Io(e.to_string()))?;

    file.write_all(content.as_bytes())
        .map_err(|e| RustDroidError::Io(e.to_string()))?;

    file.flush()
        .map_err(|e| RustDroidError::Io(e.to_string()))?;

    Ok(())
}

/// Helper to generate human-readable timestamp without dependencies
fn format_timestamp() -> String {
    // Under Android shell environment or daemon boot path, custom time libraries
    // can add compilation/dependency bloat. We use SystemTime to format a basic entry.
    match std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH) {
        Ok(duration) => format!("UNIX_{}", duration.as_secs()),
        Err(_) => "UNKNOWN_TIME".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_timestamp() {
        let ts = format_timestamp();
        assert!(ts.starts_with("UNIX_"));
    }
}
