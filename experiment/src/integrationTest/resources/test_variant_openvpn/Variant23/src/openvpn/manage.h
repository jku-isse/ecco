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

#define MANAGE_H

/* management_open flags */
#define MF_SERVER            (1<<0)
#define MF_QUERY_PASSWORDS   (1<<1)
#define MF_HOLD              (1<<2)
#define MF_SIGNAL            (1<<3)
#define MF_FORGET_DISCONNECT (1<<4)
#define MF_CONNECT_AS_CLIENT (1<<5)
#define MF_CLIENT_AUTH       (1<<6)
/* #define MF_CLIENT_PF         (1<<7) *REMOVED FEATURE* */
#define MF_UNIX_SOCK                (1<<8)
#define MF_EXTERNAL_KEY             (1<<9)
#define MF_EXTERNAL_KEY_NOPADDING   (1<<10)
#define MF_EXTERNAL_KEY_PKCS1PAD    (1<<11)
#define MF_UP_DOWN                  (1<<12)
#define MF_QUERY_REMOTE             (1<<13)
#define MF_QUERY_PROXY              (1<<14)
#define MF_EXTERNAL_CERT            (1<<15)
#define MF_EXTERNAL_KEY_PSSPAD      (1<<16)
#define MF_EXTERNAL_KEY_DIGEST      (1<<17)



/**
 * A sleep function that services the management layer for n seconds rather
 * than doing nothing.
 */
void management_sleep(const int n);
