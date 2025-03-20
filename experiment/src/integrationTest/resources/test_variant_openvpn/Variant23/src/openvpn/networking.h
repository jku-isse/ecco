/*
 *  Generic interface to platform specific networking code
 *
 *  Copyright (C) 2016-2024 Antonio Quartulli <a@unstable.cc>
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

#define NETWORKING_H_

#include "syshead.h"

#define IFACE_TYPE_LEN_MAX 64

struct context;

/* define mock types to ensure code builds on any platform */
typedef void *openvpn_net_ctx_t;
typedef void *openvpn_net_iface_t;

/* Only the iproute2 backend implements these functions,
 * the rest can rely on these stubs
 */
static inline int
net_ctx_init(struct context *c, openvpn_net_ctx_t *ctx)
{
    (void)c;
    (void)ctx;

    return 0;
}

static inline void
net_ctx_reset(openvpn_net_ctx_t *ctx)
{
    (void)ctx;
}

static inline void
net_ctx_free(openvpn_net_ctx_t *ctx)
{
    (void)ctx;
}



