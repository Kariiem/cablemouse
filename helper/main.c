/* Cable Mouse helper: reads the phone's MIDI messages and turns them into mouse and keyboard input.
 *
 * Layers (see PLAN.md):  backend_*  (platform: MIDI device + input injection, backend.h)
 *                        cm_decoder (pure: bytes -> events, common/protocol.h)
 *                        this file  (policy: reconnecting, heartbeat timeout, releasing held input)
 */
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "backend.h"
#include "common/protocol.h"
#include "common/stream_check.h"

#define HEARTBEAT_TIMEOUT_MS 2000
#define POLL_MS 100
#define REOPEN_IDLE_MS 6000     /* no data this long -> close and reopen the device */
#define REEXEC_AFTER_MS 10000   /* device unavailable this long after working once -> restart process */
#define STREAM_IDLE_MS 1000     /* a stream test is over once nothing arrives for this long */

#ifdef _WIN32
#include <windows.h>
#define SLEEP_MS(x) Sleep(x)
#else
#include <unistd.h>
#define SLEEP_MS(x) usleep((x) * 1000)
#endif

static char **g_argv;
static int stream_check_mode;       /* --stream-check: verify the phone's stream test instead of injecting input */
static cm_stream stream;

/* Input currently held down on the PC, so it can be released when the phone goes away. */
static int buttons_held[3];
static int mods_held;

/* Relative motion collected from one read, applied once. */
typedef struct { int dx, dy, scroll; } motion;

static double now_ms(void) {
#ifdef _WIN32
    LARGE_INTEGER f, c; QueryPerformanceFrequency(&f); QueryPerformanceCounter(&c);
    return 1000.0 * (double)c.QuadPart / (double)f.QuadPart;
#else
    struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
    return 1000.0 * t.tv_sec + t.tv_nsec / 1e6;
#endif
}

static void release_all(void) {
    for (int i = 0; i < 3; i++) if (buttons_held[i]) { backend_button(i, 0); buttons_held[i] = 0; }
    if (mods_held) { backend_mods(0); mods_held = 0; }
}

/* Applies one decoded event. Motion is only collected here; the caller applies it after the whole read. */
static void apply_event(const cm_event *ev, motion *m) {
    static int warned_version;
    if (stream_check_mode && ev->type != EV_HEARTBEAT) {      /* the check only counts; nothing is injected */
        if (ev->type == EV_MOVE_X) cm_stream_feed(&stream, ev->value, now_ms());
        return;
    }
    switch (ev->type) {
    case EV_MOVE_X: m->dx += ev->value; break;
    case EV_MOVE_Y: m->dy += ev->value; break;
    case EV_SCROLL: m->scroll += ev->value; break;
    case EV_BTN_DOWN: backend_button(ev->value, 1); buttons_held[ev->value] = 1; break;
    case EV_BTN_UP:   backend_button(ev->value, 0); buttons_held[ev->value] = 0; break;
    case EV_KEY_CHAR: backend_key_char((uint32_t)ev->value); break;
    case EV_KEY_SPECIAL: backend_key(ev->value); break;
    case EV_MODS: backend_mods(ev->value); mods_held = ev->value; break;
    case EV_KEY_HID: backend_key_hid(ev->value); break;
    case EV_MOVE_ABS: backend_move_abs(ev->value >> 14, ev->value & CM_ABS_MAX); break;
    case EV_HEARTBEAT:
        if (ev->value != CM_PROTOCOL_VERSION && !warned_version) {
            warned_version = 1;
            printf("warning: phone protocol v%d, helper v%d\n", ev->value, CM_PROTOCOL_VERSION);
        }
        break;
    }
}

/* In --stream-check mode: once the stream has ended, print the result and start over. */
static void report_stream_if_finished(void) {
    if (!stream_check_mode || !cm_stream_finished(&stream, now_ms(), STREAM_IDLE_MS)) return;
    char line[400];
    cm_stream_summary(&stream, line, sizeof line);
    printf("stream: %s\n", line);
    cm_stream_reset(&stream);
}

/* Last-resort recovery: winmm can keep a stale device list/handle until the process restarts,
 * so after the device has been gone for a while we simply start a fresh copy of ourselves. */
static void restart_self(void) {
    printf("Restarting helper to refresh the MIDI device list...\n");
#ifdef _WIN32
    char path[MAX_PATH]; char cmd[2048];
    GetModuleFileNameA(NULL, path, sizeof path);
    snprintf(cmd, sizeof cmd, "%s", GetCommandLineA());
    STARTUPINFOA si; PROCESS_INFORMATION pi;
    memset(&si, 0, sizeof si); si.cb = sizeof si;
    if (CreateProcessA(path, cmd, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
        CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
        ExitProcess(0);
    }
#else
    execv("/proc/self/exe", g_argv);
#endif
    printf("Restart failed; continuing to retry.\n");
}

/* Reads from the opened device until it is lost or goes quiet for too long. Returns 1 if the device was lost. */
static int serve_device(void) {
    cm_decoder dec; cm_decoder_init(&dec);
    int connected = 0, idle = 0;
    for (;;) {
        report_stream_if_finished();
        uint8_t buf[256];
        int n = backend_read_midi(buf, sizeof buf, POLL_MS);
        if (n < 0) return 1;
        if (n == 0) {
            idle += POLL_MS;
            if (connected && idle >= HEARTBEAT_TIMEOUT_MS) {
                printf("disconnected (no heartbeat)\n"); release_all(); connected = 0;
            }
            /* Silent for a long time: the handle may be stale after a replug, so reopen it. */
            if (idle >= REOPEN_IDLE_MS) { release_all(); return 0; }
            continue;
        }
        motion m = {0, 0, 0};
        for (int i = 0; i < n; i++) {
            cm_event ev;
            if (!cm_decoder_feed(&dec, buf[i], &ev)) continue;
            idle = 0;
            if (!connected) { connected = 1; printf("connected\n"); }
            apply_event(&ev, &m);
        }
        if (m.dx || m.dy) backend_move(m.dx, m.dy);
        if (m.scroll) backend_scroll(m.scroll);
    }
}

int main(int argc, char **argv) {
    g_argv = argv;
    setvbuf(stdout, NULL, _IONBF, 0);                         /* status lines show up at once, also through a pipe */
    const char *hint = NULL;                                  /* part of the MIDI device name, or a device path */
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--stream-check") == 0) stream_check_mode = 1;
        else if (!hint) hint = argv[i];
    }
    printf("Cable Mouse helper (protocol v%d). Switch the phone's USB mode to MIDI.\n", CM_PROTOCOL_VERSION);
    if (stream_check_mode) printf("Stream check mode: on the phone open Settings > Diagnostics > Stream test. No mouse or keyboard input is injected.\n");

    int had_open = 0, fail_ms = 0, quiet = 0;
    for (;;) {
        while (backend_open(hint) != 0) {
            SLEEP_MS(1000);
            fail_ms += 1000;
            /* The device worked before and has now been missing/unopenable for a while. */
            if (had_open && fail_ms >= REEXEC_AFTER_MS) { restart_self(); fail_ms = 0; }
        }
        fail_ms = 0; had_open = 1;
        if (!quiet) printf("MIDI device opened; waiting for phone...\n");
        int lost = serve_device();
        quiet = !lost;                                        /* a silent reopen is not announced again */
        if (lost) printf("MIDI device lost; reconnecting...\n");
        release_all(); backend_close();
    }
}
