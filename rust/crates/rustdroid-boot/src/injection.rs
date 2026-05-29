use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use rustdroid_common::RustDroidError;

/// Individual payload injection entry planning details
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct InjectionEntry {
    pub source_path: PathBuf,
    pub target_path: String,
    pub is_ramdisk: bool,
    pub file_size: usize,
    pub permissions: String,
}

/// Payload Injection Plan staging details
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PayloadInjectionPlan {
    pub target_arch: String,
    pub staging_data_dir: String,
    pub ramdisk_init_rc_target: String,
    pub entries: Vec<InjectionEntry>,
}

impl PayloadInjectionPlan {
    /// Create a new planner instance
    pub fn new(target_arch: &str) -> Self {
        Self {
            target_arch: target_arch.to_string(),
            staging_data_dir: "/data/adb/rustdroid".to_string(),
            ramdisk_init_rc_target: "init.rustdroid.rc".to_string(),
            entries: Vec::new(),
        }
    }

    /// Add an injection entry to the plan
    pub fn add_entry(&mut self, entry: InjectionEntry) {
        self.entries.push(entry);
    }

    /// Validates safety boundaries of all planned target paths.
    /// Returns an error if unsafe targets (path traversal, arbitrary system files) are detected.
    pub fn validate_safety(&self) -> Result<(), RustDroidError> {
        for entry in &self.entries {
            let target_str = &entry.target_path;

            // 1. Prevent Path Traversal Evasion ("..")
            if target_str.contains("..") {
                return Err(RustDroidError::MountError(format!(
                    "Unsafe path traversal detected in injection target path: {}", target_str
                )));
            }

            // 2. Validate prefix boundaries
            if entry.is_ramdisk {
                // Ramdisk root files must be relative (un-rooted) within the archive staging area
                if target_str.starts_with('/') {
                    return Err(RustDroidError::MountError(format!(
                        "Unsafe absolute path targeted in ramdisk root: {}", target_str
                    )));
                }
            } else {
                // Data folder staging files must stay strictly within the authorized /data/adb/rustdroid directory
                if !target_str.starts_with("/data/adb/rustdroid") {
                    return Err(RustDroidError::MountError(format!(
                        "Unsafe target directory outside authorized staging boundary (/data/adb/rustdroid): {}", target_str
                    )));
                }
            }
        }
        Ok(())
    }
}
