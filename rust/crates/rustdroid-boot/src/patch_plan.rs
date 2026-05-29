use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use rustdroid_common::RustDroidError;
use crate::header::BootHeaderInfo;
use crate::ramdisk::{RamdiskCompression, RamdiskManager};

/// Structured Patch Plan defining actions before any write happens
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PatchPlan {
    pub input_path: PathBuf,
    pub output_path: PathBuf,
    pub force_patch: bool,
    pub header_version: u32,
    pub page_size: u32,
    pub ramdisk_offset: usize,
    pub ramdisk_size: usize,
    pub compression: RamdiskCompression,
    pub already_patched: bool,
}

impl PatchPlan {
    /// Creates and verifies a PatchPlan for the boot image
    pub fn build(
        input_path: &Path,
        output_path: &Path,
        image_bytes: &[u8],
        force_patch: bool,
    ) -> Result<Self, RustDroidError> {
        // 1. Audit and parse header
        let header = BootHeaderInfo::parse(image_bytes)?;

        // 2. Validate ramdisk exists and falls within buffer boundaries
        if header.ramdisk_size == 0 {
            return Err(RustDroidError::BootImageInvalid(
                "Boot image contains no ramdisk to patch".to_string(),
            ));
        }

        let ramdisk_offset = header.get_ramdisk_offset();
        let ramdisk_size = header.ramdisk_size as usize;

        if ramdisk_offset + ramdisk_size > image_bytes.len() {
            return Err(RustDroidError::BootImageInvalid(
                "Malformed boot image: ramdisk offset/size exceeds file length".to_string(),
            ));
        }

        // 3. Extract ramdisk signature and detect compression
        let ramdisk_bytes = &image_bytes[ramdisk_offset..ramdisk_offset + ramdisk_size];
        let compression = RamdiskManager::detect_compression(ramdisk_bytes);

        // 4. Validate CPIO (Uncompressed stubs or formats)
        RamdiskManager::validate_cpio(ramdisk_bytes, &compression)?;

        // 5. Detect if already patched
        let already_patched = RamdiskManager::is_already_patched(ramdisk_bytes);

        if already_patched && !force_patch {
            return Err(RustDroidError::BootImageInvalid(
                "Refusing to patch: Boot image is already patched. Enforce force_patch=true to override.".to_string(),
            ));
        }

        Ok(PatchPlan {
            input_path: input_path.to_path_buf(),
            output_path: output_path.to_path_buf(),
            force_patch,
            header_version: header.header_version,
            page_size: header.page_size,
            ramdisk_offset,
            ramdisk_size,
            compression,
            already_patched,
        })
    }
}
