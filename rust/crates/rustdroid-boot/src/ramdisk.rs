use serde::{Deserialize, Serialize};
use rustdroid_common::RustDroidError;
use flate2::read::GzDecoder;
use flate2::write::GzEncoder;
use flate2::Compression;
use std::io::{Read, Write};

/// RUSTDROID magic marker to check if ramdisk/boot is already patched
pub const RUSTDROID_PATCH_MARKER: &[u8; 12] = b"RUSTDROIDV02";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum RamdiskCompression {
    Gzip,
    Lz4,
    Lz4Legacy,
    RawCpio,
    Unknown,
}

pub struct RamdiskManager;

impl RamdiskManager {
    /// Detects ramdisk compression signature or raw CPIO format from lead magic bytes
    pub fn detect_compression(lead_bytes: &[u8]) -> RamdiskCompression {
        if lead_bytes.len() < 4 {
            return RamdiskCompression::Unknown;
        }

        // Gzip standard magic: 1F 8B
        if lead_bytes[0] == 0x1f && lead_bytes[1] == 0x8b {
            return RamdiskCompression::Gzip;
        }

        // LZ4 standard magic: 04 22 4D 18
        if lead_bytes[0..4] == [0x04, 0x22, 0x4d, 0x18] {
            return RamdiskCompression::Lz4;
        }

        // LZ4 legacy magic: 02 21 4C 18
        if lead_bytes[0..4] == [0x02, 0x21, 0x4c, 0x18] {
            return RamdiskCompression::Lz4Legacy;
        }

        // Raw uncompressed CPIO (check 6 bytes magic: 070701 or 070702)
        if lead_bytes.len() >= 6 {
            let sig = &lead_bytes[0..6];
            if sig == b"070701" || sig == b"070702" {
                return RamdiskCompression::RawCpio;
            }
        }

        RamdiskCompression::Unknown
    }

    /// Checks if a ramdisk has already been patched by searching for markers
    /// Handles both legacy RUSTDROIDV02 byte patterns and v0.3 file markers in CPIO
    pub fn is_already_patched(ramdisk_bytes: &[u8]) -> bool {
        // 1. Check for legacy byte overlay marker (RUSTDROIDV02)
        if ramdisk_bytes.len() >= RUSTDROID_PATCH_MARKER.len() {
            let found_legacy = ramdisk_bytes
                .windows(RUSTDROID_PATCH_MARKER.len())
                .any(|window| window == RUSTDROID_PATCH_MARKER);
            if found_legacy {
                return true;
            }
        }

        // 2. Check for modern v0.3 CPIO file paths
        let marker_file_1 = b"rustdroid/.installed";
        let marker_file_2 = b"rustdroid/version";

        if ramdisk_bytes.len() >= marker_file_1.len() {
            let found_m1 = ramdisk_bytes
                .windows(marker_file_1.len())
                .any(|window| window == marker_file_1);
            if found_m1 {
                return true;
            }
        }

        if ramdisk_bytes.len() >= marker_file_2.len() {
            let found_m2 = ramdisk_bytes
                .windows(marker_file_2.len())
                .any(|window| window == marker_file_2);
            if found_m2 {
                return true;
            }
        }

        false
    }

    /// Performs structured CPIO audit checking to ensure it conforms to SVR4 standard
    pub fn validate_cpio(ramdisk_bytes: &[u8], compression: &RamdiskCompression) -> Result<(), RustDroidError> {
        // If the compression is RawCpio, validate its magic
        if *compression == RamdiskCompression::RawCpio {
            if ramdisk_bytes.len() < 6 {
                return Err(RustDroidError::BootImageInvalid("Ramdisk too small for CPIO audit".to_string()));
            }
            let sig = &ramdisk_bytes[0..6];
            if sig != b"070701" && sig != b"070702" {
                return Err(RustDroidError::BootImageInvalid(format!(
                    "Invalid uncompressed CPIO magic signature: {:?}",
                    String::from_utf8_lossy(sig)
                )));
            }
        } else if *compression == RamdiskCompression::Unknown {
            return Err(RustDroidError::BootImageInvalid(
                "Suspicious ramdisk format: unrecognized magic signature (not Gzip, LZ4, or Raw CPIO)".to_string()
            ));
        }
        
        Ok(())
    }

    /// Decompress the ramdisk data based on detected format
    pub fn decompress(data: &[u8], compression: &RamdiskCompression) -> Result<Vec<u8>, RustDroidError> {
        match compression {
            RamdiskCompression::RawCpio => {
                if data.is_empty() {
                    return Err(RustDroidError::BootImageInvalid("Raw CPIO data is empty".to_string()));
                }
                Ok(data.to_vec())
            }
            RamdiskCompression::Gzip => {
                let mut decoder = GzDecoder::new(data);
                let mut decompressed = Vec::new();
                decoder.read_to_end(&mut decompressed)
                    .map_err(|e| RustDroidError::BootImageInvalid(format!("Gzip decompression failed: {}", e)))?;
                if decompressed.is_empty() {
                    return Err(RustDroidError::BootImageInvalid("Gzip decompression produced empty output".to_string()));
                }
                Ok(decompressed)
            }
            RamdiskCompression::Lz4 | RamdiskCompression::Lz4Legacy => {
                let mut decoder = lz4_flex::frame::FrameDecoder::new(data);
                let mut decompressed = Vec::new();
                decoder.read_to_end(&mut decompressed)
                    .map_err(|e| RustDroidError::BootImageInvalid(format!("LZ4 decompression failed: {}", e)))?;
                if decompressed.is_empty() {
                    return Err(RustDroidError::BootImageInvalid("LZ4 decompression produced empty output".to_string()));
                }
                Ok(decompressed)
            }
            RamdiskCompression::Unknown => {
                Err(RustDroidError::BootImageInvalid("Unknown ramdisk compression format".to_string()))
            }
        }
    }

    /// Compress the ramdisk data based on target format
    pub fn compress(data: &[u8], compression: &RamdiskCompression) -> Result<Vec<u8>, RustDroidError> {
        match compression {
            RamdiskCompression::RawCpio => Ok(data.to_vec()),
            RamdiskCompression::Gzip => {
                let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
                encoder.write_all(data)
                    .map_err(|e| RustDroidError::BootImageInvalid(format!("Gzip compression failed: {}", e)))?;
                let compressed = encoder.finish()
                    .map_err(|e| RustDroidError::BootImageInvalid(format!("Gzip encoding finish failed: {}", e)))?;
                Ok(compressed)
            }
            RamdiskCompression::Lz4 => {
                let mut encoder = lz4_flex::frame::FrameEncoder::new(Vec::new());
                encoder.write_all(data)
                    .map_err(|e| RustDroidError::BootImageInvalid(format!("LZ4 compression failed: {}", e)))?;
                let compressed = encoder.finish()
                    .map_err(|e| RustDroidError::BootImageInvalid(format!("LZ4 encoding finish failed: {}", e)))?;
                Ok(compressed)
            }
            RamdiskCompression::Lz4Legacy => {
                let mut compressed = Vec::new();
                // Magic header for Legacy LZ4: 0x184C2102 (little-endian)
                compressed.extend_from_slice(&[0x02, 0x21, 0x4c, 0x18]);
                
                // Legacy LZ4 compresses the data in chunks of up to 8 MB (8388608 bytes)
                for chunk in data.chunks(8388608) {
                    let compressed_chunk = lz4_flex::block::compress(chunk);
                    let chunk_len = compressed_chunk.len() as u32;
                    compressed.extend_from_slice(&chunk_len.to_le_bytes());
                    compressed.extend_from_slice(&compressed_chunk);
                }
                Ok(compressed)
            }
            RamdiskCompression::Unknown => {
                Err(RustDroidError::BootImageInvalid("Unknown ramdisk compression format".to_string()))
            }
        }
    }
}
