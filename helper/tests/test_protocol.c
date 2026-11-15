#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../common/protocol.h"

/* Vector-file names of the events, in cm_ev_type order. */
static const char *names[] = {"move_x", "move_y", "scroll", "down", "up", "heartbeat", "key_char", "key_special", "mods", "key_hid", "move_abs"};

int main(int argc, char **argv) {
    FILE *f = fopen(argc > 1 ? argv[1] : "../testvectors/protocol.txt", "r");
    if (!f) { perror("vectors"); return 2; }
    char line[256]; int n = 0, fail = 0;
    while (fgets(line, sizeof line, f)) {
        if (line[0] == '#' || line[0] == '\n') continue;
        char *bar = strchr(line, '|'); if (!bar) continue;
        *bar = 0;
        char name[32]; int want;
        if (sscanf(bar + 1, " %31s | %d", name, &want) != 2) { printf("FAIL: bad vector line: %s|%s", line, bar + 1); n++; fail++; continue; }
        cm_decoder d; cm_decoder_init(&d); cm_event ev = {EV_HEARTBEAT, 0}; int got = 0;
        for (char *p = line, *end;; p = end) {
            unsigned long byte = strtoul(p, &end, 16);
            if (end == p) break;
            if (cm_decoder_feed(&d, (uint8_t)byte, &ev)) got++;
        }
        n++;
        if (got != 1 || strcmp(names[ev.type], name) || ev.value != want) {
            printf("FAIL: %s-> want %s %d, got %d event(s)", line, name, want, got);
            if (got) printf(", last %s %d", names[ev.type], ev.value);
            printf("\n");
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
    /* a 12-byte absolute position and a 9-byte character split across reads, with a realtime byte inside */
    {
        cm_decoder d; cm_decoder_init(&d); cm_event ev = {EV_HEARTBEAT, 0}; int c = 0;
        uint8_t s[] = {0xB0,0x16,0x07, 0xB0,0x17,0x68, 0xFE, 0xB0,0x18,0x0F, 0xB0,0x19,0x50};
        for (size_t i = 0; i < sizeof s; i++) c += cm_decoder_feed(&d, s[i], &ev);
        n++; if (c != 1 || ev.type != EV_MOVE_ABS || ev.value >> 14 != 1000 || (ev.value & CM_ABS_MAX) != 2000) {
            printf("FAIL: absolute position 1000,2000 decoded as %d events, %d,%d\n", c, ev.value >> 14, ev.value & CM_ABS_MAX); fail++;
        }
    }
    /* invalid text input must produce no event */
    {
        static const struct { uint8_t s[9]; int len; const char *what; } cases[] = {
            {{0xB0,0x10,0x00, 0xB0,0x11,0x00, 0xB0,0x12,0x00}, 9, "NUL"},
            {{0xB0,0x10,0x03, 0xB0,0x11,0x30, 0xB0,0x12,0x00}, 9, "U+D800 surrogate"},
            {{0xB0,0x10,0x7F, 0xB0,0x11,0x7F, 0xB0,0x12,0x7F}, 9, "0x1FFFFF, beyond U+10FFFF"},
            {{0xB0,0x13,0x00}, 3, "special key 0"},
            {{0xB0,0x13,0x0E}, 3, "special key 14"},
            {{0xB0,0x14,0x10}, 3, "modifier bits above Win"},
            {{0xB0,0x15,0x03}, 3, "HID usage below A"},
            {{0xB0,0x15,0x74}, 3, "HID usage above F24"},
        };
        for (size_t k = 0; k < sizeof cases / sizeof cases[0]; k++) {
            cm_decoder d; cm_decoder_init(&d); cm_event ev; int c = 0;
            for (int i = 0; i < cases[k].len; i++) c += cm_decoder_feed(&d, cases[k].s[i], &ev);
            n++; if (c != 0) { printf("FAIL: %s produced %d events\n", cases[k].what, c); fail++; }
        }
    }
    printf("%d tests, %d failures\n", n, fail);
    return fail != 0;
}
