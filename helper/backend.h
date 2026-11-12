#ifndef CM_BACKEND_H
#define CM_BACKEND_H
#include <stdint.h>

/* Platform back end. Returns 0 on success, -1 on failure (message printed). */
int  backend_open(const char *device_hint);
/* Waits up to timeout_ms; returns bytes read (>0), 0 on timeout, -1 if device lost. */
int  backend_read_midi(uint8_t *buf, int len, int timeout_ms);
void backend_move(int dx, int dy);
void backend_button(int button, int down);   /* 0 left, 1 right, 2 middle */
void backend_scroll(int clicks);             /* positive = up */
void backend_close(void);
#endif
