#ifndef RUSTDROID_NATIVE_LOG_H
#define RUSTDROID_NATIVE_LOG_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

void native_log_write(const char* category, const char* level, const char* message);
void native_log_writef(const char* category, const char* level, const char* fmt, ...);
char* native_log_get_json(const char* category);
int native_log_clear(const char* category);

#ifdef __cplusplus
}
#endif

#endif
