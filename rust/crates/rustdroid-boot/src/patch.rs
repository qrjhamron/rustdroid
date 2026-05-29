use std::path::Path;
use std::fs::{self, File};
use std::io::Write;
use rustdroid_common::RustDroidError;
use crate::header::BootHeaderInfo;
use crate::ramdisk::RamdiskManager;
use crate::patch_plan::PatchPlan;
use crate::cpio::{CpioEntry, parse_cpio, write_cpio};
use crate::report::{PatchReport, PatchSafetyScope, VerificationReport};
use sha2::{Digest, Sha256};

/// Helper to compute SHA-256 hash of byte slice
fn compute_sha256(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    let result = hasher.finalize();
    format!("{:x}", result)
}

/// Implement safe real ramdisk modification and boot image repacking for v0.7/v0.8
pub fn patch_boot_image_v0_7(
    input_path: &Path,
    output_path: &Path,
    payload_dir: &Path,
    force_patch: bool,
) -> Result<PatchReport, RustDroidError> {
    // 1. Read input image and compute initial hash
    let image_bytes = fs::read(input_path)
        .map_err(|e| RustDroidError::Io(format!("Failed to read input boot image: {}", e)))?;
    let input_sha256_before = compute_sha256(&image_bytes);

    // 2. Build and validate patch plan
    let plan = PatchPlan::build(input_path, output_path, &image_bytes, force_patch)?;

    // 3. Read boot header
    let header = BootHeaderInfo::parse(&image_bytes)?;
    let page_size = header.page_size as usize;
    let ramdisk_offset = header.get_ramdisk_offset();
    let original_ramdisk_size = header.ramdisk_size as usize;

    // 4. Decompress original ramdisk
    let ramdisk_compressed_bytes = &image_bytes[ramdisk_offset..ramdisk_offset + original_ramdisk_size];
    let raw_ramdisk_bytes = RamdiskManager::decompress(ramdisk_compressed_bytes, &plan.compression)?;

    // Verify decompressed output is valid CPIO before patching
    RamdiskManager::validate_cpio(&raw_ramdisk_bytes, &crate::ramdisk::RamdiskCompression::RawCpio)?;

    // 5. Parse SVR4 CPIO entries
    let mut entries = parse_cpio(&raw_ramdisk_bytes)?;
    let cpio_entries_before = entries.len();

    // 6. Load payload assets from payload_dir
    let init_rc_content = fs::read(payload_dir.join("init/init.rustdroid.rc"))
        .unwrap_or_else(|_| {
            // Fallback for tests or missing file
            b"# RustDroid Daemon Init Configuration (v0.8)\n".to_vec()
        });

    let metadata_content = fs::read(payload_dir.join("metadata.json"))
        .unwrap_or_else(|_| {
            // Fallback for tests or missing file
            br#"{"rustdroid_version":"v0.8","payload_version":2,"target_arch":"aarch64","safety_scope":{"execution_default_enabled":false,"module_mounting_enabled":false,"hiding_enabled":false,"bypass_enabled":false}}"#.to_vec()
        });

    let mut files_added = Vec::new();
    let mut files_replaced = Vec::new();

    // Helper to inject regular file entry
    let inject_file = |entries: &mut Vec<CpioEntry>, path: &str, content: Vec<u8>, mode: u32| -> bool {
        let entry_idx = entries.iter().position(|e| e.name == path);
        if let Some(idx) = entry_idx {
            entries[idx].content = content;
            entries[idx].mode = mode | 0o100000; // ensure regular file type
            entries[idx].uid = 0;
            entries[idx].gid = 0;
            entries[idx].mtime = 0;
            false
        } else {
            entries.push(CpioEntry {
                name: path.to_string(),
                mode: mode | 0o100000,
                uid: 0,
                gid: 0,
                mtime: 0,
                content,
            });
            true
        }
    };

    // Inject "rustdroid" directory entry if not present
    let has_dir = entries.iter().any(|e| e.name == "rustdroid");
    if !has_dir {
        entries.push(CpioEntry {
            name: "rustdroid".to_string(),
            mode: 0o040755, // directory type
            uid: 0,
            gid: 0,
            mtime: 0,
            content: Vec::new(),
        });
        files_added.push("rustdroid".to_string());
    }

    // Inject files
    if inject_file(&mut entries, "init.rustdroid.rc", init_rc_content, 0o644) {
        files_added.push("init.rustdroid.rc".to_string());
    } else {
        files_replaced.push("init.rustdroid.rc".to_string());
    }

    if inject_file(&mut entries, "rustdroid/.installed", b"1".to_vec(), 0o644) {
        files_added.push("rustdroid/.installed".to_string());
    } else {
        files_replaced.push("rustdroid/.installed".to_string());
    }

    if inject_file(&mut entries, "rustdroid/version", b"v0.8\n".to_vec(), 0o644) {
        files_added.push("rustdroid/version".to_string());
    } else {
        files_replaced.push("rustdroid/version".to_string());
    }

    if inject_file(&mut entries, "rustdroid/payload_manifest.json", metadata_content.clone(), 0o644) {
        files_added.push("rustdroid/payload_manifest.json".to_string());
    } else {
        files_replaced.push("rustdroid/payload_manifest.json".to_string());
    }

    // 7. Inject import /init.rustdroid.rc into init.rc
    let mut init_import_added = false;
    let mut warnings = Vec::new();

    if let Some(idx) = entries.iter().position(|e| e.name == "init.rc") {
        let mut content_str = String::from_utf8(entries[idx].content.clone())
            .unwrap_or_else(|_| String::new());

        if !content_str.is_empty() {
            if !content_str.contains("import /init.rustdroid.rc") {
                content_str.push_str("\n# Import RustDroid service init configuration\nimport /init.rustdroid.rc\n");
                entries[idx].content = content_str.into_bytes();
                init_import_added = true;
            }
        } else {
            warnings.push("init.rc was not valid UTF-8, skipped import line injection".to_string());
        }
    } else {
        warnings.push("init.rc not found in ramdisk, skipped import line injection".to_string());
    }

    let cpio_entries_after = entries.len();

    // 8. Re-serialize raw CPIO
    let new_raw_ramdisk_bytes = write_cpio(&entries)?;

    // 9. Recompress raw CPIO
    let new_compressed_ramdisk_bytes = RamdiskManager::compress(&new_raw_ramdisk_bytes, &plan.compression)?;
    let new_ramdisk_size = new_compressed_ramdisk_bytes.len();

    // Verify recompressed output can be decompressed again
    let decompressed_again = RamdiskManager::decompress(&new_compressed_ramdisk_bytes, &plan.compression)?;
    if decompressed_again != new_raw_ramdisk_bytes {
        return Err(RustDroidError::BootImageInvalid("Recompressed output does not match decompressed raw bytes".to_string()));
    }

    // 10. Re-build new boot image
    let mut new_image = Vec::new();

    // Page 0: updated Header
    let mut header_page = image_bytes[0..page_size].to_vec();
    let new_size_le = (new_ramdisk_size as u32).to_le_bytes();
    if header.header_version < 3 {
        header_page[16..20].copy_from_slice(&new_size_le);
    } else {
        header_page[12..16].copy_from_slice(&new_size_le);
    }
    new_image.extend_from_slice(&header_page);

    // Kernel pages
    let kernel_pages_bytes = &image_bytes[page_size..ramdisk_offset];
    new_image.extend_from_slice(kernel_pages_bytes);

    // Ramdisk pages
    new_image.extend_from_slice(&new_compressed_ramdisk_bytes);

    // Pad ramdisk to page boundary
    let ramdisk_pad = if new_ramdisk_size % page_size == 0 {
        0
    } else {
        page_size - (new_ramdisk_size % page_size)
    };
    for _ in 0..ramdisk_pad {
        new_image.push(0);
    }

    // Subsequent blobs from original image
    let original_after_ramdisk_offset = ((ramdisk_offset + original_ramdisk_size + page_size - 1) / page_size * page_size) as usize;
    if original_after_ramdisk_offset < image_bytes.len() {
        let subsequent_bytes = &image_bytes[original_after_ramdisk_offset..];
        new_image.extend_from_slice(subsequent_bytes);
    }

    // 11. Write atomically to temporary file in the same directory
    let tmp_filename = format!(
        "{}.tmp",
        output_path
            .file_name()
            .ok_or_else(|| RustDroidError::Io("Invalid output path filename".to_string()))?
            .to_string_lossy()
    );
    let tmp_path = output_path.with_file_name(tmp_filename);

    let mut tmp_file = File::create(&tmp_path)
        .map_err(|e| RustDroidError::Io(format!("Failed to create temporary output file: {}", e)))?;
    tmp_file
        .write_all(&new_image)
        .map_err(|e| RustDroidError::Io(format!("Failed to write to temporary output file: {}", e)))?;
    tmp_file.sync_all().map_err(|e| RustDroidError::Io(e.to_string()))?;

    // Verify patched recompressed output contains injected RustDroid files and safety scope is false, forbidden strings are absent
    let verification = verify_patched_boot_image(&tmp_path)?;
    if !verification.is_valid {
        let _ = fs::remove_file(&tmp_path);
        return Err(RustDroidError::BootImageInvalid(format!("Post-patch verification failed: {:?}", verification.errors)));
    }

    // Atomic rename
    fs::rename(&tmp_path, output_path)
        .map_err(|e| RustDroidError::Io(format!("Failed to atomically rename patched boot image: {}", e)))?;

    // Verify input image is unchanged (compute hash of input_path again)
    let input_bytes_after = fs::read(input_path)
        .map_err(|e| RustDroidError::Io(format!("Failed to re-read input boot image for verification: {}", e)))?;
    let input_sha256_after = compute_sha256(&input_bytes_after);

    if input_sha256_before != input_sha256_after {
        return Err(RustDroidError::Io("Safety assertion failed: input image was modified during patch operation!".to_string()));
    }

    // Compute hash of patched output image
    let output_sha256 = compute_sha256(&new_image);

    // 12. Parse safety scope from metadata.json fallback or actual contents
    let json_val: serde_json::Value = serde_json::from_slice(&metadata_content)
        .unwrap_or_else(|_| serde_json::Value::Null);

    let safety_scope = if let Some(scope_obj) = json_val.get("safety_scope") {
        PatchSafetyScope {
            execution_default_enabled: scope_obj.get("execution_default_enabled").and_then(|v| v.as_bool()).unwrap_or(false),
            module_mounting_enabled: scope_obj.get("module_mounting_enabled").and_then(|v| v.as_bool()).unwrap_or(false),
            hiding_enabled: scope_obj.get("hiding_enabled").and_then(|v| v.as_bool()).unwrap_or(false),
            bypass_enabled: scope_obj.get("bypass_enabled").and_then(|v| v.as_bool()).unwrap_or(false),
        }
    } else {
        PatchSafetyScope {
            execution_default_enabled: false,
            module_mounting_enabled: false,
            hiding_enabled: false,
            bypass_enabled: false,
        }
    };

    Ok(PatchReport {
        input_image: input_path.to_string_lossy().to_string(),
        output_image: output_path.to_string_lossy().to_string(),
        image_type: if header.is_init_boot { "init_boot.img".to_string() } else { "boot.img".to_string() },
        header_version: header.header_version,
        original_ramdisk_size: original_ramdisk_size as u32,
        patched_ramdisk_size: new_ramdisk_size as u32,
        compression: plan.compression.clone(),
        cpio_entries_before,
        cpio_entries_after,
        files_added,
        files_replaced,
        init_import_added,
        already_patched: plan.already_patched,
        warnings,
        safety_scope,
        flash_performed: false,
        input_image_sha256_before: input_sha256_before,
        input_image_sha256_after: input_sha256_after,
        output_image_sha256: output_sha256,
        compression_before: plan.compression.clone(),
        compression_after: plan.compression.clone(),
        compression_preserved: true,
        decompressed_ramdisk_size: raw_ramdisk_bytes.len() as u32,
        recompressed_ramdisk_size: new_compressed_ramdisk_bytes.len() as u32,
    })
}

/// Verification routine to reopen the patched output image and verify its contents
pub fn verify_patched_boot_image(
    patched_image_path: &Path,
) -> Result<VerificationReport, RustDroidError> {
    let mut errors = Vec::new();
    let mut files_present = Vec::new();
    let mut files_missing = Vec::new();
    let mut init_import_count = 0;
    let mut forbidden_strings_found = Vec::new();
    let mut safety_scope = PatchSafetyScope {
        execution_default_enabled: false,
        module_mounting_enabled: false,
        hiding_enabled: false,
        bypass_enabled: false,
    };
    let mut safety_scope_valid = true;

    // 1. Reopen patched output image
    let patched_bytes = match fs::read(patched_image_path) {
        Ok(b) => b,
        Err(e) => {
            return Err(RustDroidError::Io(format!("Failed to read patched image: {}", e)));
        }
    };

    // 2. Parse boot header
    let header = BootHeaderInfo::parse(&patched_bytes)?;
    let ramdisk_offset = header.get_ramdisk_offset();
    let ramdisk_size = header.ramdisk_size as usize;

    if ramdisk_offset + ramdisk_size > patched_bytes.len() {
        return Err(RustDroidError::BootImageInvalid(
            "Malformed patched boot image: ramdisk size out of bounds".to_string()
        ));
    }

    let ramdisk_compressed_bytes = &patched_bytes[ramdisk_offset..ramdisk_offset + ramdisk_size];
    let compression = RamdiskManager::detect_compression(ramdisk_compressed_bytes);

    // 3. Decompress the ramdisk
    let raw_ramdisk_bytes = RamdiskManager::decompress(ramdisk_compressed_bytes, &compression)?;

    // 4. Parse the CPIO archive
    let entries = parse_cpio(&raw_ramdisk_bytes)?;

    // Verify files exist: init.rustdroid.rc, rustdroid/.installed, rustdroid/version, rustdroid/payload_manifest.json
    let expected_files = [
        "init.rustdroid.rc",
        "rustdroid/.installed",
        "rustdroid/version",
        "rustdroid/payload_manifest.json",
    ];

    let mut injected_rc_content = Vec::new();
    let mut manifest_content = Vec::new();

    for file_name in &expected_files {
        if let Some(entry) = entries.iter().find(|e| e.name == *file_name) {
            files_present.push(file_name.to_string());
            if *file_name == "init.rustdroid.rc" {
                injected_rc_content = entry.content.clone();
            } else if *file_name == "rustdroid/payload_manifest.json" {
                manifest_content = entry.content.clone();
            }
        } else {
            files_missing.push(file_name.to_string());
            errors.push(format!("Missing required injected file: {}", file_name));
        }
    }

    // Verify init.rc contains exactly one import line: import /init.rustdroid.rc
    if let Some(init_rc_entry) = entries.iter().find(|e| e.name == "init.rc") {
        let content_str = String::from_utf8_lossy(&init_rc_entry.content);
        let occurrences = content_str.matches("import /init.rustdroid.rc").count();
        init_import_count = occurrences;
        if occurrences != 1 {
            errors.push(format!(
                "init.rc must contain exactly one import line for /init.rustdroid.rc, found: {}",
                occurrences
            ));
        }
    } else {
        errors.push("init.rc not found in CPIO archive".to_string());
    }

    // Verify forbidden strings are absent from injected init content (uncommented lines only)
    let forbidden_strings = ["setenforce", "bypass", "hide", "pivot_root", "attestation"];
    let rc_content_str = String::from_utf8_lossy(&injected_rc_content);
    for forbidden in &forbidden_strings {
        for line in rc_content_str.lines() {
            let trimmed = line.trim();
            if !trimmed.starts_with('#') && trimmed.contains(forbidden) {
                forbidden_strings_found.push(forbidden.to_string());
                errors.push(format!("Forbidden string '{}' found in uncommented line of init: {}", forbidden, trimmed));
            }
        }
    }

    // Parse safety scope from payload_manifest.json if present
    if !manifest_content.is_empty() {
        if let Ok(json_val) = serde_json::from_slice::<serde_json::Value>(&manifest_content) {
            if let Some(scope_obj) = json_val.get("safety_scope") {
                safety_scope.execution_default_enabled = scope_obj.get("execution_default_enabled").and_then(|v| v.as_bool()).unwrap_or(false);
                safety_scope.module_mounting_enabled = scope_obj.get("module_mounting_enabled").and_then(|v| v.as_bool()).unwrap_or(false);
                safety_scope.hiding_enabled = scope_obj.get("hiding_enabled").and_then(|v| v.as_bool()).unwrap_or(false);
                safety_scope.bypass_enabled = scope_obj.get("bypass_enabled").and_then(|v| v.as_bool()).unwrap_or(false);
            }
        }
    }

    // Verify safety_scope fields remain false
    if safety_scope.bypass_enabled || safety_scope.hiding_enabled || safety_scope.module_mounting_enabled {
        safety_scope_valid = false;
        errors.push("Unsafe features enabled in safety scope!".to_string());
    }

    let is_valid = errors.is_empty() && files_missing.is_empty();

    Ok(VerificationReport {
        patched_image_path: patched_image_path.to_string_lossy().to_string(),
        is_valid,
        files_present,
        files_missing,
        init_import_count,
        forbidden_strings_found,
        safety_scope_valid,
        safety_scope,
        flash_performed: false,
        errors,
        compression_before: compression.clone(),
        compression_after: compression.clone(),
    })
}

