/*
 * json_mini.h - the few JSON accessors the NeuroPad daemon needs.
 *
 * Not a general parser: it reads flat, single-line objects produced by the app, with
 * integer, string and integer-array values. Kept in a header so that test_json.c can
 * exercise it on any host.
 */
#ifndef NEUROPAD_JSON_MINI_H
#define NEUROPAD_JSON_MINI_H

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Returns a pointer just past `"key"` and the colon that follows, or NULL. */
static const char *find_value(const char *line, const char *key) {
    char pattern[64];
    snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    const char *p = line;
    size_t n = strlen(pattern);
    while ((p = strstr(p, pattern)) != NULL) {
        const char *q = p + n;
        while (*q == ' ' || *q == '\t') q++;
        if (*q == ':') {
            q++;
            while (*q == ' ' || *q == '\t') q++;
            return q;
        }
        p += n; /* matched a string value, not a key: keep looking */
    }
    return NULL;
}

static int get_int(const char *line, const char *key, int fallback) {
    const char *v = find_value(line, key);
    return v ? (int)strtol(v, NULL, 10) : fallback;
}

/* Copies a JSON string value (no escape handling needed for our names). */
static int get_string(const char *line, const char *key, char *out, size_t out_size) {
    const char *v = find_value(line, key);
    if (!v || *v != '"') return 0;
    v++;
    const char *end = strchr(v, '"');
    if (!end) return 0;
    size_t len = (size_t)(end - v);
    if (len >= out_size) len = out_size - 1;
    memcpy(out, v, len);
    out[len] = '\0';
    return 1;
}

static int get_int_array(const char *line, const char *key, uint8_t *out, int max_len) {
    const char *p = find_value(line, key);
    if (!p || *p != '[') return 0;
    p++;
    int count = 0;
    while (*p && *p != ']' && count < max_len) {
        char *end;
        long value = strtol(p, &end, 0);
        if (end == p) break; /* not a number: stop instead of looping forever */
        out[count++] = (uint8_t)value;
        p = end;
        while (*p == ',' || *p == ' ' || *p == '\r' || *p == '\n' || *p == '\t') p++;
    }
    return count;
}

#endif /* NEUROPAD_JSON_MINI_H */
