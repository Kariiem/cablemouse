#ifndef CM_PROTOCOL_H
#define CM_PROTOCOL_H
#include <stdint.h>

#define CM_PROTOCOL_VERSION 1

typedef enum { EV_MOVE_X, EV_MOVE_Y, EV_SCROLL, EV_BTN_DOWN, EV_BTN_UP, EV_HEARTBEAT } cm_ev_type;

typedef struct { cm_ev_type type; int value; } cm_event;

/* Streaming MIDI decoder: feed raw bytes one at a time (handles running status,
 * ignores realtime bytes and SysEx). Pure, no I/O. */
typedef struct { uint8_t status; uint8_t d1; int have; } cm_decoder;

void cm_decoder_init(cm_decoder *d);
/* Returns 1 and fills *ev if this byte completed an event, else 0. */
int cm_decoder_feed(cm_decoder *d, uint8_t byte, cm_event *ev);

#endif
