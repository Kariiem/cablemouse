#include <stdio.h>
#include <string.h>
#include "../common/stream_check.h"

static int fails;
#define CHECK(cond, what) do { if (!(cond)) { printf("FAIL: %s\n", what); fails++; } } while (0)

/* Feeds messages 0..count-1 two ms apart, skipping the indexes in skip[] and repeating the one at dup_at. */
static cm_stream run(int count, const int *skip, int nskip, int dup_at, int stall_at) {
    cm_stream s; cm_stream_reset(&s);
    double t = 1000;
    for (int n = 0; n < count; n++, t += 2) {
        int skipped = 0;
        for (int i = 0; i < nskip; i++) if (skip[i] == n) skipped = 1;
        if (n == stall_at) t += 50;                 /* one 50 ms stall */
        if (skipped) continue;
        cm_stream_feed(&s, (1 + n % 127) - 64, t);
        if (n == dup_at) cm_stream_feed(&s, (1 + n % 127) - 64, t);
    }
    return s;
}

int main(void) {
    char line[400];
    { cm_stream s = run(5000, NULL, 0, -1, -1);
      CHECK(cm_stream_summary(&s, line, sizeof line) == 1, "perfect stream passes");
      CHECK(s.n == 5000 && s.missing == 0 && s.repeats == 0 && s.slow == 0, "perfect stream counts");
      CHECK(!cm_stream_finished(&s, s.last + 999, 1000), "not finished before the idle time");
      CHECK(cm_stream_finished(&s, s.last + 1000, 1000), "finished after the idle time"); }
    { int skip[] = {10, 11, 500};
      cm_stream s = run(1000, skip, 3, 700, -1);
      CHECK(cm_stream_summary(&s, line, sizeof line) == 0, "damaged stream fails");
      CHECK(s.missing == 3 && s.repeats == 1 && s.n == 998, "missing and repeated are counted"); }
    { cm_stream s = run(1000, NULL, 0, -1, 400);
      CHECK(s.slow == 1 && s.maxgap > 50, "one stall is reported as one slow gap");
      CHECK(cm_stream_summary(&s, line, sizeof line) == 1, "a stall alone is not a loss"); }
    { /* wrap-around: the cycle restarts after 127 without counting a loss */
      cm_stream s = run(300, NULL, 0, -1, -1);
      CHECK(s.missing == 0, "cycle wrap is not a loss"); }
    { cm_stream s; cm_stream_reset(&s);
      CHECK(!cm_stream_finished(&s, 1e9, 1000), "an empty stream is never finished"); }
    printf("%d stream failures\n", fails);
    return fails != 0;
}
