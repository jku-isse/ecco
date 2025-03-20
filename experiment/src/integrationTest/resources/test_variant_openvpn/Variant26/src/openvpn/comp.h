/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single UDP port, with support for SSL/TLS-based
 *             session authentication and key exchange,
 *             packet encryption, packet authentication, and
 *             packet compression.
 *
 *  Copyright (C) 2002-2024 OpenVPN Inc <sales@openvpn.net>
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

/*
 * Generic compression support.  Currently we support
 * LZO 2 and LZ4.
 */
#define OPENVPN_COMP_H

/* We always parse all compression options, so we include these defines/struct
 * outside of the USE_COMP define */

/* Compression flags */
#define COMP_F_ADAPTIVE             (1<<0) /* COMP_ALG_LZO only */
#define COMP_F_ALLOW_COMPRESS       (1<<1) /* not only downlink is compressed but also uplink */
#define COMP_F_SWAP                 (1<<2) /* initial command byte is swapped with last byte in buffer to preserve payload alignment */
#define COMP_F_ADVERTISE_STUBS_ONLY (1<<3) /* tell server that we only support compression stubs */
#define COMP_F_ALLOW_STUB_ONLY      (1<<4) /* Only accept stub compression, even with COMP_F_ADVERTISE_STUBS_ONLY
                                            * we still accept other compressions to be pushed */
#define COMP_F_MIGRATE              (1<<5) /* push stub-v2 or comp-lzo no when we see a client with comp-lzo in occ */
#define COMP_F_ALLOW_ASYM           (1<<6) /* Compression was explicitly set to allow asymetric compression */
#define COMP_F_ALLOW_NOCOMP_ONLY    (1<<7) /* Do not allow compression framing (breaks DCO) */

/* algorithms */
#define COMP_ALG_UNDEF  0
#define COMP_ALG_STUB   1 /* support compression command byte and framing without actual compression */
#define COMP_ALG_LZO    2 /* LZO algorithm */
#define COMP_ALG_SNAPPY 3 /* Snappy algorithm (no longer supported) */
#define COMP_ALG_LZ4    4 /* LZ4 algorithm */


/* algorithm v2 */
#define COMP_ALGV2_UNCOMPRESSED 10
#define COMP_ALGV2_LZ4      11
/*
 #define COMP_ALGV2_LZO     12
 #define COMP_ALGV2_SNAPPY   13
 */

/*
 * Information that basically identifies a compression
 * algorithm and related flags.
 */
struct compress_options
{
    int alg;
    unsigned int flags;
};

static inline bool
comp_non_stub_enabled(const struct compress_options *info)
{
    return info->alg != COMP_ALGV2_UNCOMPRESSED
           && info->alg != COMP_ALG_STUB
           && info->alg != COMP_ALG_UNDEF;
}

/**
 * Checks if the compression settings are valid. Takes into account the
 * flags of allow-compression and also the whether algorithms are compiled
 * in
 */
bool
check_compression_settings_valid(struct compress_options *info, int msglevel);
