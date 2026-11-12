/* Windows back end: winmm MIDI input + SendInput. Used by the APE and the legacy exe.
 *
 * Hardening notes (unplugging the phone):
 *  - winmm often does NOT report an unplug (no MIM_CLOSE), and the old handle just goes silent forever.
 *    So backend_read_midi() also polls the device list once a second and reports the device as lost
 *    when it disappears.
 *  - midiInClose()/midiInReset() on a dead device can hang, so they run on a helper thread with a timeout.
 *  - The input ring buffer cannot overflow into garbage.
 */
#include <windows.h>
#include <mmsystem.h>
#include <stdio.h>
#include <string.h>
#include "../backend.h"

#ifndef MOUSEEVENTF_WHEEL
#define MOUSEEVENTF_WHEEL 0x0800
#endif

#define RING 4096
#define PRESENCE_CHECK_MS 1000
#define CLOSE_TIMEOUT_MS 2000

static uint8_t ring[RING];
static volatile unsigned head, tail;
static CRITICAL_SECTION lock;
static HANDLE evt;
static HMIDIIN hin;
static volatile int lost;
static int inited;
static char opened_name[MAXPNAMELEN];
static DWORD last_check;

static void push(uint8_t b) {
    if (head - tail >= RING) tail++;           /* overflow: drop the oldest byte */
    ring[head % RING] = b;
    head++;
}

static void CALLBACK midi_cb(HMIDIIN h, UINT msg, DWORD_PTR inst, DWORD_PTR p1, DWORD_PTR p2) {
    (void)h; (void)inst; (void)p2;
    if (msg == MIM_DATA) {
        uint8_t st = p1 & 0xFF;
        int len = ((st & 0xF0) == 0xC0 || (st & 0xF0) == 0xD0) ? 2 : 3;
        EnterCriticalSection(&lock);
        for (int i = 0; i < len; i++) push((p1 >> (8 * i)) & 0xFF);
        LeaveCriticalSection(&lock);
        SetEvent(evt);
    } else if (msg == MIM_CLOSE || msg == MIM_ERROR) {
        lost = 1; SetEvent(evt);
    }
}

static char last_msg[256];
static void note(const char *fmt, const char *a, unsigned b) {
    char m[256];
    snprintf(m, sizeof m, fmt, a, b);
    if (strcmp(m, last_msg)) { printf("%s\n", m); fflush(stdout); strcpy(last_msg, m); }
}

int backend_open(const char *hint) {
    if (!inited) { InitializeCriticalSection(&lock); evt = CreateEvent(NULL, FALSE, FALSE, NULL); inited = 1; }
    UINT n = midiInGetNumDevs(), pick = (UINT)-1;
    if (n == 0) { note("No MIDI input devices found. Is the phone in MIDI mode?%s%u", "", 0); return -1; }
    /* Prefer: user-given substring, then "Android", then the only device if there is exactly one. */
    const char *wants[2] = { hint, "Android" };
    MIDIINCAPSA caps;
    for (int w = 0; w < 2 && pick == (UINT)-1; w++) {
        if (!wants[w]) continue;
        for (UINT i = 0; i < n; i++)
            if (midiInGetDevCapsA(i, &caps, sizeof caps) == MMSYSERR_NOERROR && strstr(caps.szPname, wants[w])) { pick = i; break; }
    }
    if (pick == (UINT)-1 && n == 1 && !hint) pick = 0;
    if (pick == (UINT)-1) {
        char list[200] = "";
        for (UINT i = 0; i < n; i++)
            if (midiInGetDevCapsA(i, &caps, sizeof caps) == MMSYSERR_NOERROR) {
                strncat(list, "[", sizeof list - strlen(list) - 1);
                strncat(list, caps.szPname, sizeof list - strlen(list) - 1);
                strncat(list, "] ", sizeof list - strlen(list) - 1);
            }
        note("MIDI inputs found: %s- none matched. Run: cablemouse-legacy.exe \"<part of name>\"%u", list, 0);
        return -1;
    }
    midiInGetDevCapsA(pick, &caps, sizeof caps);
    lost = 0; head = tail = 0; last_check = GetTickCount();
    MMRESULT r = midiInOpen(&hin, pick, (DWORD_PTR)midi_cb, 0, CALLBACK_FUNCTION);
    if (r != MMSYSERR_NOERROR) {
        hin = NULL;
        note("midiInOpen failed (code %u%s). 4 = device busy: close other MIDI programs (monitors, DAWs).", "", (unsigned)r);
        return -1;
    }
    snprintf(opened_name, sizeof opened_name, "%s", caps.szPname);
    midiInStart(hin);
    return 0;
}

static int device_present(void) {
    UINT n = midiInGetNumDevs();
    for (UINT i = 0; i < n; i++) {
        MIDIINCAPSA caps;
        if (midiInGetDevCapsA(i, &caps, sizeof caps) == MMSYSERR_NOERROR && strcmp(caps.szPname, opened_name) == 0) return 1;
    }
    return 0;
}

int backend_read_midi(uint8_t *buf, int len, int timeout_ms) {
    if (lost || !hin) return -1;
    if (head == tail) WaitForSingleObject(evt, timeout_ms);
    if (lost) return -1;
    int n = 0;
    EnterCriticalSection(&lock);
    while (n < len && tail != head) buf[n++] = ring[tail++ % RING];
    LeaveCriticalSection(&lock);
    if (n == 0 && GetTickCount() - last_check >= PRESENCE_CHECK_MS) {
        last_check = GetTickCount();
        if (!device_present()) { lost = 1; return -1; }
    }
    return n;
}

static void mouse_send(DWORD flags, DWORD data, LONG dx, LONG dy) {
    INPUT in; memset(&in, 0, sizeof in);
    in.type = INPUT_MOUSE; in.mi.dwFlags = flags; in.mi.mouseData = data; in.mi.dx = dx; in.mi.dy = dy;
    SendInput(1, &in, sizeof in);
}

void backend_move(int dx, int dy) { mouse_send(MOUSEEVENTF_MOVE, 0, dx, dy); }
void backend_button(int b, int down) {
    static const DWORD dn[] = {MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_MIDDLEDOWN};
    static const DWORD up[] = {MOUSEEVENTF_LEFTUP, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_MIDDLEUP};
    if (b >= 0 && b < 3) mouse_send(down ? dn[b] : up[b], 0, 0, 0);
}
void backend_scroll(int clicks) { mouse_send(MOUSEEVENTF_WHEEL, (DWORD)(clicks * WHEEL_DELTA), 0, 0); }

static DWORD WINAPI close_thread(LPVOID p) {
    HMIDIIN h = (HMIDIIN)p;
    midiInStop(h); midiInReset(h); midiInClose(h);
    return 0;
}

void backend_close(void) {
    if (!hin) return;
    HMIDIIN h = hin; hin = NULL;
    HANDLE t = CreateThread(NULL, 0, close_thread, h, 0, NULL);
    if (t) {
        if (WaitForSingleObject(t, CLOSE_TIMEOUT_MS) == WAIT_TIMEOUT) printf("(closing the old MIDI handle hung; abandoning it)\n");
        CloseHandle(t);
    }
}
