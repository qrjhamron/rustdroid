import struct

# Construct a SVR4 CPIO archive containing a single file: init.rc
name = b"init.rc"
content = b"import /init.environ.rc\n"

# Header format: 070701 + ino + mode + uid + gid + nlink + mtime + filesize + devmajor + devminor + rdevmajor + rdevminor + namesize + checksum
namesize = len(name) + 1
filesize = len(content)

cpio_hdr = f"070701{0:08x}{0o100644:08x}{0:08x}{0:08x}{1:08x}{0:08x}{filesize:08x}{0:08x}{0:08x}{0:08x}{0:08x}{namesize:08x}{0:08x}".encode('ascii')
cpio_entry = cpio_hdr + name + b'\0'
# Padding for name
pad1 = (4 - (len(cpio_entry) % 4)) % 4
cpio_entry += b'\0' * pad1
cpio_entry += content
# Padding for content
pad2 = (4 - (len(cpio_entry) % 4)) % 4
cpio_entry += b'\0' * pad2

# Trailer
trailer_name = b"TRAILER!!!"
namesize_t = len(trailer_name) + 1
cpio_trailer_hdr = f"070701{0:08x}{0:08x}{0:08x}{0:08x}{1:08x}{0:08x}{0:08x}{0:08x}{0:08x}{0:08x}{0:08x}{namesize_t:08x}{0:08x}".encode('ascii')
cpio_trailer = cpio_trailer_hdr + trailer_name + b'\0'
pad_t = (4 - (len(cpio_trailer) % 4)) % 4
cpio_trailer += b'\0' * pad_t

cpio_archive = cpio_entry + cpio_trailer

# Now build the boot image
boot_img = bytearray(8192)

# Magic
boot_img[0:8] = b"ANDROID!"
# Kernel size = 1024
boot_img[8:12] = struct.pack("<I", 1024)
# Ramdisk size
boot_img[16:20] = struct.pack("<I", len(cpio_archive))
# Page size = 2048
boot_img[36:40] = struct.pack("<I", 2048)
# Version = 0
boot_img[40:44] = struct.pack("<I", 0)

# Write ramdisk to offset 4096
ramdisk_offset = 4096
boot_img[ramdisk_offset:ramdisk_offset + len(cpio_archive)] = cpio_archive

with open("mock_init_boot.img", "wb") as f:
    f.write(boot_img)

print("Generated mock_init_boot.img successfully!")
