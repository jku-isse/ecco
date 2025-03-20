/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single UDP port, with support for SSL/TLS-based
 *             session authentication and key exchange,
 *             packet encryption, packet authentication, and
 *             packet compression.
 *
 *  Copyright (C) 2002-2024 OpenVPN Inc <sales@openvpn.net>
 *  Copyright (C) 2014-2015  David Sommerseth <davids@redhat.com>
 *  Copyright (C) 2016-2024 David Sommerseth <davids@openvpn.net>
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
 *  These functions covers handing user input/output using the default consoles
 *
 */


#include "syshead.h"
#include "console.h"
#include "error.h"
#include "buffer.h"
#include "misc.h"






/**
 *  Core function for getting input from console
 *
 *  @params prompt    The prompt to present to the user
 *  @params echo      Should the user see what is being typed
 *  @params input     Pointer to the buffer used to save the user input
 *  @params capacity  Size of the input buffer
 *
 *  @returns Returns True if user input was gathered
 */
static bool
get_console_input(const char *prompt, const bool echo, char *input, const int capacity)
{
    bool ret = false;
    ASSERT(prompt);
    ASSERT(input);
    ASSERT(capacity > 0);
    input[0] = '\0';

    msg(M_FATAL, "Sorry, but I can't get console input on this OS (%s)", prompt);
    return ret;
}


/**
 * @copydoc query_user_exec()
 *
 * Default method for querying user using default stdin/stdout on a console.
 * This needs to be available as a backup interface for the alternative
 * implementations in case they cannot query through their implementation
 * specific methods.
 *
 * If no alternative implementation is declared, a wrapper in console.h will ensure
 * query_user_exec() will call this function instead.
 *
 */
bool
query_user_exec_builtin(void)
{
    bool ret = true; /* Presume everything goes okay */
    int i;

    /* Loop through configured query_user slots */
    for (i = 0; i < QUERY_USER_NUMSLOTS && query_user[i].response != NULL; i++)
    {
        if (!get_console_input(query_user[i].prompt, query_user[i].echo,
                               query_user[i].response, query_user[i].response_len) )
        {
            /* Force the final result state to failed on failure */
            ret = false;
        }
    }

    return ret;
}