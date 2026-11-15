#include "stream_check.h"
#include <stdio.h>
#include <string.h>

void cm_stream_reset(cm_stream *s) { memset(s, 0, sizeof *s); }

void cm_stream_feed(cm_stream *s, int move_x_value, double now_ms) {
    int b = move_x_value + 64;
    if (s->n) {
        int want = s->prev % 127 + 1;
        if (b == s->prev) s->repeats++;
        else if (b != want) s->missing += (b - want + 127) % 127;
        double gap = now_ms - s->last;
        if (gap > s->maxgap) s->maxgap = gap;
        if (gap > CM_STREAM_SLOW_MS) s->slow++;
    } else {
        s->first = now_ms;
    }
    s->prev = b; s->last = now_ms; s->n++;
}

int cm_stream_finished(const cm_stream *s, double now_ms, double idle_ms) {
    return s->n > 0 && now_ms - s->last >= idle_ms;
}

int cm_stream_summary(const cm_stream *s, char *out, size_t out_size) {
    double secs = (s->last - s->first) / 1000.0;
    int pass = s->missing == 0 && s->repeats == 0;
    snprintf(out, out_size,
             "received %ld messages in %.2f s (%.0f/s); missing %ld, repeated %ld; longest gap %.1f ms, gaps over %d ms: %ld\n"
             "stream: %s",
             s->n, secs, secs > 0 ? (s->n - 1) / secs : 0.0, s->missing, s->repeats, s->maxgap, CM_STREAM_SLOW_MS, s->slow,
             pass ? "PASS: nothing dropped" : "FAIL: messages were lost or repeated");
    return pass;
}
