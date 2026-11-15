#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../common/protocol.h"

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
