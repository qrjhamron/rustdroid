#include "include/ramdisk_handler.h"

#include <stdio.h>
#include <string.h>

static const char* PATCH_MARKER = "RUSTDROID_NATIVE_PATCH_V1";

int contains_patch_marker_in_file(const char* path) {
    FILE* fp = fopen(path, "rb");
    if (!fp) {
        return 0;
    }

    char chunk[4096 + 64];
    size_t marker_len = strlen(PATCH_MARKER);
    size_t carry = 0;

    while (!feof(fp)) {
        size_t n = fread(chunk + carry, 1, 4096, fp);
        if (n == 0) break;
        size_t total = n + carry;

        if (total >= marker_len) {
            for (size_t i = 0; i + marker_len <= total; ++i) {
                if (memcmp(chunk + i, PATCH_MARKER, marker_len) == 0) {
                    fclose(fp);
                    return 1;
                }
            }
        }

        carry = marker_len > 1 && total >= marker_len - 1 ? marker_len - 1 : total;
        if (carry > 0) {
            memmove(chunk, chunk + total - carry, carry);
        }
    }

    fclose(fp);
    return 0;
}
