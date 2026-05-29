#ifndef RUSTDROID_BOOT_IMAGE_PARSER_H
#define RUSTDROID_BOOT_IMAGE_PARSER_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int valid;
    char error[256];
    uint32_t header_version;
    uint32_t page_size;
    uint32_t kernel_size;
    uint32_t ramdisk_size;
    uint32_t ramdisk_offset;
    int is_init_boot;
    int avb_footer_detected;
    int already_patched;
    char format[48];
} boot_image_info;

int analyze_boot_image_file(const char* path, boot_image_info* out_info);

#ifdef __cplusplus
}
#endif

#endif
