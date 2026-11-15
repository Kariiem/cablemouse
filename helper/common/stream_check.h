#ifndef CM_STREAM_CHECK_H
#define CM_STREAM_CHECK_H
#include <stddef.h>

/* Verifies the phone's "Stream test": move_x messages whose byte value cycles 1..127 (b = 1 + n % 127),
 * about 2 ms apart. Pure logic: the caller supplies the clock, so it is host-testable. */
typedef struct {
    long n, missing, repeats, slow;     /* received, skipped, repeated, gaps over CM_STREAM_SLOW_MS */
    int prev;
    double first, last, maxgap;         /* timestamps and the longest gap, in ms */
} cm_stream;

#define CM_STREAM_SLOW_MS 20

void cm_stream_reset(cm_stream *s);
/* move_x_value is the decoded EV_MOVE_X value (byte - 64). */
void cm_stream_feed(cm_stream *s, int move_x_value, double now_ms);
/* 1 once a stream has been seen and nothing arrived for idle_ms. */
int  cm_stream_finished(const cm_stream *s, double now_ms, double idle_ms);
/* Writes a one-line summary and returns 1 for PASS (nothing missing, nothing repeated), else 0. */
int  cm_stream_summary(const cm_stream *s, char *out, size_t out_size);

#endif
