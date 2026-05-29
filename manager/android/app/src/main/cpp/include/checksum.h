#ifndef RUSTDROID_CHECKSUM_H
#define RUSTDROID_CHECKSUM_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint32_t state[8];
    uint64_t bit_len;
    uint8_t data[64];
    size_t data_len;
} sha256_ctx;

void sha256_init(sha256_ctx* ctx);
void sha256_update(sha256_ctx* ctx, const uint8_t* data, size_t len);
void sha256_final(sha256_ctx* ctx, uint8_t hash[32]);
int sha256_file_hex(const char* path, char out_hex[65], char* err, size_t err_len);

#ifdef __cplusplus
}
#endif

#endif
