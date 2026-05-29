#include "include/boot_image_parser.h"
#include "include/ramdisk_handler.h"

#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

static uint32_t read_u32_le(const unsigned char* p) {
    return ((uint32_t)p[0]) | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static int read_tail_has_avb_footer(const char* path) {
    FILE* fp = fopen(path, "rb");
    if (!fp) return 0;
    if (fseek(fp, 0, SEEK_END) != 0) {
        fclose(fp);
        return 0;
    }
    long size = ftell(fp);
    if (size < 64) {
        fclose(fp);
        return 0;
    }
    if (fseek(fp, size - 64, SEEK_SET) != 0) {
        fclose(fp);
        return 0;
    }

    unsigned char tail[64];
    size_t n = fread(tail, 1, sizeof(tail), fp);
    fclose(fp);
    if (n < sizeof(tail)) {
        return 0;
    }
    for (size_t i = 0; i + 4 <= sizeof(tail); ++i) {
        if (memcmp(tail + i, "AVBf", 4) == 0) {
            return 1;
        }
    }
    return 0;
}

int analyze_boot_image_file(const char* path, boot_image_info* out_info) {
    if (!path || !out_info) {
        return 0;
    }

    memset(out_info, 0, sizeof(*out_info));
    snprintf(out_info->format, sizeof(out_info->format), "unknown");

    struct stat st;
    if (stat(path, &st) != 0) {
        snprintf(out_info->error, sizeof(out_info->error), "image path not found");
        return 0;
    }
    if (st.st_size < 64) {
        snprintf(out_info->error, sizeof(out_info->error), "image too small");
        return 0;
    }

    FILE* fp = fopen(path, "rb");
    if (!fp) {
        snprintf(out_info->error, sizeof(out_info->error), "failed to open image file");
        return 0;
    }

    unsigned char header[64];
    size_t n = fread(header, 1, sizeof(header), fp);
    fclose(fp);
    if (n < sizeof(header)) {
        snprintf(out_info->error, sizeof(out_info->error), "failed to read boot header");
        return 0;
    }

    if (memcmp(header, "ANDROID!", 8) != 0) {
        snprintf(out_info->error, sizeof(out_info->error), "invalid Android boot image magic");
        return 0;
    }

    uint32_t header_version = read_u32_le(header + 40);
    if (header_version > 4) {
        snprintf(out_info->error, sizeof(out_info->error), "unsupported header version: %u", header_version);
        return 0;
    }

    uint32_t kernel_size = read_u32_le(header + 8);
    uint32_t ramdisk_size;
    uint32_t page_size;

    if (header_version < 3) {
        ramdisk_size = read_u32_le(header + 16);
        page_size = read_u32_le(header + 36);
    } else {
        ramdisk_size = read_u32_le(header + 12);
        page_size = 4096;
    }

    if (page_size == 0) {
        snprintf(out_info->error, sizeof(out_info->error), "invalid page size");
        return 0;
    }

    uint32_t kernel_pages = (kernel_size + page_size - 1) / page_size;
    uint32_t ramdisk_offset = header_version < 3 ? (1 + kernel_pages) * page_size : 4096;

    if ((long long)ramdisk_offset + (long long)ramdisk_size > st.st_size) {
        snprintf(out_info->error, sizeof(out_info->error), "ramdisk extends beyond file bounds");
        return 0;
    }

    out_info->valid = 1;
    out_info->header_version = header_version;
    out_info->kernel_size = kernel_size;
    out_info->ramdisk_size = ramdisk_size;
    out_info->page_size = page_size;
    out_info->ramdisk_offset = ramdisk_offset;
    out_info->is_init_boot = (header_version >= 3 && kernel_size == 0) ? 1 : 0;
    out_info->avb_footer_detected = read_tail_has_avb_footer(path);
    out_info->already_patched = contains_patch_marker_in_file(path);
    snprintf(out_info->format, sizeof(out_info->format), "Android boot image v%u", header_version);

    return 1;
}
