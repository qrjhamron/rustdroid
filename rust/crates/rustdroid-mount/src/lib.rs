use std::ffi::CString;
use std::path::Path;
use rustdroid_common::RustDroidError;
use rustdroid_audit::{log_event, AuditEvent};

// Expose FFI bindings to C mounting glue
extern "C" {
    /// Safe low-level FFI function defined in c/src/mount_glue.c
    /// Returns 0 on success, or non-zero error code.
    pub fn rustdroid_c_bind_mount(source: *const libc::c_char, target: *const libc::c_char) -> libc::c_int;
}

/// Perform systemless bind mount (mount --bind /source /target)
pub fn bind_mount(source: &Path, target: &Path) -> Result<(), RustDroidError> {
    if !source.exists() {
        return Err(RustDroidError::MountError(format!("Source path does not exist: {}", source.display())));
    }
    if !target.exists() {
        return Err(RustDroidError::MountError(format!("Target path does not exist: {}", target.display())));
    }

    let source_c = CString::new(source.to_string_lossy().as_bytes())
        .map_err(|e| RustDroidError::MountError(e.to_string()))?;
    let target_c = CString::new(target.to_string_lossy().as_bytes())
        .map_err(|e| RustDroidError::MountError(e.to_string()))?;

    // Call standard libc mount or our custom native glue helper.
    // For systems that require advanced mounts or namespace management,
    // we lean on process and namespace setup in C glue.
    let status = unsafe {
        // We use our custom FFI function from mount_glue.c
        rustdroid_c_bind_mount(source_c.as_ptr(), target_c.as_ptr())
    };

    if status != 0 {
        let err_no = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
        let err_msg = format!("FFI Bind mount failed with status code {}, errno={}", status, err_no);
        let _ = log_event(AuditEvent::DaemonEvent {
            event: "MountError".to_string(),
            details: format!("Failed to bind mount {} -> {}: {}", source.display(), target.display(), err_msg),
        });
        return Err(RustDroidError::MountError(err_msg));
    }

    let _ = log_event(AuditEvent::DaemonEvent {
        event: "MountSuccess".to_string(),
        details: format!("Successfully bind mounted {} -> {}", source.display(), target.display()),
    });

    Ok(())
}

/// Perform overlay or multiple systemless file injections for a Module
pub fn mount_module_files(module_id: &str, _system_dir: &Path) -> Result<(), RustDroidError> {
    // For MVP, look for system/ directory under module folders, and bind mount files.
    // E.g., /data/adb/rustdroid/modules/<module_id>/system/etc/hosts -> /system/etc/hosts
    let module_sys = Path::new(rustdroid_common::MODULES_DIR_PATH).join(module_id).join("system");
    if !module_sys.exists() {
        return Ok(()); // Nothing to systemlessly mount
    }

    let _ = log_event(AuditEvent::ModuleEvent {
        module_id: module_id.to_string(),
        action: "mount-systemless".to_string(),
        success: true,
        details: format!("Processing bind mounts from {}", module_sys.display()),
    });

    // Mock/Iterate structure and perform mounts.
    // Full systemless mounting recursively traverses the subdirectory and mirrors
    // file bind mounts to standard Android paths.
    Ok(())
}
