/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single TCP/UDP port, with support for SSL/TLS-based
 *             session authentication and key exchange,
 *             packet encryption, packet authentication, and
 *             packet compression.
 *
 *  Copyright (C) 2023 Fox Crypto B.V. <openvpn@foxcrypto.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2
 *  as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

/**
 * @file mbedtls compatibility stub
 *
 * This file provide compatibility stubs for the mbedtls libraries
 * prior to version 3. This version made most fields in structs private
 * and requires accessor functions to be used. For earlier versions, we
 * implement the accessor functions here.
 */

#define MBEDTLS_COMPAT_H_

#include "syshead.h"

#include "errlevel.h"

#include <mbedtls/cipher.h>
#include <mbedtls/ctr_drbg.h>
#include <mbedtls/dhm.h>
#include <mbedtls/md.h>
#include <mbedtls/pem.h>
#include <mbedtls/pk.h>
#include <mbedtls/ssl.h>
#include <mbedtls/version.h>
#include <mbedtls/x509_crt.h>


static inline void
mbedtls_compat_psa_crypto_init(void)
{
    return;
}

/*
 * In older versions of mbedtls, mbedtls_ctr_drbg_update() did not return an
 * error code, and it was deprecated in favor of mbedtls_ctr_drbg_update_ret()
 * which does.
 *
 * In mbedtls 3, this function was removed and mbedtls_ctr_drbg_update() returns
 * an error code.
 */
static inline int
mbedtls_compat_ctr_drbg_update(mbedtls_ctr_drbg_context *ctx,
                               const unsigned char *additional,
                               size_t add_len)
{
    mbedtls_ctr_drbg_update(ctx, additional, add_len);
    return 0;
}

static inline int
mbedtls_compat_pk_check_pair(const mbedtls_pk_context *pub, const mbedtls_pk_context *prv,
                             int (*f_rng)(void *, unsigned char *, size_t), void *p_rng)
{
    return mbedtls_pk_check_pair(pub, prv, f_rng, p_rng);
}

static inline int
mbedtls_compat_pk_parse_key(mbedtls_pk_context *ctx,
                            const unsigned char *key, size_t keylen,
                            const unsigned char *pwd, size_t pwdlen,
                            int (*f_rng)(void *, unsigned char *, size_t), void *p_rng)
{
    return mbedtls_pk_parse_key(ctx, key, keylen, pwd, pwdlen, f_rng, p_rng);
}

static inline int
mbedtls_compat_pk_parse_keyfile(mbedtls_pk_context *ctx,
                                const char *path, const char *password,
                                int (*f_rng)(void *, unsigned char *, size_t), void *p_rng)
{
    return mbedtls_pk_parse_keyfile(ctx, path, password, f_rng, p_rng);
}

