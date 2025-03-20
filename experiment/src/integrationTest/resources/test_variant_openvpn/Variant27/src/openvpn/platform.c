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

#include "openvpn.h"
#include "options.h"

#include "buffer.h"
#include "crypto.h"
#include "error.h"
#include "misc.h"
#include "win32.h"

#include "memdbg.h"

#include "platform.h"



/* Redefine the top level directory of the filesystem
 * to restrict access to files for security */
void
platform_chroot(const char *path)
{
    if (path)
    {
        msg(M_FATAL, "Sorry but I can't chroot to '%s' because this operating system doesn't appear to support the chroot() system call", path);
    }
}

/* Get/Set UID of process */

bool
platform_user_get(const char *username, struct platform_state_user *state)
{
    bool ret = false;
    CLEAR(*state);
    if (username)
    {
        msg(M_FATAL, "cannot get UID for user %s -- platform lacks getpwname() or setuid() system calls", username);
    }
    return ret;
}

static void
platform_user_set(const struct platform_state_user *state)
{
}

/* Get/Set GID of process */

bool
platform_group_get(const char *groupname, struct platform_state_group *state)
{
    bool ret = false;
    CLEAR(*state);
    if (groupname)
    {
        msg(M_FATAL, "cannot get GID for group %s -- platform lacks getgrnam() or setgid() system calls", groupname);
    }
    return ret;
}

static void
platform_group_set(const struct platform_state_group *state)
{
}

/*
 * Determine if we need to retain process capabilities. DCO and SITNL need it.
 * Enforce it for DCO, but only try and soft-fail for SITNL to keep backwards compat.
 *
 * Returns the tri-state expected by platform_user_group_set.
 * -1: try to keep caps, but continue if impossible
 *  0: don't keep caps
 *  1: keep caps, fail hard if impossible
 */
static int
need_keep_caps(struct context *c)
{
    if (!c)
    {
        return -1;
    }

    if (dco_enabled(&c->options))
    {
        /* Windows/BSD/... has no equivalent capability mechanism */
        return -1;
    }

    return 0;
}

/* Set user and group, retaining neccesary capabilities required by the platform.
 *
 * The keep_caps argument has 3 possible states:
 *  >0: Retain capabilities, and fail hard on failure to do so.
 * ==0: Don't attempt to retain any capabilities, just sitch user/group.
 *  <0: Try to retain capabilities, but continue on failure.
 */
void
platform_user_group_set(const struct platform_state_user *user_state,
                        const struct platform_state_group *group_state,
                        struct context *c)
{
    int keep_caps = need_keep_caps(c);
    unsigned int err_flags = (keep_caps > 0) ? M_FATAL : M_NONFATAL;

    if (keep_caps)
    {
        msg(err_flags, "Unable to retain capabilities");
    }

    platform_group_set(group_state);
    platform_user_set(user_state);
}

/* Change process priority */
void
platform_nice(int niceval)
{
    if (niceval)
    {
        msg(M_WARN, "WARNING: nice %d failed (function not implemented)", niceval);
    }
}

/* Get current PID */
unsigned int
platform_getpid(void)
{
    return (unsigned int) getpid();
}

/* Disable paging */
void
platform_mlockall(bool print_msg)
{
    msg(M_WARN, "WARNING: mlockall call failed (function not implemented)");
}

/*
 * Wrapper for chdir library function
 */
int
platform_chdir(const char *dir)
{
    return -1;
}

/*
 * convert execve() return into a success/failure value
 */
bool
platform_system_ok(int stat)
{
    return stat != -1 && WIFEXITED(stat) && WEXITSTATUS(stat) == 0;
}

int
platform_ret_code(int stat)
{
    if (!WIFEXITED(stat) || stat == -1)
    {
        return -1;
    }

    int status = WEXITSTATUS(stat);
    if (status >= 0 && status < 255)
    {
        return status;
    }
    else
    {
        return -1;
    }
}

int
platform_access(const char *path, int mode)
{
    return access(path, mode);
}

/*
 * Go to sleep for n milliseconds.
 */
void
platform_sleep_milliseconds(unsigned int n)
{
    struct timeval tv;
    tv.tv_sec = n / 1000;
    tv.tv_usec = (n % 1000) * 1000;
    select(0, NULL, NULL, NULL, &tv);
}

/*
 * Go to sleep indefinitely.
 */
void
platform_sleep_until_signal(void)
{
    select(0, NULL, NULL, NULL, NULL);
}

/* delete a file, return true if succeeded */
bool
platform_unlink(const char *filename)
{
    return (unlink(filename) == 0);
}

FILE *
platform_fopen(const char *path, const char *mode)
{
    return fopen(path, mode);
}

int
platform_open(const char *path, int flags, int mode)
{
    return open(path, flags, mode);
}

int
platform_stat(const char *path, platform_stat_t *buf)
{
    return stat(path, buf);
}

/* create a temporary filename in directory */
const char *
platform_create_temp_file(const char *directory, const char *prefix, struct gc_arena *gc)
{
    int fd;
    const char *retfname = NULL;
    unsigned int attempts = 0;
    char fname[256] = { 0 };
    const char *fname_fmt = PACKAGE "_%.*s_%08lx%08lx.tmp";
    const int max_prefix_len = sizeof(fname) - (sizeof(PACKAGE) + 7 + (2 * 8));

    while (attempts < 6)
    {
        ++attempts;

        if (!snprintf(fname, sizeof(fname), fname_fmt, max_prefix_len,
                      prefix, (unsigned long) get_random(),
                      (unsigned long) get_random()))
        {
            msg(M_WARN, "ERROR: temporary filename too long");
            return NULL;
        }

        retfname = platform_gen_path(directory, fname, gc);
        if (!retfname)
        {
            msg(M_WARN, "Failed to create temporary filename and path");
            return NULL;
        }

        /* Atomically create the file.  Errors out if the file already
         * exists.  */
        fd = platform_open(retfname, O_CREAT | O_EXCL | O_WRONLY, S_IRUSR | S_IWUSR);
        if (fd != -1)
        {
            close(fd);
            return retfname;
        }
        else if (fd == -1 && errno != EEXIST)
        {
            /* Something else went wrong, no need to retry.  */
            msg(M_WARN | M_ERRNO, "Could not create temporary file '%s'",
                retfname);
            return NULL;
        }
    }

    msg(M_WARN, "Failed to create temporary file after %i attempts", attempts);
    return NULL;
}

/*
 * Put a directory and filename together.
 */
const char *
platform_gen_path(const char *directory, const char *filename,
                  struct gc_arena *gc)
{
    const int CC_PATH_RESERVED = CC_SLASH;

    if (!gc)
    {
        return NULL; /* Would leak memory otherwise */
    }

    const char *safe_filename = string_mod_const(filename, CC_PRINT, CC_PATH_RESERVED, '_', gc);

    if (safe_filename
        && strcmp(safe_filename, ".")
        && strcmp(safe_filename, "..")
        )
    {
        const size_t outsize = strlen(safe_filename) + (directory ? strlen(directory) : 0) + 16;
        struct buffer out = alloc_buf_gc(outsize, gc);
        char dirsep[2];

        dirsep[0] = PATH_SEPARATOR;
        dirsep[1] = '\0';

        if (directory)
        {
            buf_printf(&out, "%s%s", directory, dirsep);
        }
        buf_printf(&out, "%s", safe_filename);

        return BSTR(&out);
    }
    else
    {
        return NULL;
    }
}

bool
platform_absolute_pathname(const char *pathname)
{
    if (pathname)
    {
        const int c = pathname[0];
        return c == '/';
    }
    else
    {
        return false;
    }
}

/* return true if filename can be opened for read */
bool
platform_test_file(const char *filename)
{
    bool ret = false;
    if (filename)
    {
        FILE *fp = platform_fopen(filename, "r");
        if (fp)
        {
            fclose(fp);
            ret = true;
        }
        else
        {
            if (errno == EACCES)
            {
                msg( M_WARN | M_ERRNO, "Could not access file '%s'", filename);
            }
        }
    }

    dmsg(D_TEST_FILE, "TEST FILE '%s' [%d]",
         filename ? filename : "UNDEF",
         ret);

    return ret;
}