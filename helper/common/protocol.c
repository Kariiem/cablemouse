#include "protocol.h"

void cm_decoder_init(cm_decoder *d) { d->status = 0; d->d1 = 0; d->have = 0; }

int cm_decoder_feed(cm_decoder *d, uint8_t b, cm_event *ev) {
    if (b >= 0xF8) return 0;                 /* realtime: may appear anywhere */
    if (b & 0x80) {
        d->have = 0;
        d->status = (b >= 0xF0) ? 0 : b;     /* system messages/SysEx: ignore */
        return 0;
    }
    if (!d->status) return 0;
    if (!d->have) { d->d1 = b; d->have = 1; return 0; }
    d->have = 0;
    if ((d->status & 0x0F) != 0) return 0;   /* only MIDI channel 1 */
    switch (d->status & 0xF0) {
    case 0xB0:
        switch (d->d1) {
        case 0x01: ev->type = EV_MOVE_X; ev->value = (int)b - 64; return 1;
        case 0x02: ev->type = EV_MOVE_Y; ev->value = (int)b - 64; return 1;
        case 0x03: ev->type = EV_SCROLL; ev->value = (int)b - 64; return 1;
        case 0x7F: ev->type = EV_HEARTBEAT; ev->value = b; return 1;
        }
        return 0;
    case 0x90:
        if (d->d1 > 2) return 0;
        ev->type = b ? EV_BTN_DOWN : EV_BTN_UP; ev->value = d->d1; return 1;
    case 0x80:
        if (d->d1 > 2) return 0;
        ev->type = EV_BTN_UP; ev->value = d->d1; return 1;
    }
    return 0;
}
