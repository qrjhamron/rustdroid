#ifndef RUSTDROID_RAMDISK_HANDLER_H
#define RUSTDROID_RAMDISK_HANDLER_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

int contains_patch_marker_in_file(const char* path);

#ifdef __cplusplus
}
#endif

#endif
