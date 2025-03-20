/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single TCP/UDP port, with support for SSL/TLS-based
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
 * plug-in support, using dynamically loaded libraries
 */

#define OPENVPN_PLUGIN_H

#include "openvpn-plugin.h"

struct plugin_list { int dummy; };
struct plugin_return { int dummy; };

static inline bool
plugin_defined(const struct plugin_list *pl, const int type)
{
    return false;
}

static inline int
plugin_call_ssl(const struct plugin_list *pl,
                const int type,
                const struct argv *av,
                struct plugin_return *pr,
                struct env_set *es,
                int current_cert_depth,
                openvpn_x509_cert_t *current_cert
                )
{
    return 0;
}


static inline int
plugin_call(const struct plugin_list *pl,
            const int type,
            const struct argv *av,
            struct plugin_return *pr,
            struct env_set *es)
{
    return plugin_call_ssl(pl, type, av, pr, es, -1, NULL);
}

void plugin_abort(void);
