/* Linux back end: raw MIDI from /dev/snd/midiC*D0 (no libasound), input via XTest (dlopen). */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "../backend.h"
#include "../common/protocol.h"

static int fd = -1;
static void *dpy;
static void *(*XOpenDisplay_)(const char *);
static int (*XFlush_)(void *);
static int (*XTestFakeRelativeMotionEvent_)(void *, int, int, unsigned long);
static int (*XTestFakeButtonEvent_)(void *, unsigned, int, unsigned long);

static int (*XTestFakeKeyEvent_)(void *, unsigned, int, unsigned long);
static int (*XDisplayKeycodes_)(void *, int *, int *);
static unsigned long *(*XGetKeyboardMapping_)(void *, unsigned char, int, int *);
static int (*XChangeKeyboardMapping_)(void *, int, int, unsigned long *, int);
static int (*XSync_)(void *, int);
static int (*XFree_)(void *);

static unsigned (*XKeysymToKeycode_)(void *, unsigned long);
static int (*XTestFakeMotionEvent_)(void *, int, int, int, unsigned long);
static int (*XDefaultScreen_)(void *);
static int (*XDisplayWidth_)(void *, int);
static int (*XDisplayHeight_)(void *, int);

static int find_device(const char *hint, char *out, size_t n) {
    if (hint && hint[0] == '/') { snprintf(out, n, "%s", hint); return 0; }
    const char *want = hint ? hint : "Android";
    FILE *f = fopen("/proc/asound/cards", "r");
    if (!f) return -1;
    char line[256]; int card = -1;
    while (fgets(line, sizeof line, f)) {
        int c;
        if (sscanf(line, " %d [", &c) == 1 && strstr(line, want)) { card = c; break; }
    }
    fclose(f);
    if (card < 0) return -1;
    snprintf(out, n, "/dev/snd/midiC%dD0", card);
    return 0;
}

static int init_x(void) {
    if (dpy) return 0;
    void *x11 = dlopen("libX11.so.6", RTLD_NOW), *xt = dlopen("libXtst.so.6", RTLD_NOW);
    if (!x11 || !xt) { fprintf(stderr, "libX11/libXtst not found; X11 session required (Wayland unsupported).\n"); return -1; }
    XOpenDisplay_ = dlsym(x11, "XOpenDisplay"); XFlush_ = dlsym(x11, "XFlush");
    XTestFakeRelativeMotionEvent_ = dlsym(xt, "XTestFakeRelativeMotionEvent");
    XTestFakeButtonEvent_ = dlsym(xt, "XTestFakeButtonEvent");
    XTestFakeKeyEvent_ = dlsym(xt, "XTestFakeKeyEvent");
    XDisplayKeycodes_ = dlsym(x11, "XDisplayKeycodes"); XGetKeyboardMapping_ = dlsym(x11, "XGetKeyboardMapping");
    XChangeKeyboardMapping_ = dlsym(x11, "XChangeKeyboardMapping"); XSync_ = dlsym(x11, "XSync");
    XFree_ = dlsym(x11, "XFree"); XKeysymToKeycode_ = dlsym(x11, "XKeysymToKeycode");
    XTestFakeMotionEvent_ = dlsym(xt, "XTestFakeMotionEvent"); XDefaultScreen_ = dlsym(x11, "XDefaultScreen");
    XDisplayWidth_ = dlsym(x11, "XDisplayWidth"); XDisplayHeight_ = dlsym(x11, "XDisplayHeight");
    dpy = XOpenDisplay_ ? XOpenDisplay_(NULL) : NULL;
    if (!dpy) { fprintf(stderr, "Cannot open X display (Wayland sessions need uinput; not supported yet).\n"); return -1; }
    return 0;
}

int backend_open(const char *hint) {
    char path[128];
    if (init_x() != 0) return -1;
    if (find_device(hint, path, sizeof path) != 0) return -1;
    fd = open(path, O_RDONLY | O_NONBLOCK);
    return fd < 0 ? -1 : 0;
}

int backend_read_midi(uint8_t *buf, int len, int timeout_ms) {
    struct pollfd p = {fd, POLLIN, 0};
    int r = poll(&p, 1, timeout_ms);
    if (r == 0) return 0;
    if (r < 0 || (p.revents & (POLLERR | POLLHUP | POLLNVAL))) return -1;
    int n = read(fd, buf, len);
    return n < 0 ? -1 : n;
}

void backend_move(int dx, int dy) { XTestFakeRelativeMotionEvent_(dpy, dx, dy, 0); XFlush_(dpy); }
void backend_button(int b, int down) {
    static const unsigned map[] = {1, 3, 2};   /* X: 1 left, 2 middle, 3 right */
    if (b >= 0 && b < 3) { XTestFakeButtonEvent_(dpy, map[b], down, 0); XFlush_(dpy); }
}
void backend_scroll(int clicks) {
    unsigned b = clicks > 0 ? 4 : 5;
    for (int i = 0, n = abs(clicks); i < n; i++) { XTestFakeButtonEvent_(dpy, b, 1, 0); XTestFakeButtonEvent_(dpy, b, 0, 0); }
    XFlush_(dpy);
}
/* Types one keysym on X11 the way xdotool does: bind it to a spare keycode, tap that keycode, restore.
 * This works for any character regardless of the keyboard layout and needs no modifier handling. */
static void tap_keysym(unsigned long ks) {
    if (!XTestFakeKeyEvent_ || !XDisplayKeycodes_ || !XGetKeyboardMapping_ || !XChangeKeyboardMapping_ || !XSync_) return;
    int lo, hi, per;
    XDisplayKeycodes_(dpy, &lo, &hi);
    unsigned long *map = XGetKeyboardMapping_(dpy, (unsigned char)lo, hi - lo + 1, &per);
    if (!map) return;
    int spare = hi;                                   /* fallback: the highest keycode */
    for (int k = hi; k >= lo; k--) {
        int used = 0;
        for (int j = 0; j < per; j++) if (map[(k - lo) * per + j]) { used = 1; break; }
        if (!used) { spare = k; break; }
    }
    if (XFree_) XFree_(map);
    unsigned long row[8] = {ks, ks, ks, ks, ks, ks, ks, ks};
    XChangeKeyboardMapping_(dpy, spare, 1, row, 1);
    XSync_(dpy, 0);
    XTestFakeKeyEvent_(dpy, (unsigned)spare, 1, 0);
    XTestFakeKeyEvent_(dpy, (unsigned)spare, 0, 0);
    XSync_(dpy, 0);
    unsigned long none[8] = {0};
    XChangeKeyboardMapping_(dpy, spare, 1, none, 1);
    XSync_(dpy, 0);
}

void backend_key_char(uint32_t cp) {
    /* Latin-1 code points are their own keysyms; everything else is 0x01000000 + code point. */
    tap_keysym(((cp >= 0x20 && cp < 0x7F) || (cp >= 0xA0 && cp <= 0xFF)) ? cp : 0x01000000UL + cp);
}

void backend_key(int key) {
    static const unsigned long ks[] = {0, 0xFF08, 0xFF0D, 0xFF09, 0xFF1B, 0xFFFF, 0xFF51, 0xFF53,
                                       0xFF52, 0xFF54, 0xFF50, 0xFF57, 0xFF55, 0xFF56};
    if (key >= 1 && key <= CM_KEY_LAST) tap_keysym(ks[key]);
}

/* USB HID keyboard usage -> unshifted X keysym (0 = unsupported). */
static unsigned long hid_to_keysym(int u) {
    if (u >= 0x04 && u <= 0x1D) return 'a' + (u - 0x04);
    if (u >= 0x1E && u <= 0x26) return '1' + (u - 0x1E);
    if (u >= 0x3A && u <= 0x45) return 0xFFBE + (u - 0x3A);        /* F1..F12 */
    if (u >= 0x68 && u <= 0x73) return 0xFFCA + (u - 0x68);        /* F13..F24 */
    switch (u) {
    case 0x27: return '0';      case 0x28: return 0xFF0D;   case 0x29: return 0xFF1B;  case 0x2A: return 0xFF08;
    case 0x2B: return 0xFF09;   case 0x2C: return ' ';      case 0x2D: return '-';     case 0x2E: return '=';
    case 0x2F: return '[';      case 0x30: return ']';      case 0x31: return '\\';    case 0x33: return ';';
    case 0x34: return '\'';     case 0x35: return '`';      case 0x36: return ',';     case 0x37: return '.';
    case 0x38: return '/';      case 0x39: return 0xFFE5;   case 0x46: return 0xFF61;  case 0x47: return 0xFF14;
    case 0x48: return 0xFF13;   case 0x49: return 0xFF63;   case 0x4A: return 0xFF50;  case 0x4B: return 0xFF55;
    case 0x4C: return 0xFFFF;   case 0x4D: return 0xFF57;   case 0x4E: return 0xFF56;  case 0x4F: return 0xFF53;
    case 0x50: return 0xFF51;   case 0x51: return 0xFF54;   case 0x52: return 0xFF52;
    }
    return 0;
}

/* Physical-key style: use the keyboard's real keycodes so modifiers and shift levels behave normally. */
void backend_key_hid(int usage) {
    unsigned long ks = hid_to_keysym(usage);
    unsigned kc = ks && XKeysymToKeycode_ ? XKeysymToKeycode_(dpy, ks) : 0;
    if (!kc) return;
    XTestFakeKeyEvent_(dpy, kc, 1, 0); XTestFakeKeyEvent_(dpy, kc, 0, 0); XFlush_(dpy);
}

void backend_mods(int mask) {
    static int cur;
    static const struct { int bit; unsigned long ks; } m[] = {
        {CM_MOD_CTRL, 0xFFE3}, {CM_MOD_SHIFT, 0xFFE1}, {CM_MOD_ALT, 0xFFE9}, {CM_MOD_WIN, 0xFFEB}};
    for (int i = 0; i < 4; i++) {
        int want = (mask & m[i].bit) != 0, have = (cur & m[i].bit) != 0;
        unsigned kc = XKeysymToKeycode_ ? XKeysymToKeycode_(dpy, m[i].ks) : 0;
        if (want != have && kc) XTestFakeKeyEvent_(dpy, kc, want, 0);
    }
    cur = mask & CM_MOD_ALL;
    XFlush_(dpy);
}

/* Absolute position on the default X screen (the whole X screen, which is the primary display on a single-monitor setup). */
void backend_move_abs(int x, int y) {
    if (!XTestFakeMotionEvent_ || !XDefaultScreen_ || !XDisplayWidth_ || !XDisplayHeight_) return;
    int scr = XDefaultScreen_(dpy), w = XDisplayWidth_(dpy, scr), h = XDisplayHeight_(dpy, scr);
    XTestFakeMotionEvent_(dpy, scr, (int)((long long)x * (w - 1) / CM_ABS_MAX), (int)((long long)y * (h - 1) / CM_ABS_MAX), 0);
    XFlush_(dpy);
}

void backend_close(void) { if (fd >= 0) close(fd); fd = -1; }
