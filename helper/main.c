#include <stdio.h>
#include <string.h>
#include <time.h>
#include "backend.h"
#include "common/protocol.h"

#define HEARTBEAT_TIMEOUT_MS 2000
#define POLL_MS 100
#define REOPEN_IDLE_MS 6000     /* no data this long -> close and reopen the device */
#define REEXEC_AFTER_MS 10000   /* device unavailable this long after working once -> restart process */

#ifdef _WIN32
#include <windows.h>
#define SLEEP_MS(x) Sleep(x)
#else
#include <unistd.h>
#define SLEEP_MS(x) usleep((x) * 1000)
#endif

static int held[3];
static int mods_held;

/* --stream-check: verify the phone's "Stream test" (5000 move_x messages, 2 ms apart, value cycling 1..127)
 * instead of moving the cursor. Counts missing and repeated messages and the longest gap between messages. */
static int stream_check;
static struct { long n, missing, repeats, slow; int prev; double first, last, maxgap; } sc;

static double now_ms(void) {
#ifdef _WIN32
    LARGE_INTEGER f, c; QueryPerformanceFrequency(&f); QueryPerformanceCounter(&c);
    return 1000.0 * (double)c.QuadPart / (double)f.QuadPart;
#else
    struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
    return 1000.0 * t.tv_sec + t.tv_nsec / 1e6;
#endif
}

static void stream_feed(int value) {
    int b = value + 64;                     /* the phone sent b = 1 + n % 127 */
    double t = now_ms();
    if (sc.n) {
        int want = sc.prev % 127 + 1;
        if (b == sc.prev) sc.repeats++;
        else if (b != want) sc.missing += (b - want + 127) % 127;
        double gap = t - sc.last;
        if (gap > sc.maxgap) sc.maxgap = gap;
        if (gap > 20) sc.slow++;
    } else sc.first = t;
    sc.prev = b; sc.last = t; sc.n++;
}

/* Called regularly: once no message has arrived for a second, the stream is over, so print the result. */
static void stream_poll(void) {
    if (!stream_check || !sc.n || now_ms() - sc.last < 1000) return;
    double secs = (sc.last - sc.first) / 1000.0;
    printf("stream: received %ld messages in %.2f s (%.0f/s); missing %ld, repeated %ld; longest gap %.1f ms, gaps over 20 ms: %ld\n",
           sc.n, secs, secs > 0 ? (sc.n - 1) / secs : 0.0, sc.missing, sc.repeats, sc.maxgap, sc.slow);
    printf("stream: %s\n", sc.missing == 0 && sc.repeats == 0 ? "PASS: nothing dropped" : "FAIL: messages were lost or repeated");
    fflush(stdout);
    memset(&sc, 0, sizeof sc);
}
static char **g_argv;

static void release_all(void) {
    for (int i = 0; i < 3; i++) if (held[i]) { backend_button(i, 0); held[i] = 0; }
    if (mods_held) { backend_mods(0); mods_held = 0; }
}

/* Last-resort recovery: winmm can keep a stale device list/handle until the process restarts,
 * so after the device has been gone for a while we simply start a fresh copy of ourselves. */
static void restart_self(void) {
    printf("Restarting helper to refresh the MIDI device list...\n"); fflush(stdout);
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

int main(int argc, char **argv) {
    g_argv = argv;
    const char *hint = NULL;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--stream-check") == 0) stream_check = 1;
        else if (!hint) hint = argv[i];
    }
    printf("Cable Mouse helper (protocol v%d). Switch the phone's USB mode to MIDI.\n", CM_PROTOCOL_VERSION);
    if (stream_check) printf("Stream check mode: on the phone open Settings > Diagnostics > Stream test. The mouse is not moved.\n");
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
        cm_decoder dec; cm_decoder_init(&dec);
        int connected = 0, idle = 0, lost = 0;
        for (;;) {
            stream_poll();
            uint8_t buf[256];
            int n = backend_read_midi(buf, sizeof buf, POLL_MS);
            if (n < 0) { lost = 1; break; }
            if (n == 0) {
                idle += POLL_MS;
                if (connected && idle >= HEARTBEAT_TIMEOUT_MS) {
                    printf("disconnected (no heartbeat)\n"); release_all(); connected = 0;
                }
                /* Silent for a long time: the handle may be stale after a replug, so reopen it. */
                if (idle >= REOPEN_IDLE_MS) { release_all(); quiet = 1; break; }
                continue;
            }
            quiet = 0;
            int dx = 0, dy = 0, sc = 0;
            for (int i = 0; i < n; i++) {
                cm_event ev;
                if (!cm_decoder_feed(&dec, buf[i], &ev)) continue;
                idle = 0;
                if (!connected) { connected = 1; printf("connected\n"); }
                switch (ev.type) {
                case EV_MOVE_X: if (stream_check) stream_feed(ev.value); else dx += ev.value; break;
                case EV_MOVE_Y: dy += ev.value; break;
                case EV_SCROLL: sc += ev.value; break;
                case EV_BTN_DOWN: backend_button(ev.value, 1); held[ev.value] = 1; break;
                case EV_BTN_UP:   backend_button(ev.value, 0); held[ev.value] = 0; break;
                case EV_KEY_CHAR: backend_key_char((uint32_t)ev.value); break;
                case EV_KEY_SPECIAL: backend_key(ev.value); break;
                case EV_MODS: backend_mods(ev.value); mods_held = ev.value; break;
                case EV_KEY_HID: backend_key_hid(ev.value); break;
                case EV_MOVE_ABS: backend_move_abs(ev.value >> 14, ev.value & CM_ABS_MAX); break;
                case EV_HEARTBEAT:
                    if (ev.value != CM_PROTOCOL_VERSION)
                        printf("warning: phone protocol v%d, helper v%d\n", ev.value, CM_PROTOCOL_VERSION);
                    break;
                }
            }
            if (dx || dy) backend_move(dx, dy);
            if (sc) backend_scroll(sc);
        }
        if (lost) printf("MIDI device lost; reconnecting...\n");
        release_all(); backend_close();
    }
}
