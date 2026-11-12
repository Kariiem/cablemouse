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

static int fd = -1;
static void *dpy;
static void *(*XOpenDisplay_)(const char *);
static int (*XFlush_)(void *);
static int (*XTestFakeRelativeMotionEvent_)(void *, int, int, unsigned long);
static int (*XTestFakeButtonEvent_)(void *, unsigned, int, unsigned long);

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
void backend_close(void) { if (fd >= 0) close(fd); fd = -1; }
