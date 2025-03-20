/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single TCP/UDP port, with support for SSL/TLS-based
 *             session authentication and key exchange,
 *             packet encryption, packet authentication, and
 *             packet compression.
 *
 *  Copyright (C) 2021-2024 Arne Schwabe <arne@rfc2549.org>
 *  Copyright (C) 2021-2024 Antonio Quartulli <a@unstable.cc>
 *  Copyright (C) 2021-2024 OpenVPN Inc <sales@openvpn.net>
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
 *  You should have received a copy of the GNU General Public License
 *  along with this program (see the file COPYING included with this
 *  distribution); if not, write to the Free Software Foundation, Inc.,
 *  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
#define DCO_H

#include "buffer.h"
#include "error.h"
#include "dco_internal.h"
#include "networking.h"

/* forward declarations (including other headers leads to nasty include
 * order problems)
 */
struct event_set;
struct key2;
struct key_state;
struct multi_context;
struct multi_instance;
struct mroute_addr;
struct options;
struct tls_multi;
struct tuntap;

#define DCO_IROUTE_METRIC   100
#define DCO_DEFAULT_METRIC  200


typedef void *dco_context_t;

static inline bool
dco_available(int msglevel)
{
    return false;
}

static inline const char *
dco_version_string(struct gc_arena *gc)
{
    return "not-compiled";
}

static inline bool
dco_check_option(int msglevel, const struct options *o)
{
    return false;
}

static inline bool
dco_check_startup_option(int msglevel, const struct options *o)
{
    return false;
}

static inline bool
dco_check_pull_options(int msglevel, const struct options *o)
{
    return false;
}

static inline bool
ovpn_dco_init(int mode, dco_context_t *dco)
{
    return true;
}

static inline int
open_tun_dco(struct tuntap *tt, openvpn_net_ctx_t *ctx, const char *dev)
{
    return 0;
}

static inline void
close_tun_dco(struct tuntap *tt, openvpn_net_ctx_t *ctx)
{
}

static inline int
dco_do_read(dco_context_t *dco)
{
    ASSERT(false);
    return 0;
}

static inline void
dco_event_set(dco_context_t *dco, struct event_set *es, void *arg)
{
}

static inline int
init_key_dco_bi(struct tls_multi *multi, struct key_state *ks,
                const struct key2 *key2, int key_direction,
                const char *ciphername, bool server)
{
    return 0;
}

static inline bool
dco_update_keys(dco_context_t *dco, struct tls_multi *multi)
{
    ASSERT(false);
    return false;
}

static inline int
dco_p2p_add_new_peer(struct context *c)
{
    return 0;
}

static inline int
dco_set_peer(dco_context_t *dco, unsigned int peerid,
             int keepalive_interval, int keepalive_timeout, int mss)
{
    return 0;
}

static inline void
dco_remove_peer(struct context *c)
{
}

static inline int
dco_multi_add_new_peer(struct multi_context *m, struct multi_instance *mi)
{
    return 0;
}

static inline void
dco_install_iroute(struct multi_context *m, struct multi_instance *mi,
                   struct mroute_addr *addr)
{
}

static inline void
dco_delete_iroutes(struct multi_context *m, struct multi_instance *mi)
{
}

static inline int
dco_get_peer_stats_multi(dco_context_t *dco, struct multi_context *m)
{
    return 0;
}

static inline int
dco_get_peer_stats(struct context *c)
{
    return 0;
}

static inline const char *
dco_get_supported_ciphers()
{
    return "";
}
