#include "jpake.h"

#include <stdlib.h>
#include <string.h>

#include "psa/crypto.h"

typedef struct {
    psa_pake_operation_t operation;
    mbedtls_svc_key_id_t password_key;
} jpake_t;

static int fail(psa_status_t status) {
    return status == PSA_SUCCESS ? 0 : (status < 0 ? (int) status : -1);
}

JPAKE_API void *jpake_new(int is_server, const uint8_t *password, size_t password_len) {
    if (psa_crypto_init() != PSA_SUCCESS) {
        return NULL;
    }
    jpake_t *jp = calloc(1, sizeof(*jp));
    if (jp == NULL) {
        return NULL;
    }
    jp->operation = psa_pake_operation_init();

    psa_pake_cipher_suite_t suite = psa_pake_cipher_suite_init();
    psa_pake_cs_set_algorithm(&suite, PSA_ALG_JPAKE);
    psa_pake_cs_set_primitive(
        &suite, PSA_PAKE_PRIMITIVE(PSA_PAKE_PRIMITIVE_TYPE_ECC, PSA_ECC_FAMILY_SECP_R1, 256));
    psa_pake_cs_set_hash(&suite, PSA_ALG_SHA_256);

    psa_key_attributes_t attributes = psa_key_attributes_init();
    psa_set_key_usage_flags(&attributes, PSA_KEY_USAGE_DERIVE);
    psa_set_key_algorithm(&attributes, PSA_ALG_JPAKE);
    psa_set_key_type(&attributes, PSA_KEY_TYPE_PASSWORD);

    static const uint8_t server_id[] = "server";
    static const uint8_t client_id[] = "client";
    const uint8_t *user = is_server ? server_id : client_id;
    const uint8_t *peer = is_server ? client_id : server_id;

    if (psa_import_key(&attributes, password, password_len, &jp->password_key) != PSA_SUCCESS) {
        free(jp);
        return NULL;
    }
    if (psa_pake_setup(&jp->operation, &suite) != PSA_SUCCESS ||
        psa_pake_set_user(&jp->operation, user, 6) != PSA_SUCCESS ||
        psa_pake_set_peer(&jp->operation, peer, 6) != PSA_SUCCESS ||
        psa_pake_set_password_key(&jp->operation, jp->password_key) != PSA_SUCCESS) {
        jpake_free(jp);
        return NULL;
    }
    return jp;
}

JPAKE_API int jpake_output(void *handle, int step, uint8_t *out, size_t capacity, size_t *out_len) {
    jpake_t *jp = handle;
    return fail(psa_pake_output(&jp->operation, (psa_pake_step_t) step, out, capacity, out_len));
}

JPAKE_API int jpake_input(void *handle, int step, const uint8_t *in, size_t in_len) {
    jpake_t *jp = handle;
    return fail(psa_pake_input(&jp->operation, (psa_pake_step_t) step, in, in_len));
}

JPAKE_API int jpake_derive_key(void *handle, uint8_t out[JPAKE_KEY_BYTES]) {
    jpake_t *jp = handle;
    psa_key_derivation_operation_t derivation = psa_key_derivation_operation_init();
    psa_status_t status = psa_key_derivation_setup(&derivation, PSA_ALG_TLS12_ECJPAKE_TO_PMS);
    if (status == PSA_SUCCESS) {
        status = psa_pake_get_implicit_key(&jp->operation, &derivation);
    }
    if (status == PSA_SUCCESS) {
        status = psa_key_derivation_output_bytes(&derivation, out, JPAKE_KEY_BYTES);
    }
    psa_key_derivation_abort(&derivation);
    return fail(status);
}

JPAKE_API void jpake_free(void *handle) {
    jpake_t *jp = handle;
    if (jp == NULL) {
        return;
    }
    psa_pake_abort(&jp->operation);
    psa_destroy_key(jp->password_key);
    memset(jp, 0, sizeof(*jp));
    free(jp);
}
