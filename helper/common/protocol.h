#ifndef CM_PROTOCOL_H
#define CM_PROTOCOL_H
#include <stdint.h>

#define CM_PROTOCOL_VERSION 1

typedef enum { EV_MOVE_X, EV_MOVE_Y, EV_SCROLL, EV_BTN_DOWN, EV_BTN_UP, EV_HEARTBEAT,
               EV_KEY_CHAR, EV_KEY_SPECIAL, EV_MODS, EV_KEY_HID, EV_MOVE_ABS } cm_ev_type;

/* EV_MOVE_ABS value: (x << 14) | y with x, y in 0..CM_ABS_MAX, fractions of the primary screen. */
#define CM_ABS_MAX 16383

/* EV_MODS value: bit mask of held modifiers. EV_KEY_HID value: USB HID keyboard usage (0x04..0x73), tapped
 * with the modifiers currently held. */
enum { CM_MOD_CTRL = 1, CM_MOD_SHIFT = 2, CM_MOD_ALT = 4, CM_MOD_WIN = 8, CM_MOD_ALL = 15 };
#define CM_HID_MIN 0x04
#define CM_HID_MAX 0x73

/* EV_KEY_CHAR value: a Unicode code point. EV_KEY_SPECIAL value: one of CM_KEY_*. */
enum { CM_KEY_BACKSPACE = 1, CM_KEY_ENTER, CM_KEY_TAB, CM_KEY_ESCAPE, CM_KEY_DELETE, CM_KEY_LEFT, CM_KEY_RIGHT,
       CM_KEY_UP, CM_KEY_DOWN, CM_KEY_HOME, CM_KEY_END, CM_KEY_PAGE_UP, CM_KEY_PAGE_DOWN, CM_KEY_LAST = CM_KEY_PAGE_DOWN };

typedef struct { cm_ev_type type; int value; } cm_event;

/* Streaming MIDI decoder: feed raw bytes one at a time (handles running status,
 * ignores realtime bytes and SysEx). Pure, no I/O. */
typedef struct { uint8_t status; uint8_t d1; int have; uint32_t cp; uint32_t ax, ay; } cm_decoder;

void cm_decoder_init(cm_decoder *d);
/* Returns 1 and fills *ev if this byte completed an event, else 0. */
int cm_decoder_feed(cm_decoder *d, uint8_t byte, cm_event *ev);

#endif
