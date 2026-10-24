#ifndef LOCKATE_JPAKE_H
#define LOCKATE_JPAKE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JPAKE_STEP_KEY_SHARE 1
#define JPAKE_STEP_ZK_PUBLIC 2
#define JPAKE_STEP_ZK_PROOF 3

#define JPAKE_KEY_BYTES 32

#define JPAKE_API __attribute__((visibility("default")))

/*
 * EC-JPAKE (RFC 8236) over P-256 with SHA-256, through Mbed TLS' PSA API. The inviter is the
 * "server" and the invitee the "client". Every function returns 0 on success and a negative
 * number on failure; jpake_new returns NULL on failure. The round order is up to the caller
 * and is checked by Mbed TLS.
 */
JPAKE_API void *jpake_new(int is_server, const uint8_t *password, size_t password_len);

JPAKE_API int jpake_output(void *handle, int step, uint8_t *out, size_t capacity, size_t *out_len);

JPAKE_API int jpake_input(void *handle, int step, const uint8_t *in, size_t in_len);

/* Valid only after both rounds have been output and input */
JPAKE_API int jpake_derive_key(void *handle, uint8_t out[JPAKE_KEY_BYTES]);

JPAKE_API void jpake_free(void *handle);

#ifdef __cplusplus
}
#endif

#endif
