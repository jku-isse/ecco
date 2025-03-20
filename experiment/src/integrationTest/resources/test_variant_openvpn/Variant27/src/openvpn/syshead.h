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

#define SYSHEAD_H

#include "compat.h"
#include <stdbool.h>

/* branch prediction hints */
#define likely(x)      (x)
#define unlikely(x)    (x)






#define WEXITSTATUS(stat_val) ((unsigned)(stat_val) >> 8)
#define WIFEXITED(stat_val) (((stat_val) & 255) == 0)


#include <time.h>







/* These headers belong to C99 and should be always be present */
#include <stdlib.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdarg.h>
#include <signal.h>
#include <limits.h>
#include <stdio.h>
#include <ctype.h>
#include <errno.h>












#include <string.h>













/*
 * Pedantic mode is meant to accomplish lint-style program checking,
 * not to build a working executable.
 */

/*
 * Do we have the capability to support the --passtos option?
 */
#define PASSTOS_CAPABILITY 0

/*
 * Do we have the capability to report extended socket errors?
 */
#define EXTENDED_SOCKET_ERROR_CAPABILITY 0

/*
 * Does this platform support linux-style IP_PKTINFO
 * or bsd-style IP_RECVDSTADDR ?
 */
#define ENABLE_IP_PKTINFO 0

/*
 * Does this platform define SOL_IP
 * or only bsd-style IPPROTO_IP ?
 */
#define SOL_IP IPPROTO_IP

/*
 * Define type sa_family_t if it isn't defined in the socket headers
 */
typedef unsigned short sa_family_t;

/*
 * Disable ESEC
 */

/*
 * Do we have a syslog capability?
 */
#define SYSLOG_CAPABILITY 0

/*
 * Does this OS draw a distinction between binary and ascii files?
 */
#define O_BINARY 0

/*
 * Directory separation char
 */
#define PATH_SEPARATOR '/'
#define PATH_SEPARATOR_STR "/"

/*
 * Our socket descriptor type.
 */
#define SOCKET_UNDEFINED (-1)
#define SOCKET_PRINTF "%d"
typedef int socket_descriptor_t;

static inline int
socket_defined(const socket_descriptor_t sd)
{
    return sd != SOCKET_UNDEFINED;
}

/*
 * Should we enable the use of execve() for calling subprocesses,
 * instead of system()?
 */

/*
 * HTTPS port sharing capability
 */
#define PORT_SHARE 0


/*
 * Do we support Unix domain sockets?
 */
#define UNIX_SOCK_SUPPORT 0

/*
 * Should we include NTLM proxy functionality
 */

/*
 * Should we include proxy digest auth functionality
 */
#define PROXY_DIGEST_AUTH 1

/*
 * Do we have CryptoAPI capability?
 */

/*
 * Is poll available on this platform?
 * (Note: on win32 select is faster than poll and we avoid
 * using poll there)
 */
#define POLL 1

/*
 * Is epoll available on this platform?
 */
#define EPOLL 0

/*
 * Compression support
 */

/*
 * Enable --memstats option
 */

