#ifndef RUSTDROID_BOOT_IMAGE_PATCHER_H
#define RUSTDROID_BOOT_IMAGE_PATCHER_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int success;
    char output_path[512];
    char output_filename[128];
    char output_sha256[65];
    char error[256];
} boot_patch_result;

int patch_boot_image_file(
    const char* input_path,
    const char* output_dir,
    boot_patch_result* out_result
);

#ifdef __cplusplus
}
#endif

#endif
