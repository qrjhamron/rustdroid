use rustdroid_common::RustDroidError;
use crate::header::BootHeaderInfo;
use crate::ramdisk::RamdiskManager;
use crate::report::AuditReport;

/// Core auditor performing deep read-only inspection of boot images
pub fn run_audit(image_bytes: &[u8]) -> Result<AuditReport, RustDroidError> {
    // 1. Parse header (propagates errors on invalid magic/version/sizes)
    let header = match BootHeaderInfo::parse(image_bytes) {
        Ok(h) => h,
        Err(e) => {
            return Ok(AuditReport {
                is_valid: false,
                image_type: "unknown".to_string(),
                header_version: 0,
                page_size: 0,
                kernel_size: 0,
                ramdisk_size: 0,
                os_version: 0,
                compression: crate::ramdisk::RamdiskCompression::Unknown,
                already_patched: false,
                warnings: vec![format!("Header validation failed: {}", e)],
            });
        }
    };

    // 2. Identify Image type
    let image_type = if header.is_init_boot {
        "init_boot.img".to_string()
    } else {
        "boot.img".to_string()
    };

    // 3. Verify ramdisk bounds
    let ramdisk_offset = header.get_ramdisk_offset();
    let ramdisk_size = header.ramdisk_size as usize;

    let mut compression = crate::ramdisk::RamdiskCompression::Unknown;
    let mut already_patched = false;
    let mut warnings = Vec::new();

    if ramdisk_size > 0 {
        if ramdisk_offset + ramdisk_size <= image_bytes.len() {
            let ramdisk_bytes = &image_bytes[ramdisk_offset..ramdisk_offset + ramdisk_size];
            compression = RamdiskManager::detect_compression(ramdisk_bytes);
            already_patched = RamdiskManager::is_already_patched(ramdisk_bytes);
            
            if already_patched {
                warnings.push("Image already contains a RustDroid privilege patch marker!".to_string());
            }
        } else {
            warnings.push("Malformed image: ramdisk size extends beyond file bounds".to_string());
        }
    } else {
        warnings.push("Image contains no ramdisk segment".to_string());
    }

    Ok(AuditReport {
        is_valid: warnings.is_empty() || (warnings.len() == 1 && already_patched),
        image_type,
        header_version: header.header_version,
        page_size: header.page_size,
        kernel_size: header.kernel_size,
        ramdisk_size: header.ramdisk_size,
        os_version: header.os_version,
        compression,
        already_patched,
        warnings,
    })
}
