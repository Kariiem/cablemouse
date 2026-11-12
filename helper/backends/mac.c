/* macOS back end: TODO (CoreMIDI + CGEventPost via cosmo_dlopen). See PLAN.md Phase 5 step 21. */
#include <stdio.h>
#include "../backend.h"
int backend_open(const char *h) { (void)h; fprintf(stderr, "macOS back end not implemented yet.\n"); return -1; }
int backend_read_midi(uint8_t *b, int l, int t) { (void)b; (void)l; (void)t; return -1; }
void backend_move(int dx, int dy) { (void)dx; (void)dy; }
void backend_button(int b, int d) { (void)b; (void)d; }
void backend_scroll(int c) { (void)c; }
void backend_close(void) {}
