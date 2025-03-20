/*
 *  OpenVPN -- An application to securely tunnel IP networks
 *             over a single TCP/UDP port, with support for SSL/TLS-based
 *             session authentication and key exchange,
 *             packet encryption, packet authentication, and
 *             packet compression.
 *
 *  Copyright (C) 2002-2024 OpenVPN Technologies, Inc. <sales@openvpn.net>
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
#include "platform.h"
#include "win32.h"

#include "memdbg.h"

#include "run_command.h"

/* contains an SSEC_x value defined in platform.h */
static int script_security_level = SSEC_BUILT_IN; /* GLOBAL */

int
script_security(void)
{
    return script_security_level;
}

void
script_security_set(int level)
{
    script_security_level = level;
}

/*
 * Generate an error message based on the status code returned by openvpn_execve().
 */
static const char *
system_error_message(int stat, struct gc_arena *gc)
{
    struct buffer out = alloc_buf_gc(256, gc);

    switch (stat)
    {
        case OPENVPN_EXECVE_NOT_ALLOWED:
            buf_printf(&out, "disallowed by script-security setting");
            break;


        case OPENVPN_EXECVE_ERROR:
            buf_printf(&out, "external program fork failed");
            break;

        default:
            if (!WIFEXITED(stat))
            {
                buf_printf(&out, "external program did not exit normally");
            }
            else
            {
                const int cmd_ret = WEXITSTATUS(stat);
                if (!cmd_ret)
                {
                    buf_printf(&out, "external program exited normally");
                }
                else if (cmd_ret == OPENVPN_EXECVE_FAILURE)
                {
                    buf_printf(&out, "could not execute external program");
                }
                else
                {
                    buf_printf(&out, "external program exited with error status: %d", cmd_ret);
                }
            }
            break;
    }
    return (const char *)out.data;
}

bool
openvpn_execve_allowed(const unsigned int flags)
{
    if (flags & S_SCRIPT)
    {
        return script_security() >= SSEC_SCRIPTS;
    }
    else
    {
        return script_security() >= SSEC_BUILT_IN;
    }
}


/*
 * Run execve() inside a fork().  Designed to replicate the semantics of system() but
 * in a safer way that doesn't require the invocation of a shell or the risks
 * associated with formatting and parsing a command line.
 * Returns the exit status of child, OPENVPN_EXECVE_NOT_ALLOWED if openvpn_execve_allowed()
 * returns false, or OPENVPN_EXECVE_ERROR on other errors.
 */
int
openvpn_execve(const struct argv *a, const struct env_set *es, const unsigned int flags)
{
    struct gc_arena gc = gc_new();
    int ret = OPENVPN_EXECVE_ERROR;
    static bool warn_shown = false;

    if (a && a->argv[0])
    {
        msg(M_WARN, "openvpn_execve: execve function not available");
    }
    else
    {
        msg(M_FATAL, "openvpn_execve: called with empty argv");
    }

    gc_free(&gc);
    return ret;
}

/*
 * Wrapper around openvpn_execve
 */
int
openvpn_execve_check(const struct argv *a, const struct env_set *es, const unsigned int flags, const char *error_message)
{
    struct gc_arena gc = gc_new();
    const int stat = openvpn_execve(a, es, flags);
    int ret = false;

    if (flags & S_EXITCODE)
    {
        ret = platform_ret_code(stat);
        if (ret != -1)
        {
            goto done;
        }
    }
    else if (platform_system_ok(stat))
    {
        ret = true;
        goto done;
    }
    if (error_message)
    {
        msg(((flags & S_FATAL) ? M_FATAL : M_WARN), "%s: %s",
            error_message,
            system_error_message(stat, &gc));
    }
done:
    gc_free(&gc);

    return ret;
}

/*
 * Run execve() inside a fork(), duping stdout.  Designed to replicate the semantics of popen() but
 * in a safer way that doesn't require the invocation of a shell or the risks
 * associated with formatting and parsing a command line.
 */
int
openvpn_popen(const struct argv *a,  const struct env_set *es)
{
    struct gc_arena gc = gc_new();
    int ret = -1;

    if (a && a->argv[0])
    {
        msg(M_WARN, "openvpn_popen: execve function not available");
    }
    else
    {
        msg(M_FATAL, "openvpn_popen: called with empty argv");
    }

    gc_free(&gc);
    return ret;
}