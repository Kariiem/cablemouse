#ifndef CM_STATUS_H
#define CM_STATUS_H

/* Status lines for the console. backend_open() is retried every second while the phone is away, so a line
 * that repeats the previous one is not printed again. */
#if defined(__GNUC__)
__attribute__((format(printf, 1, 2)))
#endif
void cm_status(const char *fmt, ...);
/* Forgets the previous line, so the same problem is reported again if it comes back later. */
void cm_status_reset(void);

#endif
