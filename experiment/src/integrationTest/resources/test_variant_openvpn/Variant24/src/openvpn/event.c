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


#include "syshead.h"

#include "buffer.h"
#include "error.h"
#include "integer.h"
#include "event.h"
#include "fdmisc.h"


#include "memdbg.h"

/*
 * Some OSes will prefer select() over poll()
 * when both are available.
 */

/*
 * All non-windows OSes are assumed to have select()
 */
#define SELECT 1

/*
 * This should be set to the highest file descriptor
 * which can be used in one of the FD_ macros.
 */
#define SELECT_MAX_FDS 256

static inline int
tv_to_ms_timeout(const struct timeval *tv)
{
    if (tv->tv_sec == 0 && tv->tv_usec == 0)
    {
        return 0;
    }
    else
    {
        return max_int(tv->tv_sec * 1000 + (tv->tv_usec + 500) / 1000, 1);
    }
}





static struct event_set *
event_set_init_simple(int *maxevents, unsigned int flags)
{
    struct event_set *ret = NULL;
#error At least one of poll, select, or WSAWaitForMultipleEvents must be supported by the kernel
    ASSERT(ret);
    return ret;
}

static struct event_set *
event_set_init_scalable(int *maxevents, unsigned int flags)
{
    struct event_set *ret = NULL;
    ret = event_set_init_simple(maxevents, flags);
    ASSERT(ret);
    return ret;
}

struct event_set *
event_set_init(int *maxevents, unsigned int flags)
{
    if (flags & EVENT_METHOD_FAST)
    {
        return event_set_init_simple(maxevents, flags);
    }
    else
    {
        return event_set_init_scalable(maxevents, flags);
    }
}