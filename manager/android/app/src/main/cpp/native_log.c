#include "include/native_log.h"

#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define LOG_CAPACITY 256
#define LOG_MSG_MAX 512
#define LOG_CAT_MAX 32
#define LOG_LEVEL_MAX 16

typedef struct {
    char ts[32];
    char category[LOG_CAT_MAX];
    char level[LOG_LEVEL_MAX];
    char message[LOG_MSG_MAX];
    int in_use;
} native_log_entry;

static native_log_entry g_logs[LOG_CAPACITY];
static int g_log_count = 0;
static int g_log_start = 0;
static pthread_mutex_t g_log_mutex = PTHREAD_MUTEX_INITIALIZER;

static void format_timestamp(char out[32]) {
    time_t now = time(NULL);
    struct tm tm_buf;
    gmtime_r(&now, &tm_buf);
    strftime(out, 32, "%Y-%m-%dT%H:%M:%SZ", &tm_buf);
}

static void append_escaped(char* dst, size_t dst_len, const char* src) {
    size_t j = strlen(dst);
    for (size_t i = 0; src[i] != '\0' && j + 2 < dst_len; ++i) {
        char c = src[i];
        if (c == '"' || c == '\\') {
            if (j + 2 >= dst_len) break;
            dst[j++] = '\\';
            dst[j++] = c;
        } else if (c == '\n' || c == '\r' || c == '\t') {
            if (j + 2 >= dst_len) break;
            dst[j++] = '\\';
            dst[j++] = (c == '\n') ? 'n' : (c == '\r' ? 'r' : 't');
        } else {
            dst[j++] = c;
        }
    }
    dst[j] = '\0';
}

void native_log_write(const char* category, const char* level, const char* message) {
    if (!category || !level || !message) {
        return;
    }

    pthread_mutex_lock(&g_log_mutex);

    int index;
    if (g_log_count < LOG_CAPACITY) {
        index = (g_log_start + g_log_count) % LOG_CAPACITY;
        g_log_count++;
    } else {
        index = g_log_start;
        g_log_start = (g_log_start + 1) % LOG_CAPACITY;
    }

    native_log_entry* e = &g_logs[index];
    memset(e, 0, sizeof(*e));
    format_timestamp(e->ts);
    snprintf(e->category, LOG_CAT_MAX, "%s", category);
    snprintf(e->level, LOG_LEVEL_MAX, "%s", level);
    snprintf(e->message, LOG_MSG_MAX, "%s", message);
    e->in_use = 1;

    pthread_mutex_unlock(&g_log_mutex);
}

void native_log_writef(const char* category, const char* level, const char* fmt, ...) {
    char buffer[LOG_MSG_MAX];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    native_log_write(category, level, buffer);
}

char* native_log_get_json(const char* category) {
    const char* filter = category ? category : "";
    size_t capacity = 64 * 1024;
    char* json = (char*)malloc(capacity);
    if (!json) {
        return NULL;
    }
    snprintf(json, capacity, "{\"status\":\"success\",\"category\":\"%s\",\"entries\":[", filter);

    pthread_mutex_lock(&g_log_mutex);
    int first = 1;
    for (int i = 0; i < g_log_count; ++i) {
        int idx = (g_log_start + i) % LOG_CAPACITY;
        native_log_entry* e = &g_logs[idx];
        if (!e->in_use) continue;
        if (filter[0] != '\0' && strcmp(filter, "all") != 0 && strcmp(e->category, filter) != 0) {
            continue;
        }

        char esc_msg[LOG_MSG_MAX * 2];
        char esc_level[LOG_LEVEL_MAX * 2];
        char esc_cat[LOG_CAT_MAX * 2];
        esc_msg[0] = '\0';
        esc_level[0] = '\0';
        esc_cat[0] = '\0';
        append_escaped(esc_msg, sizeof(esc_msg), e->message);
        append_escaped(esc_level, sizeof(esc_level), e->level);
        append_escaped(esc_cat, sizeof(esc_cat), e->category);

        char row[1600];
        snprintf(row, sizeof(row), "%s{\"timestamp\":\"%s\",\"category\":\"%s\",\"level\":\"%s\",\"message\":\"%s\"}",
                 first ? "" : ",", e->ts, esc_cat, esc_level, esc_msg);
        first = 0;

        if (strlen(json) + strlen(row) + 4 >= capacity) {
            capacity *= 2;
            char* grown = (char*)realloc(json, capacity);
            if (!grown) {
                pthread_mutex_unlock(&g_log_mutex);
                free(json);
                return NULL;
            }
            json = grown;
        }
        strcat(json, row);
    }
    pthread_mutex_unlock(&g_log_mutex);

    strcat(json, "]}");
    return json;
}

int native_log_clear(const char* category) {
    const char* filter = category ? category : "";
    pthread_mutex_lock(&g_log_mutex);

    if (filter[0] == '\0' || strcmp(filter, "all") == 0) {
        memset(g_logs, 0, sizeof(g_logs));
        g_log_count = 0;
        g_log_start = 0;
        pthread_mutex_unlock(&g_log_mutex);
        return 1;
    }

    native_log_entry kept[LOG_CAPACITY];
    memset(kept, 0, sizeof(kept));
    int kept_count = 0;
    for (int i = 0; i < g_log_count; ++i) {
        int idx = (g_log_start + i) % LOG_CAPACITY;
        native_log_entry* e = &g_logs[idx];
        if (!e->in_use) continue;
        if (strcmp(e->category, filter) != 0) {
            kept[kept_count++] = *e;
        }
    }

    memset(g_logs, 0, sizeof(g_logs));
    for (int i = 0; i < kept_count; ++i) {
        g_logs[i] = kept[i];
    }
    g_log_count = kept_count;
    g_log_start = 0;

    pthread_mutex_unlock(&g_log_mutex);
    return 1;
}
