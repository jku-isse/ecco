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


#include "syshead.h"

#include "comp.h"
#include "error.h"


bool
check_compression_settings_valid(struct compress_options *info, int msglevel)
{
    /*
     * We also allow comp-stub-v2 here as it technically allows escaping of
     * weird mac address and IPv5 protocol but practically always is used
     * as an way to disable all framing.
     */
    if (info->alg != COMP_ALGV2_UNCOMPRESSED && info->alg != COMP_ALG_UNDEF
        && (info->flags & COMP_F_ALLOW_NOCOMP_ONLY))
    {
        msg(msglevel, "Compression or compression stub framing is not allowed "
            "since OpenVPN was built without compression support.");
        return false;
    }

    if ((info->flags & COMP_F_ALLOW_STUB_ONLY) && comp_non_stub_enabled(info))
    {
        msg(msglevel, "Compression is not allowed since allow-compression is "
            "set to 'stub-only'");
        return false;
    }
    if (info->alg == COMP_ALGV2_LZ4 || info->alg == COMP_ALG_LZ4)
    {
        msg(msglevel, "OpenVPN is compiled without LZ4 support. Requested "
            "compression cannot be enabled.");
        return false;
    }
    if (info->alg == COMP_ALG_LZO)
    {
        msg(msglevel, "OpenVPN is compiled without LZO support. Requested "
            "compression cannot be enabled.");
        return false;
    }
    return true;
}