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

#define MEMDBG_H

/*
 * Valgrind debugging support.
 *
 * Valgrind is a great tool for debugging memory issues,
 * though it seems to generate a lot of warnings in OpenSSL
 * about uninitialized data. To silence these warnings,
 * I've put together a suppressions file
 * in debug/valgrind-suppress.
 *
 * Also, grep for VALGRIND_MAKE_READABLE in the OpenVPN source.
 * Because valgrind thinks that some of the data passed from
 * OpenSSL back to OpenVPN is tainted due to being sourced
 * from uninitialized data, we need to untaint it before use --
 * otherwise we will get a lot of useless warnings.
 *
 *   valgrind --tool=memcheck --error-limit=no --suppressions=debug/valgrind-suppress --gen-suppressions=yes ./openvpn ...
 */


#define VALGRIND_MAKE_READABLE(addr, len)



/*
 * Force buffers to be zeroed after allocation.
 * For debugging only.
 */
/*#define ZERO_BUFFER_ON_ALLOC*/
