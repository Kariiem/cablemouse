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
void backend_key_char(uint32_t code_point);  /* type one Unicode character */
void backend_key(int key);                   /* press + release a special key (CM_KEY_* in common/protocol.h) */
void backend_mods(int mask);                 /* hold exactly these modifiers (CM_MOD_*), releasing the others */
void backend_key_hid(int usage);             /* press + release the key with this USB HID usage */
void backend_move_abs(int x, int y);         /* x, y in 0..CM_ABS_MAX: a fraction of the primary screen */
void backend_close(void);
#endif
