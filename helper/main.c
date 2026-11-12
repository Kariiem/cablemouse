#include <stdio.h>
#include <string.h>
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
static char **g_argv;

static void release_all(void) {
    for (int i = 0; i < 3; i++) if (held[i]) { backend_button(i, 0); held[i] = 0; }
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
    (void)argc; g_argv = argv;
    const char *hint = argc > 1 ? argv[1] : NULL;
    printf("Cable Mouse helper (protocol v%d). Switch the phone's USB mode to MIDI.\n", CM_PROTOCOL_VERSION);
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
                case EV_MOVE_X: dx += ev.value; break;
                case EV_MOVE_Y: dy += ev.value; break;
                case EV_SCROLL: sc += ev.value; break;
                case EV_BTN_DOWN: backend_button(ev.value, 1); held[ev.value] = 1; break;
                case EV_BTN_UP:   backend_button(ev.value, 0); held[ev.value] = 0; break;
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
