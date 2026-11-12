#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../common/protocol.h"

static const char *names[] = {"move_x", "move_y", "scroll", "down", "up", "heartbeat"};

int main(int argc, char **argv) {
    FILE *f = fopen(argc > 1 ? argv[1] : "../testvectors/protocol.txt", "r");
    if (!f) { perror("vectors"); return 2; }
    char line[256]; int n = 0, fail = 0;
    while (fgets(line, sizeof line, f)) {
        if (line[0] == '#' || line[0] == '\n') continue;
        char *bar = strchr(line, '|'); if (!bar) continue;
        *bar = 0;
        char name[32]; int want;
        if (sscanf(bar + 1, " %31s | %d", name, &want) != 2) { fail++; continue; }
        cm_decoder d; cm_decoder_init(&d); cm_event ev; int got = 0;
        for (char *p = line, *e; (unsigned long)(strtoul(p, &e, 16), e != p); p = e) {
            if (cm_decoder_feed(&d, (uint8_t)strtoul(p, NULL, 16), &ev)) got++;
        }
        n++;
        if (got != 1 || strcmp(names[ev.type], name) || ev.value != want) {
            printf("FAIL: %s-> %s %d (got %d events, last %s %d)\n", line, name, want, got, names[ev.type], ev.value);
            fail++;
        }
    }
    /* running status, realtime interleave, wrong channel, SysEx */
    {
        cm_decoder d; cm_decoder_init(&d); cm_event ev; int c = 0;
        uint8_t s[] = {0xB0,0x01,0x41, 0x02,0x42, 0xF8, 0x01,0x40, 0xB1,0x01,0x41, 0xF0,0x01,0x02,0xF7};
        for (size_t i = 0; i < sizeof s; i++) c += cm_decoder_feed(&d, s[i], &ev);
        n++; if (c != 3) { printf("FAIL: stream test got %d, want 3\n", c); fail++; }
    }
    printf("%d tests, %d failures\n", n, fail);
    return fail != 0;
}
