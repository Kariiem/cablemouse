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
#include "../common/protocol.h"

#ifndef MOUSEEVENTF_ABSOLUTE
#define MOUSEEVENTF_ABSOLUTE 0x8000
#endif
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

/* Absolute position as a fraction of the primary monitor (no VIRTUALDESK flag, so other monitors are not used). */
void backend_move_abs(int x, int y) {
    mouse_send(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE, 0, (LONG)((long long)x * 65535 / CM_ABS_MAX), (LONG)((long long)y * 65535 / CM_ABS_MAX));
}

void backend_move(int dx, int dy) { mouse_send(MOUSEEVENTF_MOVE, 0, dx, dy); }
void backend_button(int b, int down) {
    static const DWORD dn[] = {MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_MIDDLEDOWN};
    static const DWORD up[] = {MOUSEEVENTF_LEFTUP, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_MIDDLEUP};
    if (b >= 0 && b < 3) mouse_send(down ? dn[b] : up[b], 0, 0, 0);
}
void backend_scroll(int clicks) { mouse_send(MOUSEEVENTF_WHEEL, (DWORD)(clicks * WHEEL_DELTA), 0, 0); }

/* Text goes in as KEYEVENTF_UNICODE events (layout independent); characters above U+FFFF as a surrogate pair. */
static void unicode_unit(WORD unit) {
    INPUT in[2]; memset(in, 0, sizeof in);
    for (int i = 0; i < 2; i++) {
        in[i].type = INPUT_KEYBOARD;
        in[i].ki.wScan = unit;
        in[i].ki.dwFlags = KEYEVENTF_UNICODE | (i ? KEYEVENTF_KEYUP : 0);
    }
    SendInput(2, in, sizeof(INPUT));
}

void backend_key_char(uint32_t cp) {
    if (cp >= 0x10000) {
        cp -= 0x10000;
        unicode_unit((WORD)(0xD800 + (cp >> 10)));
        unicode_unit((WORD)(0xDC00 + (cp & 0x3FF)));
    } else {
        unicode_unit((WORD)cp);
    }
}

static void vk_event(WORD vk, int ext, int up) {
    INPUT in; memset(&in, 0, sizeof in);
    in.type = INPUT_KEYBOARD;
    in.ki.wVk = vk;
    in.ki.wScan = (WORD)MapVirtualKeyA(vk, MAPVK_VK_TO_VSC);
    in.ki.dwFlags = (ext ? KEYEVENTF_EXTENDEDKEY : 0) | (up ? KEYEVENTF_KEYUP : 0);
    SendInput(1, &in, sizeof in);
}

/* Navigation keys must be flagged extended, otherwise they act as their numeric-keypad twins. */
static int vk_is_extended(WORD vk) {
    return vk == VK_INSERT || vk == VK_DELETE || vk == VK_HOME || vk == VK_END || vk == VK_PRIOR || vk == VK_NEXT ||
           vk == VK_LEFT || vk == VK_RIGHT || vk == VK_UP || vk == VK_DOWN || vk == VK_LWIN;
}

static void vk_tap(WORD vk) { int e = vk_is_extended(vk); vk_event(vk, e, 0); vk_event(vk, e, 1); }

void backend_key(int key) {
    static const WORD vk[] = {0, VK_BACK, VK_RETURN, VK_TAB, VK_ESCAPE, VK_DELETE, VK_LEFT, VK_RIGHT,
                              VK_UP, VK_DOWN, VK_HOME, VK_END, VK_PRIOR, VK_NEXT};
    if (key >= 1 && key <= CM_KEY_LAST) vk_tap(vk[key]);
}

/* USB HID keyboard usage -> Windows virtual key (0 = unsupported). Letters are layout independent; the OEM
 * keys produce whatever the PC's layout puts on that physical position. */
static WORD hid_to_vk(int u) {
    if (u >= 0x04 && u <= 0x1D) return (WORD)('A' + (u - 0x04));
    if (u >= 0x1E && u <= 0x26) return (WORD)('1' + (u - 0x1E));
    if (u >= 0x3A && u <= 0x45) return (WORD)(VK_F1 + (u - 0x3A));
    if (u >= 0x68 && u <= 0x73) return (WORD)(VK_F13 + (u - 0x68));
    switch (u) {
    case 0x27: return '0';
    case 0x28: return VK_RETURN;   case 0x29: return VK_ESCAPE;  case 0x2A: return VK_BACK;
    case 0x2B: return VK_TAB;      case 0x2C: return VK_SPACE;   case 0x2D: return VK_OEM_MINUS;
    case 0x2E: return VK_OEM_PLUS; case 0x2F: return VK_OEM_4;   case 0x30: return VK_OEM_6;
    case 0x31: return VK_OEM_5;    case 0x33: return VK_OEM_1;   case 0x34: return VK_OEM_7;
    case 0x35: return VK_OEM_3;    case 0x36: return VK_OEM_COMMA; case 0x37: return VK_OEM_PERIOD;
    case 0x38: return VK_OEM_2;    case 0x39: return VK_CAPITAL; case 0x46: return VK_SNAPSHOT;
    case 0x47: return VK_SCROLL;   case 0x48: return VK_PAUSE;   case 0x49: return VK_INSERT;
    case 0x4A: return VK_HOME;     case 0x4B: return VK_PRIOR;   case 0x4C: return VK_DELETE;
    case 0x4D: return VK_END;      case 0x4E: return VK_NEXT;    case 0x4F: return VK_RIGHT;
    case 0x50: return VK_LEFT;     case 0x51: return VK_DOWN;    case 0x52: return VK_UP;
    }
    return 0;
}

void backend_key_hid(int usage) { WORD vk = hid_to_vk(usage); if (vk) vk_tap(vk); }

void backend_mods(int mask) {
    static int cur;
    static const struct { int bit; WORD vk; } m[] = {
        {CM_MOD_CTRL, VK_LCONTROL}, {CM_MOD_SHIFT, VK_LSHIFT}, {CM_MOD_ALT, VK_LMENU}, {CM_MOD_WIN, VK_LWIN}};
    for (int i = 0; i < 4; i++) {
        int want = (mask & m[i].bit) != 0, have = (cur & m[i].bit) != 0;
        if (want != have) vk_event(m[i].vk, vk_is_extended(m[i].vk), !want);
    }
    cur = mask & CM_MOD_ALL;
}

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
