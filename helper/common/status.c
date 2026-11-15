#include "status.h"
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

static char last[320];

void cm_status(const char *fmt, ...) {
    char line[sizeof last];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof line, fmt, ap);
    va_end(ap);
    if (strcmp(line, last) == 0) return;
    memcpy(last, line, sizeof last);
    printf("%s\n", line);
    fflush(stdout);
}

void cm_status_reset(void) { last[0] = 0; }
