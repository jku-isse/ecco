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
 * Support routines for configuring and accessing TUN/TAP
 * virtual network adapters.
 *
 * This file is based on the TUN/TAP driver interface routines
 * from VTun by Maxim Krasnyansky <max_mk@yahoo.com>.
 */


#include "syshead.h"

#include "openvpn.h"
#include "tun.h"
#include "fdmisc.h"
#include "common.h"
#include "run_command.h"
#include "socket.h"
#include "manage.h"
#include "route.h"
#include "win32.h"
#include "block_dns.h"
#include "networking.h"

#include "memdbg.h"


#include <string.h>




static void clear_tuntap(struct tuntap *tuntap);

bool
is_dev_type(const char *dev, const char *dev_type, const char *match_type)
{
    ASSERT(match_type);
    if (!dev)
    {
        return false;
    }
    if (dev_type)
    {
        return !strcmp(dev_type, match_type);
    }
    else
    {
        return !strncmp(dev, match_type, strlen(match_type));
    }
}

int
dev_type_enum(const char *dev, const char *dev_type)
{
    if (is_dev_type(dev, dev_type, "tun"))
    {
        return DEV_TYPE_TUN;
    }
    else if (is_dev_type(dev, dev_type, "tap"))
    {
        return DEV_TYPE_TAP;
    }
    else if (is_dev_type(dev, dev_type, "null"))
    {
        return DEV_TYPE_NULL;
    }
    else
    {
        return DEV_TYPE_UNDEF;
    }
}

const char *
dev_type_string(const char *dev, const char *dev_type)
{
    switch (dev_type_enum(dev, dev_type))
    {
        case DEV_TYPE_TUN:
            return "tun";

        case DEV_TYPE_TAP:
            return "tap";

        case DEV_TYPE_NULL:
            return "null";

        default:
            return "[unknown-dev-type]";
    }
}

/*
 * Try to predict the actual TUN/TAP device instance name,
 * before the device is actually opened.
 */
const char *
guess_tuntap_dev(const char *dev,
                 const char *dev_type,
                 const char *dev_node,
                 struct gc_arena *gc)
{

    /* default case */
    return dev;
}


/* --ifconfig-nowarn disables some options sanity checking */
static const char ifconfig_warn_how_to_silence[] = "(silence this warning with --ifconfig-nowarn)";

/*
 * If !tun, make sure ifconfig_remote_netmask looks
 *  like a netmask.
 *
 * If tun, make sure ifconfig_remote_netmask looks
 *  like an IPv4 address.
 */
static void
ifconfig_sanity_check(bool tun, in_addr_t addr, int topology)
{
    struct gc_arena gc = gc_new();
    const bool looks_like_netmask = ((addr & 0xFF000000) == 0xFF000000);
    if (tun)
    {
        if (looks_like_netmask && (topology == TOP_NET30 || topology == TOP_P2P))
        {
            msg(M_WARN, "WARNING: Since you are using --dev tun with a point-to-point topology, the second argument to --ifconfig must be an IP address.  You are using something (%s) that looks more like a netmask. %s",
                print_in_addr_t(addr, 0, &gc),
                ifconfig_warn_how_to_silence);
        }
    }
    else /* tap */
    {
        if (!looks_like_netmask)
        {
            msg(M_WARN, "WARNING: Since you are using --dev tap, the second argument to --ifconfig must be a netmask, for example something like 255.255.255.0. %s",
                ifconfig_warn_how_to_silence);
        }
    }
    gc_free(&gc);
}

/*
 * Check that --local and --remote addresses do not
 * clash with ifconfig addresses or subnet.
 */
static void
check_addr_clash(const char *name,
                 int type,
                 in_addr_t public,
                 in_addr_t local,
                 in_addr_t remote_netmask)
{
    struct gc_arena gc = gc_new();

    if (public)
    {
        if (type == DEV_TYPE_TUN)
        {
            const in_addr_t test_netmask = 0xFFFFFF00;
            const in_addr_t public_net = public &test_netmask;
            const in_addr_t local_net = local & test_netmask;
            const in_addr_t remote_net = remote_netmask & test_netmask;

            if (public == local || public == remote_netmask)
            {
                msg(M_WARN,
                    "WARNING: --%s address [%s] conflicts with --ifconfig address pair [%s, %s]. %s",
                    name,
                    print_in_addr_t(public, 0, &gc),
                    print_in_addr_t(local, 0, &gc),
                    print_in_addr_t(remote_netmask, 0, &gc),
                    ifconfig_warn_how_to_silence);
            }

            if (public_net == local_net || public_net == remote_net)
            {
                msg(M_WARN,
                    "WARNING: potential conflict between --%s address [%s] and --ifconfig address pair [%s, %s] -- this is a warning only that is triggered when local/remote addresses exist within the same /24 subnet as --ifconfig endpoints. %s",
                    name,
                    print_in_addr_t(public, 0, &gc),
                    print_in_addr_t(local, 0, &gc),
                    print_in_addr_t(remote_netmask, 0, &gc),
                    ifconfig_warn_how_to_silence);
            }
        }
        else if (type == DEV_TYPE_TAP)
        {
            const in_addr_t public_network = public &remote_netmask;
            const in_addr_t virtual_network = local & remote_netmask;
            if (public_network == virtual_network)
            {
                msg(M_WARN,
                    "WARNING: --%s address [%s] conflicts with --ifconfig subnet [%s, %s] -- local and remote addresses cannot be inside of the --ifconfig subnet. %s",
                    name,
                    print_in_addr_t(public, 0, &gc),
                    print_in_addr_t(local, 0, &gc),
                    print_in_addr_t(remote_netmask, 0, &gc),
                    ifconfig_warn_how_to_silence);
            }
        }
    }
    gc_free(&gc);
}

/*
 * Issue a warning if ip/netmask (on the virtual IP network) conflicts with
 * the settings on the local LAN.  This is designed to flag issues where
 * (for example) the OpenVPN server LAN is running on 192.168.1.x, but then
 * an OpenVPN client tries to connect from a public location that is also running
 * off of a router set to 192.168.1.x.
 */
void
check_subnet_conflict(const in_addr_t ip,
                      const in_addr_t netmask,
                      const char *prefix)
{
}

void
warn_on_use_of_common_subnets(openvpn_net_ctx_t *ctx)
{
    struct gc_arena gc = gc_new();
    struct route_gateway_info rgi;
    const int needed = (RGI_ADDR_DEFINED|RGI_NETMASK_DEFINED);

    get_default_gateway(&rgi, ctx);
    if ((rgi.flags & needed) == needed)
    {
        const in_addr_t lan_network = rgi.gateway.addr & rgi.gateway.netmask;
        if (lan_network == 0xC0A80000 || lan_network == 0xC0A80100)
        {
            msg(M_WARN, "NOTE: your local LAN uses the extremely common subnet address 192.168.0.x or 192.168.1.x.  Be aware that this might create routing conflicts if you connect to the VPN server from public locations such as internet cafes that use the same subnet.");
        }
    }
    gc_free(&gc);
}

/*
 * Return a string to be used for options compatibility check
 * between peers.
 */
const char *
ifconfig_options_string(const struct tuntap *tt, bool remote, bool disable, struct gc_arena *gc)
{
    struct buffer out = alloc_buf_gc(256, gc);
    if (tt->did_ifconfig_setup && !disable)
    {
        if (tt->type == DEV_TYPE_TAP || (tt->type == DEV_TYPE_TUN && tt->topology == TOP_SUBNET))
        {
            buf_printf(&out, "%s %s",
                       print_in_addr_t(tt->local & tt->remote_netmask, 0, gc),
                       print_in_addr_t(tt->remote_netmask, 0, gc));
        }
        else if (tt->type == DEV_TYPE_TUN)
        {
            const char *l, *r;
            if (remote)
            {
                r = print_in_addr_t(tt->local, 0, gc);
                l = print_in_addr_t(tt->remote_netmask, 0, gc);
            }
            else
            {
                l = print_in_addr_t(tt->local, 0, gc);
                r = print_in_addr_t(tt->remote_netmask, 0, gc);
            }
            buf_printf(&out, "%s %s", r, l);
        }
        else
        {
            buf_printf(&out, "[undef]");
        }
    }
    return BSTR(&out);
}

/*
 * Return a status string describing wait state.
 */
const char *
tun_stat(const struct tuntap *tt, unsigned int rwflags, struct gc_arena *gc)
{
    struct buffer out = alloc_buf_gc(64, gc);
    if (tt)
    {
        if (rwflags & EVENT_READ)
        {
            buf_printf(&out, "T%s",
                       (tt->rwflags_debug & EVENT_READ) ? "R" : "r");
        }
        if (rwflags & EVENT_WRITE)
        {
            buf_printf(&out, "T%s",
                       (tt->rwflags_debug & EVENT_WRITE) ? "W" : "w");
        }
    }
    else
    {
        buf_printf(&out, "T?");
    }
    return BSTR(&out);
}

/*
 * Return true for point-to-point topology, false for subnet topology
 */
bool
is_tun_p2p(const struct tuntap *tt)
{
    bool tun = false;

    if (tt->type == DEV_TYPE_TAP
        || (tt->type == DEV_TYPE_TUN && tt->topology == TOP_SUBNET)
        || tt->type == DEV_TYPE_NULL)
    {
        tun = false;
    }
    else if (tt->type == DEV_TYPE_TUN)
    {
        tun = true;
    }
    else
    {
        msg(M_FATAL, "Error: problem with tun vs. tap setting"); /* JYFIXME -- needs to be caught earlier, in init_tun? */

    }
    return tun;
}

/*
 * Set the ifconfig_* environment variables, both for IPv4 and IPv6
 */
void
do_ifconfig_setenv(const struct tuntap *tt, struct env_set *es)
{
    struct gc_arena gc = gc_new();
    const char *ifconfig_local = print_in_addr_t(tt->local, 0, &gc);
    const char *ifconfig_remote_netmask = print_in_addr_t(tt->remote_netmask, 0, &gc);

    /*
     * Set environmental variables with ifconfig parameters.
     */
    if (tt->did_ifconfig_setup)
    {
        bool tun = is_tun_p2p(tt);

        setenv_str(es, "ifconfig_local", ifconfig_local);
        if (tun)
        {
            setenv_str(es, "ifconfig_remote", ifconfig_remote_netmask);
        }
        else
        {
            setenv_str(es, "ifconfig_netmask", ifconfig_remote_netmask);
        }
    }

    if (tt->did_ifconfig_ipv6_setup)
    {
        const char *ifconfig_ipv6_local = print_in6_addr(tt->local_ipv6, 0, &gc);
        const char *ifconfig_ipv6_remote = print_in6_addr(tt->remote_ipv6, 0, &gc);

        setenv_str(es, "ifconfig_ipv6_local", ifconfig_ipv6_local);
        setenv_int(es, "ifconfig_ipv6_netbits", tt->netbits_ipv6);
        setenv_str(es, "ifconfig_ipv6_remote", ifconfig_ipv6_remote);
    }

    gc_free(&gc);
}

/*
 * Init tun/tap object.
 *
 * Set up tuntap structure for ifconfig,
 * but don't execute yet.
 */
struct tuntap *
init_tun(const char *dev,        /* --dev option */
         const char *dev_type,   /* --dev-type option */
         int topology,           /* one of the TOP_x values */
         const char *ifconfig_local_parm,           /* --ifconfig parm 1 */
         const char *ifconfig_remote_netmask_parm,  /* --ifconfig parm 2 */
         const char *ifconfig_ipv6_local_parm,      /* --ifconfig parm 1 IPv6 */
         int ifconfig_ipv6_netbits_parm,
         const char *ifconfig_ipv6_remote_parm,     /* --ifconfig parm 2 IPv6 */
         struct addrinfo *local_public,
         struct addrinfo *remote_public,
         const bool strict_warn,
         struct env_set *es,
         openvpn_net_ctx_t *ctx,
         struct tuntap *tt)
{
    if (!tt)
    {
        ALLOC_OBJ(tt, struct tuntap);
        clear_tuntap(tt);
    }

    tt->type = dev_type_enum(dev, dev_type);
    tt->topology = topology;

    if (ifconfig_local_parm && ifconfig_remote_netmask_parm)
    {
        bool tun = false;

        /*
         * We only handle TUN/TAP devices here, not --dev null devices.
         */
        tun = is_tun_p2p(tt);

        /*
         * Convert arguments to binary IPv4 addresses.
         */

        tt->local = getaddr(
            GETADDR_RESOLVE
            | GETADDR_HOST_ORDER
            | GETADDR_FATAL_ON_SIGNAL
            | GETADDR_FATAL,
            ifconfig_local_parm,
            0,
            NULL,
            NULL);

        tt->remote_netmask = getaddr(
            (tun ? GETADDR_RESOLVE : 0)
            | GETADDR_HOST_ORDER
            | GETADDR_FATAL_ON_SIGNAL
            | GETADDR_FATAL,
            ifconfig_remote_netmask_parm,
            0,
            NULL,
            NULL);

        /*
         * Look for common errors in --ifconfig parms
         */
        if (strict_warn)
        {
            struct addrinfo *curele;
            ifconfig_sanity_check(tt->type == DEV_TYPE_TUN, tt->remote_netmask, tt->topology);

            /*
             * If local_public or remote_public addresses are defined,
             * make sure they do not clash with our virtual subnet.
             */

            for (curele = local_public; curele; curele = curele->ai_next)
            {
                if (curele->ai_family == AF_INET)
                {
                    check_addr_clash("local",
                                     tt->type,
                                     ((struct sockaddr_in *)curele->ai_addr)->sin_addr.s_addr,
                                     tt->local,
                                     tt->remote_netmask);
                }
            }

            for (curele = remote_public; curele; curele = curele->ai_next)
            {
                if (curele->ai_family == AF_INET)
                {
                    check_addr_clash("remote",
                                     tt->type,
                                     ((struct sockaddr_in *)curele->ai_addr)->sin_addr.s_addr,
                                     tt->local,
                                     tt->remote_netmask);
                }
            }

            if (tt->type == DEV_TYPE_TAP || (tt->type == DEV_TYPE_TUN && tt->topology == TOP_SUBNET))
            {
                check_subnet_conflict(tt->local, tt->remote_netmask, "TUN/TAP adapter");
            }
            else if (tt->type == DEV_TYPE_TUN)
            {
                check_subnet_conflict(tt->local, IPV4_NETMASK_HOST, "TUN/TAP adapter");
            }
        }


        tt->did_ifconfig_setup = true;
    }

    if (ifconfig_ipv6_local_parm && ifconfig_ipv6_remote_parm)
    {

        /*
         * Convert arguments to binary IPv6 addresses.
         */

        if (inet_pton( AF_INET6, ifconfig_ipv6_local_parm, &tt->local_ipv6 ) != 1
            || inet_pton( AF_INET6, ifconfig_ipv6_remote_parm, &tt->remote_ipv6 ) != 1)
        {
            msg( M_FATAL, "init_tun: problem converting IPv6 ifconfig addresses %s and %s to binary", ifconfig_ipv6_local_parm, ifconfig_ipv6_remote_parm );
        }
        tt->netbits_ipv6 = ifconfig_ipv6_netbits_parm;

        tt->did_ifconfig_ipv6_setup = true;
    }

    /*
     * Set environmental variables with ifconfig parameters.
     */
    if (es)
    {
        do_ifconfig_setenv(tt, es);
    }

    return tt;
}

/*
 * Platform specific tun initializations
 */
void
init_tun_post(struct tuntap *tt,
              const struct frame *frame,
              const struct tuntap_options *options)
{
    tt->options = *options;
}



/**
 * do_ifconfig_ipv6 - perform platform specific ifconfig6 commands
 *
 * @param tt        the tuntap interface context
 * @param ifname    the human readable interface name
 * @param mtu       the MTU value to set the interface to
 * @param es        the environment to be used when executing the commands
 * @param ctx       the networking API opaque context
 */
static void
do_ifconfig_ipv6(struct tuntap *tt, const char *ifname, int tun_mtu,
                 const struct env_set *es, openvpn_net_ctx_t *ctx)
{
    struct argv argv = argv_new();
    struct gc_arena gc = gc_new();
    const char *ifconfig_ipv6_local = print_in6_addr(tt->local_ipv6, 0, &gc);

    msg(M_FATAL, "Sorry, but I don't know how to do IPv6 'ifconfig' commands on this operating system.  You should ifconfig your TUN/TAP device manually or use an --up script.");

    gc_free(&gc);
    argv_free(&argv);
}

/**
 * do_ifconfig_ipv4 - perform platform specific ifconfig commands
 *
 * @param tt        the tuntap interface context
 * @param ifname    the human readable interface name
 * @param mtu       the MTU value to set the interface to
 * @param es        the environment to be used when executing the commands
 * @param ctx       the networking API opaque context
 */
static void
do_ifconfig_ipv4(struct tuntap *tt, const char *ifname, int tun_mtu,
                 const struct env_set *es, openvpn_net_ctx_t *ctx)
{
    /*
     * We only handle TUN/TAP devices here, not --dev null devices.
     */
    bool tun = is_tun_p2p(tt);

    const char *ifconfig_local = NULL;
    const char *ifconfig_remote_netmask = NULL;
    struct argv argv = argv_new();
    struct gc_arena gc = gc_new();

    /*
     * Set ifconfig parameters
     */
    ifconfig_local = print_in_addr_t(tt->local, 0, &gc);
    ifconfig_remote_netmask = print_in_addr_t(tt->remote_netmask, 0, &gc);

    msg(M_FATAL, "Sorry, but I don't know how to do 'ifconfig' commands on this operating system.  You should ifconfig your TUN/TAP device manually or use an --up script.");

    gc_free(&gc);
    argv_free(&argv);
}

/* execute the ifconfig command through the shell */
void
do_ifconfig(struct tuntap *tt, const char *ifname, int tun_mtu,
            const struct env_set *es, openvpn_net_ctx_t *ctx)
{
    msg(D_LOW, "do_ifconfig, ipv4=%d, ipv6=%d", tt->did_ifconfig_setup,
        tt->did_ifconfig_ipv6_setup);


    if (tt->did_ifconfig_setup)
    {
        do_ifconfig_ipv4(tt, ifname, tun_mtu, es, ctx);
    }

    if (tt->did_ifconfig_ipv6_setup)
    {
        do_ifconfig_ipv6(tt, ifname, tun_mtu, es, ctx);
    }

    /* release resources potentially allocated during interface setup */
    net_ctx_free(ctx);
}

static void
undo_ifconfig_ipv4(struct tuntap *tt, openvpn_net_ctx_t *ctx)
{
       /* Empty for _WIN32 and all other unixoid platforms */
}

static void
undo_ifconfig_ipv6(struct tuntap *tt, openvpn_net_ctx_t *ctx)
{
       /* Empty for _WIN32 and all other unixoid platforms */
}

void
undo_ifconfig(struct tuntap *tt, openvpn_net_ctx_t *ctx)
{
    if (tt->type != DEV_TYPE_NULL)
    {
        if (tt->did_ifconfig_setup)
        {
            undo_ifconfig_ipv4(tt, ctx);
        }

        if (tt->did_ifconfig_ipv6_setup)
        {
            undo_ifconfig_ipv6(tt, ctx);
        }

        /* release resources potentially allocated during undo */
        net_ctx_reset(ctx);
    }
}

static void
clear_tuntap(struct tuntap *tuntap)
{
    CLEAR(*tuntap);
    tuntap->fd = -1;
}

static void
open_null(struct tuntap *tt)
{
    tt->actual_name = string_alloc("null", NULL);
}



bool
tun_name_is_fixed(const char *dev)
{
    return has_digit(dev);
}



static void
open_tun_generic(const char *dev, const char *dev_type, const char *dev_node,
                 struct tuntap *tt)
{
    char tunname[256];
    char dynamic_name[256];
    bool dynamic_opened = false;

    if (tt->type == DEV_TYPE_NULL)
    {
        open_null(tt);
    }
    else
    {
        /*
         * --dev-node specified, so open an explicit device node
         */
        if (dev_node)
        {
            snprintf(tunname, sizeof(tunname), "%s", dev_node);
        }
        else
        {
            /*
             * dynamic open is indicated by --dev specified without
             * explicit unit number.  Try opening /dev/[dev]n
             * where n = [0, 255].
             */

            if (!tun_name_is_fixed(dev))
            {
                for (int i = 0; i < 256; ++i)
                {
                    snprintf(tunname, sizeof(tunname),
                             "/dev/%s%d", dev, i);
                    snprintf(dynamic_name, sizeof(dynamic_name),
                             "%s%d", dev, i);
                    if ((tt->fd = open(tunname, O_RDWR)) > 0)
                    {
                        dynamic_opened = true;
                        break;
                    }
                    msg(D_READ_WRITE | M_ERRNO, "Tried opening %s (failed)", tunname);
                }
                if (!dynamic_opened)
                {
                    msg(M_FATAL, "Cannot allocate TUN/TAP dev dynamically");
                }
            }
            /*
             * explicit unit number specified
             */
            else
            {
                snprintf(tunname, sizeof(tunname), "/dev/%s", dev);
            }
        }

        if (!dynamic_opened)
        {
            /* has named device existed before? if so, don't destroy at end */
            if (if_nametoindex( dev ) > 0)
            {
                msg(M_INFO, "TUN/TAP device %s exists previously, keep at program end", dev );
                tt->persistent_if = true;
            }

            if ((tt->fd = open(tunname, O_RDWR)) < 0)
            {
                msg(M_ERR, "Cannot open TUN/TAP dev %s", tunname);
            }
        }

        set_nonblock(tt->fd);
        set_cloexec(tt->fd); /* don't pass fd to scripts */
        msg(M_INFO, "TUN/TAP device %s opened", tunname);

        /* tt->actual_name is passed to up and down scripts and used as the ifconfig dev name */
        tt->actual_name = string_alloc(dynamic_opened ? dynamic_name : dev, NULL);
    }
}


static void
close_tun_generic(struct tuntap *tt)
{
    if (tt->fd >= 0)
    {
        close(tt->fd);
    }

    free(tt->actual_name);
    clear_tuntap(tt);
}


void
open_tun(const char *dev, const char *dev_type, const char *dev_node, struct tuntap *tt,
         openvpn_net_ctx_t *ctx)
{
    open_tun_generic(dev, dev_type, dev_node, tt);
}

void
close_tun(struct tuntap *tt, openvpn_net_ctx_t *ctx)
{
    ASSERT(tt);

    close_tun_generic(tt);
    free(tt);
}

int
write_tun(struct tuntap *tt, uint8_t *buf, int len)
{
    return write(tt->fd, buf, len);
}

int
read_tun(struct tuntap *tt, uint8_t *buf, int len)
{
    return read(tt->fd, buf, len);
}
