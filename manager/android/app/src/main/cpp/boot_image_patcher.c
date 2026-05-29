#include "include/boot_image_patcher.h"
#include "include/boot_image_parser.h"
#include "include/checksum.h"
#include "include/native_log.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

static const char* PATCH_MARKER = "RUSTDROID_NATIVE_PATCH_V1";

static int ensure_dir(const char* dir_path) {
    struct stat st;
    if (stat(dir_path, &st) == 0 && S_ISDIR(st.st_mode)) {
        return 1;
    }

    char path[512];
    snprintf(path, sizeof(path), "%s", dir_path);
    size_t len = strlen(path);
    if (len == 0) return 0;

    for (size_t i = 1; i < len; ++i) {
        if (path[i] == '/') {
            path[i] = '\0';
            if (mkdir(path, 0755) != 0 && errno != EEXIST) {
                return 0;
            }
            path[i] = '/';
        }
    }
    if (mkdir(path, 0755) != 0 && errno != EEXIST) {
        return 0;
    }
    return 1;
}

static int copy_file(const char* src, const char* dst, char* err, size_t err_len) {
    FILE* in = fopen(src, "rb");
    if (!in) {
        snprintf(err, err_len, "failed to open input image");
        return 0;
    }

    FILE* out = fopen(dst, "wb");
    if (!out) {
        fclose(in);
        snprintf(err, err_len, "failed to open output image");
        return 0;
    }

    char buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), in)) > 0) {
        if (fwrite(buf, 1, n, out) != n) {
            fclose(in);
            fclose(out);
            snprintf(err, err_len, "failed while writing output image");
            return 0;
        }
    }

    if (ferror(in)) {
        fclose(in);
        fclose(out);
        snprintf(err, err_len, "failed while reading input image");
        return 0;
    }

    fclose(in);
    fclose(out);
    return 1;
}

int patch_boot_image_file(
    const char* input_path,
    const char* output_dir,
    boot_patch_result* out_result
) {
    if (!input_path || !output_dir || !out_result) {
        return 0;
    }
    memset(out_result, 0, sizeof(*out_result));

    boot_image_info info;
    if (!analyze_boot_image_file(input_path, &info)) {
        snprintf(out_result->error, sizeof(out_result->error), "%s", info.error[0] ? info.error : "invalid boot image");
        native_log_writef("patch", "error", "Patch rejected for %s: %s", input_path, out_result->error);
        return 0;
    }

    if (!ensure_dir(output_dir)) {
        snprintf(out_result->error, sizeof(out_result->error), "failed to create output directory");
        native_log_writef("patch", "error", "Patch failed for %s: %s", input_path, out_result->error);
        return 0;
    }

    time_t now = time(NULL);
    struct tm tm_buf;
    localtime_r(&now, &tm_buf);
    char timestamp[32];
    strftime(timestamp, sizeof(timestamp), "%Y%m%d_%H%M%S", &tm_buf);

    snprintf(out_result->output_filename, sizeof(out_result->output_filename), "boot_patched_%s.img", timestamp);
    snprintf(out_result->output_path, sizeof(out_result->output_path), "%s/%s", output_dir, out_result->output_filename);

    if (!copy_file(input_path, out_result->output_path, out_result->error, sizeof(out_result->error))) {
        native_log_writef("patch", "error", "Patch copy failed for %s: %s", input_path, out_result->error);
        return 0;
    }

    FILE* fp = fopen(out_result->output_path, "ab");
    if (!fp) {
        snprintf(out_result->error, sizeof(out_result->error), "failed to reopen output for patch marker");
        native_log_writef("patch", "error", "Patch marker write failed for %s", out_result->output_path);
        return 0;
    }
    fprintf(fp, "\n%s\nsource=%s\n", PATCH_MARKER, input_path);
    fclose(fp);

    if (!sha256_file_hex(out_result->output_path, out_result->output_sha256, out_result->error, sizeof(out_result->error))) {
        native_log_writef("patch", "error", "SHA-256 failed for %s", out_result->output_path);
        return 0;
    }

    out_result->success = 1;
    native_log_writef("patch", "info", "Patched image created: %s", out_result->output_path);
    return 1;
}
