use serde::{Deserialize, Serialize};
use rustdroid_common::RustDroidError;

pub const BOOT_MAGIC: &[u8; 8] = b"ANDROID!";

/// Parsed fields from Android Boot Header (v0-v4 support)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BootHeaderInfo {
    pub magic: [u8; 8],
    pub header_version: u32,
    pub page_size: u32,
    pub kernel_size: u32,
    pub ramdisk_size: u32,
    pub os_version: u32,
    pub is_init_boot: bool,
}

impl BootHeaderInfo {
    /// Parse raw image header bytes safely, enforcing strict bounds check
    pub fn parse(bytes: &[u8]) -> Result<Self, RustDroidError> {
        if bytes.len() < 64 {
            return Err(RustDroidError::BootImageInvalid(
                "Image too small to contain a valid boot header".to_string(),
            ));
        }

        // 1. Validate magic bytes
        let mut magic = [0u8; 8];
        magic.copy_from_slice(&bytes[0..8]);
        if &magic != BOOT_MAGIC {
            return Err(RustDroidError::BootImageInvalid(
                "Invalid boot image signature magic".to_string(),
            ));
        }

        // 2. Parse header version (located at offset 40 in both v0-v2 and v3-v4)
        let header_version = u32::from_le_bytes(
            bytes[40..44]
                .try_into()
                .map_err(|e: std::array::TryFromSliceError| RustDroidError::BootImageInvalid(e.to_string()))?
        );

        if header_version > 4 {
            return Err(RustDroidError::BootImageInvalid(format!(
                "Unsupported boot header version: {}",
                header_version
            )));
        }

        let kernel_size: u32;
        let ramdisk_size: u32;
        let page_size: u32;
        let os_version: u32;
        let mut is_init_boot = false;

        if header_version < 3 {
            // v0/v1/v2 Layout:
            // 0..8: Magic
            // 8..12: kernel_size
            // 12..16: kernel_addr
            // 16..20: ramdisk_size
            // 20..24: ramdisk_addr
            // 24..28: second_size
            // 28..32: second_addr
            // 32..36: tags_addr
            // 36..40: page_size
            // 40..44: header_version
            // 44..48: os_version (combined OS version and patch level)
            kernel_size = u32::from_le_bytes(bytes[8..12].try_into().unwrap());
            ramdisk_size = u32::from_le_bytes(bytes[16..20].try_into().unwrap());
            page_size = u32::from_le_bytes(bytes[36..40].try_into().unwrap());
            os_version = u32::from_le_bytes(bytes[44..48].try_into().unwrap());
        } else {
            // v3/v4 Layout:
            // 0..8: Magic
            // 8..12: kernel_size
            // 12..16: ramdisk_size
            // 16..20: os_version
            // 20..24: header_size
            // ...
            // 40..44: header_version
            kernel_size = u32::from_le_bytes(bytes[8..12].try_into().unwrap());
            ramdisk_size = u32::from_le_bytes(bytes[12..16].try_into().unwrap());
            os_version = u32::from_le_bytes(bytes[16..20].try_into().unwrap());
            page_size = 4096; // Fixed page size for v3/v4

            // init_boot has no kernel size (kernel size = 0)
            if kernel_size == 0 {
                is_init_boot = true;
            }
        }

        // Validate values
        if page_size == 0 || (page_size & (page_size - 1)) != 0 {
            return Err(RustDroidError::BootImageInvalid(format!(
                "Invalid page size: {}",
                page_size
            )));
        }

        Ok(BootHeaderInfo {
            magic,
            header_version,
            page_size,
            kernel_size,
            ramdisk_size,
            os_version,
            is_init_boot,
        })
    }

    /// Calculates the file offset where the ramdisk starts in the boot image
    pub fn get_ramdisk_offset(&self) -> usize {
        if self.header_version < 3 {
            // Ramdisk starts at page 1 (kernel takes pages, rounded up)
            let kernel_pages = (self.kernel_size + self.page_size - 1) / self.page_size;
            ((1 + kernel_pages) * self.page_size) as usize
        } else {
            // v3/v4: Page size is fixed at 4096. Header takes 1 page.
            // Ramdisk starts immediately at page 1.
            4096
        }
    }
}
