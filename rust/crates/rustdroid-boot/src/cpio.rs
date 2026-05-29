use serde::{Deserialize, Serialize};
use rustdroid_common::RustDroidError;

/// Representation of a single SVR4/newc CPIO entry
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct CpioEntry {
    pub name: String,
    pub mode: u32,
    pub uid: u32,
    pub gid: u32,
    pub mtime: u32,
    pub content: Vec<u8>,
}

/// Parses raw newc/SVR4 CPIO bytes into structured CpioEntry vectors
pub fn parse_cpio(data: &[u8]) -> Result<Vec<CpioEntry>, RustDroidError> {
    let mut entries = Vec::new();
    let mut cursor = 0;

    while cursor + 110 <= data.len() {
        let magic = &data[cursor..cursor + 6];
        if magic != b"070701" && magic != b"070702" {
            return Err(RustDroidError::BootImageInvalid(format!(
                "Invalid CPIO magic: {:?}", String::from_utf8_lossy(magic)
            )));
        }

        // Parse 8-character hex field helper
        let parse_hex = |offset: usize| -> Result<u32, RustDroidError> {
            let s = std::str::from_utf8(&data[cursor + offset..cursor + offset + 8])
                .map_err(|e| RustDroidError::BootImageInvalid(e.to_string()))?;
            u32::from_str_radix(s, 16)
                .map_err(|e| RustDroidError::BootImageInvalid(format!("Invalid hex field in CPIO header: {}", e)))
        };

        let mode = parse_hex(14)?;
        let uid = parse_hex(22)?;
        let gid = parse_hex(30)?;
        let mtime = parse_hex(46)?;
        let filesize = parse_hex(54)?;
        let namesize = parse_hex(94)?;

        cursor += 110;

        if cursor + namesize as usize > data.len() {
            return Err(RustDroidError::BootImageInvalid("Incomplete CPIO archive: name out of bounds".to_string()));
        }

        // SVR4 CPIO names are null-terminated
        let mut name_bytes = data[cursor..cursor + namesize as usize].to_vec();
        if let Some(&0) = name_bytes.last() {
            name_bytes.pop();
        }
        let name = String::from_utf8(name_bytes)
            .map_err(|e| RustDroidError::BootImageInvalid(format!("CPIO filename not UTF-8: {}", e)))?;

        // 4-byte padding alignment for header + namesize
        let header_name_size = 110 + namesize as usize;
        let pad1 = if header_name_size % 4 == 0 { 0 } else { 4 - (header_name_size % 4) };
        cursor += namesize as usize + pad1;

        if name == "TRAILER!!!" {
            break;
        }

        if cursor + filesize as usize > data.len() {
            return Err(RustDroidError::BootImageInvalid(format!(
                "Incomplete CPIO archive: file content out of bounds for {}", name
            )));
        }

        let content = data[cursor..cursor + filesize as usize].to_vec();
        
        // 4-byte padding alignment for file content
        let pad2 = if filesize % 4 == 0 { 0 } else { 4 - (filesize % 4) } as usize;
        cursor += filesize as usize + pad2;

        entries.push(CpioEntry {
            name,
            mode,
            uid,
            gid,
            mtime,
            content,
        });
    }

    Ok(entries)
}

/// Serializes structured CpioEntry vectors back into SVR4 CPIO bytes
pub fn write_cpio(entries: &[CpioEntry]) -> Result<Vec<u8>, RustDroidError> {
    let mut out = Vec::new();

    for entry in entries {
        let name_bytes = entry.name.as_bytes();
        let namesize = name_bytes.len() + 1; // including terminal null

        let header = format!(
            "070701{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}",
            0, // ino
            entry.mode,
            entry.uid,
            entry.gid,
            1, // nlink
            entry.mtime,
            entry.content.len(),
            0, // devmajor
            0, // devminor
            0, // rdevmajor
            0, // rdevminor
            namesize,
            0 // checksum
        );
        out.extend_from_slice(header.as_bytes());

        // Write name plus null terminator
        out.extend_from_slice(name_bytes);
        out.push(0);

        // Header + name padding to 4 bytes
        let header_name_size = 110 + namesize;
        let pad1 = if header_name_size % 4 == 0 { 0 } else { 4 - (header_name_size % 4) };
        for _ in 0..pad1 {
            out.push(0);
        }

        // Write content
        out.extend_from_slice(&entry.content);

        // Content padding to 4 bytes
        let filesize = entry.content.len();
        let pad2 = if filesize % 4 == 0 { 0 } else { 4 - (filesize % 4) };
        for _ in 0..pad2 {
            out.push(0);
        }
    }

    // Append standard trailer record
    let trailer_name = b"TRAILER!!!\0";
    let namesize = trailer_name.len();
    let header = format!(
        "070701{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}",
        0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, namesize, 0
    );
    out.extend_from_slice(header.as_bytes());
    out.extend_from_slice(trailer_name);

    let trailer_size = 110 + namesize;
    let pad = if trailer_size % 4 == 0 { 0 } else { 4 - (trailer_size % 4) };
    for _ in 0..pad {
        out.push(0);
    }

    Ok(out)
}
