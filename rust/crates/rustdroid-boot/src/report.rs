use serde::{Deserialize, Serialize};
use crate::ramdisk::RamdiskCompression;

/// Detailed JSON-serializable audit report
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuditReport {
    pub is_valid: bool,
    pub image_type: String, // "boot.img", "init_boot.img", or "unknown"
    pub header_version: u32,
    pub page_size: u32,
    pub kernel_size: u32,
    pub ramdisk_size: u32,
    pub os_version: u32,
    pub compression: RamdiskCompression,
    pub already_patched: bool,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PatchSafetyScope {
    pub execution_default_enabled: bool,
    pub module_mounting_enabled: bool,
    pub hiding_enabled: bool,
    pub bypass_enabled: bool,
}

/// Detailed JSON-serializable patch report
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PatchReport {
    pub input_image: String,
    pub output_image: String,
    pub image_type: String,
    pub header_version: u32,
    pub original_ramdisk_size: u32,
    pub patched_ramdisk_size: u32,
    pub compression: RamdiskCompression,
    pub cpio_entries_before: usize,
    pub cpio_entries_after: usize,
    pub files_added: Vec<String>,
    pub files_replaced: Vec<String>,
    pub init_import_added: bool,
    pub already_patched: bool,
    pub warnings: Vec<String>,
    pub safety_scope: PatchSafetyScope,
    pub flash_performed: bool,
    // v0.8 Refinement Hash Fields
    pub input_image_sha256_before: String,
    pub input_image_sha256_after: String,
    pub output_image_sha256: String,
    // v0.9a LZ4 support fields
    pub compression_before: RamdiskCompression,
    pub compression_after: RamdiskCompression,
    pub compression_preserved: bool,
    pub decompressed_ramdisk_size: u32,
    pub recompressed_ramdisk_size: u32,
}

/// Detailed JSON-serializable verification report
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VerificationReport {
    pub patched_image_path: String,
    pub is_valid: bool,
    pub files_present: Vec<String>,
    pub files_missing: Vec<String>,
    pub init_import_count: usize,
    pub forbidden_strings_found: Vec<String>,
    pub safety_scope_valid: bool,
    pub safety_scope: PatchSafetyScope,
    pub flash_performed: bool,
    pub errors: Vec<String>,
    pub compression_before: RamdiskCompression,
    pub compression_after: RamdiskCompression,
}
