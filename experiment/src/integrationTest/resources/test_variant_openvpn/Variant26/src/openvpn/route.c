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
 * Support routines for adding/deleting network routes.
 */
#include <stddef.h>


#include "syshead.h"

#include "common.h"
#include "error.h"
#include "route.h"
#include "run_command.h"
#include "socket.h"
#include "manage.h"
#include "win32.h"
#include "options.h"
#include "networking.h"
#include "integer.h"

#include "memdbg.h"




static void delete_route(struct route_ipv4 *r, const struct tuntap *tt, unsigned int flags, const struct route_gateway_info *rgi, const struct env_set *es, openvpn_net_ctx_t *ctx);

static void get_bypass_addresses(struct route_bypass *rb, const unsigned int flags);


/* Route addition return status codes */
#define RTA_ERROR   0   /* route addition failed */
#define RTA_SUCCESS 1   /* route addition succeeded */
#define RTA_EEXIST  2   /* route not added as it already exists */

static bool
add_bypass_address(struct route_bypass *rb, const in_addr_t a)
{
    int i;
    for (i = 0; i < rb->n_bypass; ++i)
    {
        if (a == rb->bypass[i]) /* avoid duplicates */
        {
            return true;
        }
    }
    if (rb->n_bypass < N_ROUTE_BYPASS)
    {
        rb->bypass[rb->n_bypass++] = a;
        return true;
    }
    else
    {
        return false;
    }
}

struct route_option_list *
new_route_option_list(struct gc_arena *a)
{
    struct route_option_list *ret;
    ALLOC_OBJ_CLEAR_GC(ret, struct route_option_list, a);
    ret->gc = a;
    return ret;
}

struct route_ipv6_option_list *
new_route_ipv6_option_list(struct gc_arena *a)
{
    struct route_ipv6_option_list *ret;
    ALLOC_OBJ_CLEAR_GC(ret, struct route_ipv6_option_list, a);
    ret->gc = a;
    return ret;
}

/*
 * NOTE: structs are cloned/copied shallow by design.
 * The routes list from src will stay intact since it is allocated using
 * the options->gc. The cloned/copied lists will share this common tail
 * to avoid copying the data around between pulls. Pulled routes use
 * the c2->gc so they get freed immediately after a reconnect.
 */
struct route_option_list *
clone_route_option_list(const struct route_option_list *src, struct gc_arena *a)
{
    struct route_option_list *ret;
    ALLOC_OBJ_GC(ret, struct route_option_list, a);
    *ret = *src;
    return ret;
}

struct route_ipv6_option_list *
clone_route_ipv6_option_list(const struct route_ipv6_option_list *src, struct gc_arena *a)
{
    struct route_ipv6_option_list *ret;
    ALLOC_OBJ_GC(ret, struct route_ipv6_option_list, a);
    *ret = *src;
    return ret;
}

void
copy_route_option_list(struct route_option_list *dest, const struct route_option_list *src, struct gc_arena *a)
{
    *dest = *src;
    dest->gc = a;
}

void
copy_route_ipv6_option_list(struct route_ipv6_option_list *dest,
                            const struct route_ipv6_option_list *src,
                            struct gc_arena *a)
{
    *dest = *src;
    dest->gc = a;
}

static const char *
route_string(const struct route_ipv4 *r, struct gc_arena *gc)
{
    struct buffer out = alloc_buf_gc(256, gc);
    buf_printf(&out, "ROUTE network %s netmask %s gateway %s",
               print_in_addr_t(r->network, 0, gc),
               print_in_addr_t(r->netmask, 0, gc),
               print_in_addr_t(r->gateway, 0, gc)
               );
    if (r->flags & RT_METRIC_DEFINED)
    {
        buf_printf(&out, " metric %d", r->metric);
    }
    return BSTR(&out);
}

static bool
is_route_parm_defined(const char *parm)
{
    if (!parm)
    {
        return false;
    }
    if (!strcmp(parm, "default"))
    {
        return false;
    }
    return true;
}

static void
setenv_route_addr(struct env_set *es, const char *key, const in_addr_t addr, int i)
{
    struct gc_arena gc = gc_new();
    struct buffer name = alloc_buf_gc(256, &gc);
    if (i >= 0)
    {
        buf_printf(&name, "route_%s_%d", key, i);
    }
    else
    {
        buf_printf(&name, "route_%s", key);
    }
    setenv_str(es, BSTR(&name), print_in_addr_t(addr, 0, &gc));
    gc_free(&gc);
}

static bool
get_special_addr(const struct route_list *rl,
                 const char *string,
                 in_addr_t *out,
                 bool *status)
{
    if (status)
    {
        *status = true;
    }
    if (!strcmp(string, "vpn_gateway"))
    {
        if (rl)
        {
            if (rl->spec.flags & RTSA_REMOTE_ENDPOINT)
            {
                *out = rl->spec.remote_endpoint;
            }
            else
            {
                msg(M_INFO, PACKAGE_NAME " ROUTE: vpn_gateway undefined");
                if (status)
                {
                    *status = false;
                }
            }
        }
        return true;
    }
    else if (!strcmp(string, "net_gateway"))
    {
        if (rl)
        {
            if (rl->rgi.flags & RGI_ADDR_DEFINED)
            {
                *out = rl->rgi.gateway.addr;
            }
            else
            {
                msg(M_INFO, PACKAGE_NAME " ROUTE: net_gateway undefined -- unable to get default gateway from system");
                if (status)
                {
                    *status = false;
                }
            }
        }
        return true;
    }
    else if (!strcmp(string, "remote_host"))
    {
        if (rl)
        {
            if (rl->spec.flags & RTSA_REMOTE_HOST)
            {
                *out = rl->spec.remote_host;
            }
            else
            {
                msg(M_INFO, PACKAGE_NAME " ROUTE: remote_host undefined");
                if (status)
                {
                    *status = false;
                }
            }
        }
        return true;
    }
    return false;
}

bool
is_special_addr(const char *addr_str)
{
    if (addr_str)
    {
        return get_special_addr(NULL, addr_str, NULL, NULL);
    }
    else
    {
        return false;
    }
}

static bool
init_route(struct route_ipv4 *r,
           struct addrinfo **network_list,
           const struct route_option *ro,
           const struct route_list *rl)
{
    const in_addr_t default_netmask = IPV4_NETMASK_HOST;
    bool status;
    int ret;
    struct in_addr special = {0};

    CLEAR(*r);
    r->option = ro;

    /* network */

    if (!is_route_parm_defined(ro->network))
    {
        goto fail;
    }


    /* get_special_addr replaces specialaddr with a special ip addr
     * like gw. getaddrinfo is called to convert a a addrinfo struct */

    if (get_special_addr(rl, ro->network, (in_addr_t *) &special.s_addr, &status))
    {
        if (!status)
        {
            goto fail;
        }
        special.s_addr = htonl(special.s_addr);
        char buf[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &special, buf, sizeof(buf));
        ret = openvpn_getaddrinfo(0, buf, NULL, 0, NULL,
                                  AF_INET, network_list);
    }
    else
    {
        ret = openvpn_getaddrinfo(GETADDR_RESOLVE | GETADDR_WARN_ON_SIGNAL,
                                  ro->network, NULL, 0, NULL, AF_INET, network_list);
    }

    status = (ret == 0);

    if (!status)
    {
        goto fail;
    }

    /* netmask */

    if (is_route_parm_defined(ro->netmask))
    {
        r->netmask = getaddr(
            GETADDR_HOST_ORDER
            | GETADDR_WARN_ON_SIGNAL,
            ro->netmask,
            0,
            &status,
            NULL);
        if (!status)
        {
            goto fail;
        }
    }
    else
    {
        r->netmask = default_netmask;
    }

    /* gateway */

    if (is_route_parm_defined(ro->gateway))
    {
        if (!get_special_addr(rl, ro->gateway, &r->gateway, &status))
        {
            r->gateway = getaddr(
                GETADDR_RESOLVE
                | GETADDR_HOST_ORDER
                | GETADDR_WARN_ON_SIGNAL,
                ro->gateway,
                0,
                &status,
                NULL);
        }
        if (!status)
        {
            goto fail;
        }
    }
    else
    {
        if (rl->spec.flags & RTSA_REMOTE_ENDPOINT)
        {
            r->gateway = rl->spec.remote_endpoint;
        }
        else
        {
            msg(M_WARN, PACKAGE_NAME " ROUTE: " PACKAGE_NAME " needs a gateway parameter for a --route option and no default was specified by either --route-gateway or --ifconfig options");
            goto fail;
        }
    }

    /* metric */

    r->metric = 0;
    if (is_route_parm_defined(ro->metric))
    {
        r->metric = atoi(ro->metric);
        if (r->metric < 0)
        {
            msg(M_WARN, PACKAGE_NAME " ROUTE: route metric for network %s (%s) must be >= 0",
                ro->network,
                ro->metric);
            goto fail;
        }
        r->flags |= RT_METRIC_DEFINED;
    }
    else if (rl->spec.flags & RTSA_DEFAULT_METRIC)
    {
        r->metric = rl->spec.default_metric;
        r->flags |= RT_METRIC_DEFINED;
    }

    r->flags |= RT_DEFINED;

    return true;

fail:
    msg(M_WARN, PACKAGE_NAME " ROUTE: failed to parse/resolve route for host/network: %s",
        ro->network);
    return false;
}

static bool
init_route_ipv6(struct route_ipv6 *r6,
                const struct route_ipv6_option *r6o,
                const struct route_ipv6_list *rl6 )
{
    CLEAR(*r6);

    if (!get_ipv6_addr( r6o->prefix, &r6->network, &r6->netbits, M_WARN ))
    {
        goto fail;
    }

    /* gateway */
    if (is_route_parm_defined(r6o->gateway))
    {
        if (inet_pton( AF_INET6, r6o->gateway, &r6->gateway ) != 1)
        {
            msg( M_WARN, PACKAGE_NAME "ROUTE6: cannot parse gateway spec '%s'", r6o->gateway );
        }
    }
    else if (rl6->spec_flags & RTSA_REMOTE_ENDPOINT)
    {
        r6->gateway = rl6->remote_endpoint_ipv6;
    }

    /* metric */

    r6->metric = -1;
    if (is_route_parm_defined(r6o->metric))
    {
        r6->metric = atoi(r6o->metric);
        if (r6->metric < 0)
        {
            msg(M_WARN, PACKAGE_NAME " ROUTE: route metric for network %s (%s) must be >= 0",
                r6o->prefix,
                r6o->metric);
            goto fail;
        }
        r6->flags |= RT_METRIC_DEFINED;
    }
    else if (rl6->spec_flags & RTSA_DEFAULT_METRIC)
    {
        r6->metric = rl6->default_metric;
        r6->flags |= RT_METRIC_DEFINED;
    }

    r6->flags |= RT_DEFINED;

    return true;

fail:
    msg(M_WARN, PACKAGE_NAME " ROUTE: failed to parse/resolve route for host/network: %s",
        r6o->prefix);
    return false;
}

void
add_route_to_option_list(struct route_option_list *l,
                         const char *network,
                         const char *netmask,
                         const char *gateway,
                         const char *metric)
{
    struct route_option *ro;
    ALLOC_OBJ_GC(ro, struct route_option, l->gc);
    ro->network = network;
    ro->netmask = netmask;
    ro->gateway = gateway;
    ro->metric = metric;
    ro->next = l->routes;
    l->routes = ro;

}

void
add_route_ipv6_to_option_list(struct route_ipv6_option_list *l,
                              const char *prefix,
                              const char *gateway,
                              const char *metric)
{
    struct route_ipv6_option *ro;
    ALLOC_OBJ_GC(ro, struct route_ipv6_option, l->gc);
    ro->prefix = prefix;
    ro->gateway = gateway;
    ro->metric = metric;
    ro->next = l->routes_ipv6;
    l->routes_ipv6 = ro;
}

static void
clear_route_list(struct route_list *rl)
{
    gc_free(&rl->gc);
    CLEAR(*rl);
}

static void
clear_route_ipv6_list(struct route_ipv6_list *rl6)
{
    gc_free(&rl6->gc);
    CLEAR(*rl6);
}

void
route_list_add_vpn_gateway(struct route_list *rl,
                           struct env_set *es,
                           const in_addr_t addr)
{
    ASSERT(rl);
    rl->spec.remote_endpoint = addr;
    rl->spec.flags |= RTSA_REMOTE_ENDPOINT;
    setenv_route_addr(es, "vpn_gateway", rl->spec.remote_endpoint, -1);
}

static void
add_block_local_item(struct route_list *rl,
                     const struct route_gateway_address *gateway,
                     in_addr_t target)
{
    const int rgi_needed = (RGI_ADDR_DEFINED|RGI_NETMASK_DEFINED);
    if ((rl->rgi.flags & rgi_needed) == rgi_needed
        && rl->rgi.gateway.netmask < 0xFFFFFFFF)
    {
        struct route_ipv4 *r1, *r2;
        unsigned int l2;

        ALLOC_OBJ_GC(r1, struct route_ipv4, &rl->gc);
        ALLOC_OBJ_GC(r2, struct route_ipv4, &rl->gc);

        /* split a route into two smaller blocking routes, and direct them to target */
        l2 = ((~gateway->netmask)+1)>>1;
        r1->flags = RT_DEFINED;
        r1->gateway = target;
        r1->network = gateway->addr & gateway->netmask;
        r1->netmask = ~(l2-1);
        r1->next = rl->routes;
        rl->routes = r1;

        *r2 = *r1;
        r2->network += l2;
        r2->next = rl->routes;
        rl->routes = r2;
    }
}

static void
add_block_local(struct route_list *rl)
{
    const int rgi_needed = (RGI_ADDR_DEFINED|RGI_NETMASK_DEFINED);
    if ((rl->flags & RG_BLOCK_LOCAL)
        && (rl->rgi.flags & rgi_needed) == rgi_needed
        && (rl->spec.flags & RTSA_REMOTE_ENDPOINT)
        && rl->spec.remote_host_local != TLA_LOCAL)
    {
        size_t i;

        /* add bypass for gateway addr */
        add_bypass_address(&rl->spec.bypass, rl->rgi.gateway.addr);

        /* block access to local subnet */
        add_block_local_item(rl, &rl->rgi.gateway, rl->spec.remote_endpoint);

        /* process additional subnets on gateway interface */
        for (i = 0; i < rl->rgi.n_addrs; ++i)
        {
            const struct route_gateway_address *gwa = &rl->rgi.addrs[i];
            /* omit the add/subnet in &rl->rgi which we processed above */
            if (!((rl->rgi.gateway.addr & rl->rgi.gateway.netmask) == (gwa->addr & gwa->netmask)
                  && rl->rgi.gateway.netmask == gwa->netmask))
            {
                add_block_local_item(rl, gwa, rl->spec.remote_endpoint);
            }
        }
    }
}

bool
init_route_list(struct route_list *rl,
                const struct route_option_list *opt,
                const char *remote_endpoint,
                int default_metric,
                in_addr_t remote_host,
                struct env_set *es,
                openvpn_net_ctx_t *ctx)
{
    struct gc_arena gc = gc_new();
    bool ret = true;

    clear_route_list(rl);

    rl->flags = opt->flags;

    if (remote_host != IPV4_INVALID_ADDR)
    {
        rl->spec.remote_host = remote_host;
        rl->spec.flags |= RTSA_REMOTE_HOST;
    }

    if (default_metric)
    {
        rl->spec.default_metric = default_metric;
        rl->spec.flags |= RTSA_DEFAULT_METRIC;
    }

    get_default_gateway(&rl->rgi, ctx);
    if (rl->rgi.flags & RGI_ADDR_DEFINED)
    {
        setenv_route_addr(es, "net_gateway", rl->rgi.gateway.addr, -1);
    }
    else
    {
        dmsg(D_ROUTE, "ROUTE: default_gateway=UNDEF");
    }

    if (rl->spec.flags & RTSA_REMOTE_HOST)
    {
        rl->spec.remote_host_local = test_local_addr(remote_host, &rl->rgi);
    }

    if (is_route_parm_defined(remote_endpoint))
    {
        bool defined = false;
        rl->spec.remote_endpoint = getaddr(
            GETADDR_RESOLVE
            | GETADDR_HOST_ORDER
            | GETADDR_WARN_ON_SIGNAL,
            remote_endpoint,
            0,
            &defined,
            NULL);

        if (defined)
        {
            setenv_route_addr(es, "vpn_gateway", rl->spec.remote_endpoint, -1);
            rl->spec.flags |= RTSA_REMOTE_ENDPOINT;
        }
        else
        {
            msg(M_WARN, PACKAGE_NAME " ROUTE: failed to parse/resolve default gateway: %s",
                remote_endpoint);
            ret = false;
        }
    }

    if (rl->flags & RG_ENABLE)
    {
        add_block_local(rl);
        get_bypass_addresses(&rl->spec.bypass, rl->flags);
    }

    /* parse the routes from opt to rl */
    {
        struct route_option *ro;
        for (ro = opt->routes; ro; ro = ro->next)
        {
            struct addrinfo *netlist = NULL;
            struct route_ipv4 r;

            if (!init_route(&r, &netlist, ro, rl))
            {
                ret = false;
            }
            else
            {
                struct addrinfo *curele;
                for (curele = netlist; curele; curele = curele->ai_next)
                {
                    struct route_ipv4 *new;
                    ALLOC_OBJ_GC(new, struct route_ipv4, &rl->gc);
                    *new = r;
                    new->network = ntohl(((struct sockaddr_in *)curele->ai_addr)->sin_addr.s_addr);
                    new->next = rl->routes;
                    rl->routes = new;
                }
            }
            if (netlist)
            {
                gc_addspecial(netlist, &gc_freeaddrinfo_callback, &gc);
            }
        }
    }

    gc_free(&gc);
    return ret;
}

/* check whether an IPv6 host address is covered by a given route_ipv6
 * (not the most beautiful implementation in the world, but portable and
 * "good enough")
 */
static bool
route_ipv6_match_host( const struct route_ipv6 *r6,
                       const struct in6_addr *host )
{
    unsigned int bits = r6->netbits;
    int i;
    unsigned int mask;

    if (bits>128)
    {
        return false;
    }

    for (i = 0; bits >= 8; i++, bits -= 8)
    {
        if (r6->network.s6_addr[i] != host->s6_addr[i])
        {
            return false;
        }
    }

    if (bits == 0)
    {
        return true;
    }

    mask = 0xff << (8-bits);

    if ( (r6->network.s6_addr[i] & mask) == (host->s6_addr[i] & mask ))
    {
        return true;
    }

    return false;
}

bool
init_route_ipv6_list(struct route_ipv6_list *rl6,
                     const struct route_ipv6_option_list *opt6,
                     const char *remote_endpoint,
                     int default_metric,
                     const struct in6_addr *remote_host_ipv6,
                     struct env_set *es,
                     openvpn_net_ctx_t *ctx)
{
    struct gc_arena gc = gc_new();
    bool ret = true;
    bool need_remote_ipv6_route;

    clear_route_ipv6_list(rl6);

    rl6->flags = opt6->flags;

    if (remote_host_ipv6)
    {
        rl6->remote_host_ipv6 = *remote_host_ipv6;
        rl6->spec_flags |= RTSA_REMOTE_HOST;
    }

    if (default_metric >= 0)
    {
        rl6->default_metric = default_metric;
        rl6->spec_flags |= RTSA_DEFAULT_METRIC;
    }

    msg(D_ROUTE, "GDG6: remote_host_ipv6=%s",
        remote_host_ipv6 ?  print_in6_addr(*remote_host_ipv6, 0, &gc) : "n/a" );

    get_default_gateway_ipv6(&rl6->rgi6, remote_host_ipv6, ctx);
    if (rl6->rgi6.flags & RGI_ADDR_DEFINED)
    {
        setenv_str(es, "net_gateway_ipv6", print_in6_addr(rl6->rgi6.gateway.addr_ipv6, 0, &gc));
    }
    else
    {
        dmsg(D_ROUTE, "ROUTE6: default_gateway=UNDEF");
    }

    if (is_route_parm_defined( remote_endpoint ))
    {
        if (inet_pton( AF_INET6, remote_endpoint,
                       &rl6->remote_endpoint_ipv6) == 1)
        {
            rl6->spec_flags |= RTSA_REMOTE_ENDPOINT;
        }
        else
        {
            msg(M_WARN, PACKAGE_NAME " ROUTE: failed to parse/resolve VPN endpoint: %s", remote_endpoint);
            ret = false;
        }
    }

    /* parse the routes from opt6 to rl6
     * discovering potential overlaps with remote_host_ipv6 in the process
     */
    need_remote_ipv6_route = false;

    {
        struct route_ipv6_option *ro6;
        for (ro6 = opt6->routes_ipv6; ro6; ro6 = ro6->next)
        {
            struct route_ipv6 *r6;
            ALLOC_OBJ_GC(r6, struct route_ipv6, &rl6->gc);
            if (!init_route_ipv6(r6, ro6, rl6))
            {
                ret = false;
            }
            else
            {
                r6->next = rl6->routes_ipv6;
                rl6->routes_ipv6 = r6;

                /* On Android the VPNService protect function call will take of
                 * avoiding routing loops, so ignore this part and let
                 * need_remote_ipv6_route always evaluate to false
                 */
                if (remote_host_ipv6
                    && route_ipv6_match_host( r6, remote_host_ipv6 ) )
                {
                    need_remote_ipv6_route = true;
                    msg(D_ROUTE, "ROUTE6: %s/%d overlaps IPv6 remote %s, adding host route to VPN endpoint",
                        print_in6_addr(r6->network, 0, &gc), r6->netbits,
                        print_in6_addr(*remote_host_ipv6, 0, &gc));
                }
            }
        }
    }

    /* add VPN server host route if needed */
    if (need_remote_ipv6_route)
    {
        if ( (rl6->rgi6.flags & (RGI_ADDR_DEFINED|RGI_IFACE_DEFINED) ) ==
             (RGI_ADDR_DEFINED|RGI_IFACE_DEFINED) )
        {
            struct route_ipv6 *r6;
            ALLOC_OBJ_CLEAR_GC(r6, struct route_ipv6, &rl6->gc);

            r6->network = *remote_host_ipv6;
            r6->netbits = 128;
            if (!(rl6->rgi6.flags & RGI_ON_LINK) )
            {
                r6->gateway = rl6->rgi6.gateway.addr_ipv6;
            }
            r6->metric = 1;
            r6->iface = rl6->rgi6.iface;
            r6->flags = RT_DEFINED | RT_METRIC_DEFINED;

            r6->next = rl6->routes_ipv6;
            rl6->routes_ipv6 = r6;
        }
        else
        {
            msg(M_WARN, "ROUTE6: IPv6 route overlaps with IPv6 remote address, but could not determine IPv6 gateway address + interface, expect failure\n" );
        }
    }

    gc_free(&gc);
    return ret;
}

static bool
add_route3(in_addr_t network,
           in_addr_t netmask,
           in_addr_t gateway,
           const struct tuntap *tt,
           unsigned int flags,
           const struct route_gateway_info *rgi,
           const struct env_set *es,
           openvpn_net_ctx_t *ctx)
{
    struct route_ipv4 r;
    CLEAR(r);
    r.flags = RT_DEFINED;
    r.network = network;
    r.netmask = netmask;
    r.gateway = gateway;
    return add_route(&r, tt, flags, rgi, es, ctx);
}

static void
del_route3(in_addr_t network,
           in_addr_t netmask,
           in_addr_t gateway,
           const struct tuntap *tt,
           unsigned int flags,
           const struct route_gateway_info *rgi,
           const struct env_set *es,
           openvpn_net_ctx_t *ctx)
{
    struct route_ipv4 r;
    CLEAR(r);
    r.flags = RT_DEFINED|RT_ADDED;
    r.network = network;
    r.netmask = netmask;
    r.gateway = gateway;
    delete_route(&r, tt, flags, rgi, es, ctx);
}

static bool
add_bypass_routes(struct route_bypass *rb,
                  in_addr_t gateway,
                  const struct tuntap *tt,
                  unsigned int flags,
                  const struct route_gateway_info *rgi,
                  const struct env_set *es,
                  openvpn_net_ctx_t *ctx)
{
    int ret = true;
    for (int i = 0; i < rb->n_bypass; ++i)
    {
        if (rb->bypass[i])
        {
            ret = add_route3(rb->bypass[i], IPV4_NETMASK_HOST, gateway, tt,
                             flags | ROUTE_REF_GW, rgi, es, ctx) && ret;
        }
    }
    return ret;
}

static void
del_bypass_routes(struct route_bypass *rb,
                  in_addr_t gateway,
                  const struct tuntap *tt,
                  unsigned int flags,
                  const struct route_gateway_info *rgi,
                  const struct env_set *es,
                  openvpn_net_ctx_t *ctx)
{
    int i;
    for (i = 0; i < rb->n_bypass; ++i)
    {
        if (rb->bypass[i])
        {
            del_route3(rb->bypass[i],
                       IPV4_NETMASK_HOST,
                       gateway,
                       tt,
                       flags | ROUTE_REF_GW,
                       rgi,
                       es,
                       ctx);
        }
    }
}

static bool
redirect_default_route_to_vpn(struct route_list *rl, const struct tuntap *tt,
                              unsigned int flags, const struct env_set *es,
                              openvpn_net_ctx_t *ctx)
{
    const char err[] = "NOTE: unable to redirect IPv4 default gateway --";
    bool ret = true;

    if (rl && rl->flags & RG_ENABLE)
    {
        bool local = rl->flags & RG_LOCAL;

        if (!(rl->spec.flags & RTSA_REMOTE_ENDPOINT) && (rl->flags & RG_REROUTE_GW))
        {
            msg(M_WARN, "%s VPN gateway parameter (--route-gateway or --ifconfig) is missing", err);
            ret = false;
        }
        /*
         * check if a default route is defined, unless:
         * - we are connecting to a remote host in our network
         * - we are connecting to a non-IPv4 remote host (i.e. we use IPv6)
         */
        else if (!(rl->rgi.flags & RGI_ADDR_DEFINED) && !local
                 && (rl->spec.flags & RTSA_REMOTE_HOST))
        {
            msg(M_WARN, "%s Cannot read current default gateway from system", err);
            ret = false;
        }
        else
        {
            if (rl->flags & RG_AUTO_LOCAL)
            {
                const int tla = rl->spec.remote_host_local;
                if (tla == TLA_NONLOCAL)
                {
                    dmsg(D_ROUTE, "ROUTE remote_host is NOT LOCAL");
                    local = false;
                }
                else if (tla == TLA_LOCAL)
                {
                    dmsg(D_ROUTE, "ROUTE remote_host is LOCAL");
                    local = true;
                }
            }
            if (!local)
            {
                /* route remote host to original default gateway */
                /* if remote_host is not ipv4 (ie: ipv6), just skip
                 * adding this special /32 route */
                if ((rl->spec.flags & RTSA_REMOTE_HOST)
                    && rl->spec.remote_host != IPV4_INVALID_ADDR)
                {
                    ret = add_route3(rl->spec.remote_host, IPV4_NETMASK_HOST,
                                     rl->rgi.gateway.addr, tt, flags | ROUTE_REF_GW,
                                     &rl->rgi, es, ctx);
                    rl->iflags |= RL_DID_LOCAL;
                }
                else
                {
                    dmsg(D_ROUTE, "ROUTE remote_host protocol differs from tunneled");
                }
            }

            /* route DHCP/DNS server traffic through original default gateway */
            ret = add_bypass_routes(&rl->spec.bypass, rl->rgi.gateway.addr, tt, flags,
                                    &rl->rgi, es, ctx) && ret;

            if (rl->flags & RG_REROUTE_GW)
            {
                if (rl->flags & RG_DEF1)
                {
                    /* add new default route (1st component) */
                    ret = add_route3(0x00000000, 0x80000000, rl->spec.remote_endpoint,
                                     tt, flags, &rl->rgi, es, ctx) && ret;

                    /* add new default route (2nd component) */
                    ret = add_route3(0x80000000, 0x80000000, rl->spec.remote_endpoint,
                                     tt, flags, &rl->rgi, es, ctx) && ret;
                }
                else
                {
                    /* don't try to remove the def route if it does not exist */
                    if (rl->rgi.flags & RGI_ADDR_DEFINED)
                    {
                        /* delete default route */
                        del_route3(0, 0, rl->rgi.gateway.addr, tt,
                                   flags | ROUTE_REF_GW, &rl->rgi, es, ctx);
                    }

                    /* add new default route */
                    ret = add_route3(0, 0, rl->spec.remote_endpoint, tt,
                                     flags, &rl->rgi, es, ctx) && ret;
                }
            }

            /* set a flag so we can undo later */
            rl->iflags |= RL_DID_REDIRECT_DEFAULT_GATEWAY;
        }
    }
    return ret;
}

static void
undo_redirect_default_route_to_vpn(struct route_list *rl,
                                   const struct tuntap *tt, unsigned int flags,
                                   const struct env_set *es,
                                   openvpn_net_ctx_t *ctx)
{
    if (rl && rl->iflags & RL_DID_REDIRECT_DEFAULT_GATEWAY)
    {
        /* delete remote host route */
        if (rl->iflags & RL_DID_LOCAL)
        {
            del_route3(rl->spec.remote_host,
                       IPV4_NETMASK_HOST,
                       rl->rgi.gateway.addr,
                       tt,
                       flags | ROUTE_REF_GW,
                       &rl->rgi,
                       es,
                       ctx);
            rl->iflags &= ~RL_DID_LOCAL;
        }

        /* delete special DHCP/DNS bypass route */
        del_bypass_routes(&rl->spec.bypass, rl->rgi.gateway.addr, tt, flags,
                          &rl->rgi, es, ctx);

        if (rl->flags & RG_REROUTE_GW)
        {
            if (rl->flags & RG_DEF1)
            {
                /* delete default route (1st component) */
                del_route3(0x00000000,
                           0x80000000,
                           rl->spec.remote_endpoint,
                           tt,
                           flags,
                           &rl->rgi,
                           es,
                           ctx);

                /* delete default route (2nd component) */
                del_route3(0x80000000,
                           0x80000000,
                           rl->spec.remote_endpoint,
                           tt,
                           flags,
                           &rl->rgi,
                           es,
                           ctx);
            }
            else
            {
                /* delete default route */
                del_route3(0,
                           0,
                           rl->spec.remote_endpoint,
                           tt,
                           flags,
                           &rl->rgi,
                           es,
                           ctx);
                /* restore original default route if there was any */
                if (rl->rgi.flags & RGI_ADDR_DEFINED)
                {
                    add_route3(0, 0, rl->rgi.gateway.addr, tt,
                               flags | ROUTE_REF_GW, &rl->rgi, es, ctx);
                }
            }
        }

        rl->iflags &= ~RL_DID_REDIRECT_DEFAULT_GATEWAY;
    }
}

bool
add_routes(struct route_list *rl, struct route_ipv6_list *rl6,
           const struct tuntap *tt, unsigned int flags,
           const struct env_set *es, openvpn_net_ctx_t *ctx)
{
    bool ret = redirect_default_route_to_vpn(rl, tt, flags, es, ctx);
    if (rl && !(rl->iflags & RL_ROUTES_ADDED) )
    {
        struct route_ipv4 *r;

        if (rl->routes && !tt->did_ifconfig_setup)
        {
            msg(M_INFO, "WARNING: OpenVPN was configured to add an IPv4 "
                "route. However, no IPv4 has been configured for %s, "
                "therefore the route installation may fail or may not work "
                "as expected.", tt->actual_name);
        }


        for (r = rl->routes; r; r = r->next)
        {
            check_subnet_conflict(r->network, r->netmask, "route");
            if (flags & ROUTE_DELETE_FIRST)
            {
                delete_route(r, tt, flags, &rl->rgi, es, ctx);
            }
            ret = add_route(r, tt, flags, &rl->rgi, es, ctx) && ret;
        }
        rl->iflags |= RL_ROUTES_ADDED;
    }
    if (rl6 && !(rl6->iflags & RL_ROUTES_ADDED) )
    {
        struct route_ipv6 *r;

        if (!tt->did_ifconfig_ipv6_setup)
        {
            msg(M_INFO, "WARNING: OpenVPN was configured to add an IPv6 "
                "route. However, no IPv6 has been configured for %s, "
                "therefore the route installation may fail or may not work "
                "as expected.", tt->actual_name);
        }

        for (r = rl6->routes_ipv6; r; r = r->next)
        {
            if (flags & ROUTE_DELETE_FIRST)
            {
                delete_route_ipv6(r, tt, flags, es, ctx);
            }
            ret = add_route_ipv6(r, tt, flags, es, ctx) && ret;
        }
        rl6->iflags |= RL_ROUTES_ADDED;
    }
    return ret;
}

void
delete_routes(struct route_list *rl, struct route_ipv6_list *rl6,
              const struct tuntap *tt, unsigned int flags,
              const struct env_set *es, openvpn_net_ctx_t *ctx)
{
    if (rl && rl->iflags & RL_ROUTES_ADDED)
    {
        struct route_ipv4 *r;
        for (r = rl->routes; r; r = r->next)
        {
            delete_route(r, tt, flags, &rl->rgi, es, ctx);
        }
        rl->iflags &= ~RL_ROUTES_ADDED;
    }

    undo_redirect_default_route_to_vpn(rl, tt, flags, es, ctx);

    if (rl)
    {
        clear_route_list(rl);
    }

    if (rl6 && (rl6->iflags & RL_ROUTES_ADDED) )
    {
        struct route_ipv6 *r6;
        for (r6 = rl6->routes_ipv6; r6; r6 = r6->next)
        {
            delete_route_ipv6(r6, tt, flags, es, ctx);
        }
        rl6->iflags &= ~RL_ROUTES_ADDED;
    }

    if (rl6)
    {
        clear_route_ipv6_list(rl6);
    }
}


static const char *
show_opt(const char *option)
{
    if (!option)
    {
        return "default (not set)";
    }
    else
    {
        return option;
    }
}

static void
print_route_option(const struct route_option *ro, int level)
{
    msg(level, "  route %s/%s/%s/%s",
        show_opt(ro->network),
        show_opt(ro->netmask),
        show_opt(ro->gateway),
        show_opt(ro->metric));
}

void
print_route_options(const struct route_option_list *rol,
                    int level)
{
    struct route_option *ro;
    if (rol->flags & RG_ENABLE)
    {
        msg(level, "  [redirect_default_gateway local=%d]",
            (rol->flags & RG_LOCAL) != 0);
    }
    for (ro = rol->routes; ro; ro = ro->next)
    {
        print_route_option(ro, level);
    }
}

void
print_default_gateway(const int msglevel,
                      const struct route_gateway_info *rgi,
                      const struct route_ipv6_gateway_info *rgi6)
{
    struct gc_arena gc = gc_new();
    if (rgi && (rgi->flags & RGI_ADDR_DEFINED))
    {
        struct buffer out = alloc_buf_gc(256, &gc);
        buf_printf(&out, "ROUTE_GATEWAY");
        if (rgi->flags & RGI_ON_LINK)
        {
            buf_printf(&out, " ON_LINK");
        }
        else
        {
            buf_printf(&out, " %s", print_in_addr_t(rgi->gateway.addr, 0, &gc));
        }
        if (rgi->flags & RGI_NETMASK_DEFINED)
        {
            buf_printf(&out, "/%s", print_in_addr_t(rgi->gateway.netmask, 0, &gc));
        }
        if (rgi->flags & RGI_IFACE_DEFINED)
        {
            buf_printf(&out, " IFACE=%s", rgi->iface);
        }
        if (rgi->flags & RGI_HWADDR_DEFINED)
        {
            buf_printf(&out, " HWADDR=%s", format_hex_ex(rgi->hwaddr, 6, 0, 1, ":", &gc));
        }
        msg(msglevel, "%s", BSTR(&out));
    }

    if (rgi6 && (rgi6->flags & RGI_ADDR_DEFINED))
    {
        struct buffer out = alloc_buf_gc(256, &gc);
        buf_printf(&out, "ROUTE6_GATEWAY");
        buf_printf(&out, " %s", print_in6_addr(rgi6->gateway.addr_ipv6, 0, &gc));
        if (rgi6->flags & RGI_ON_LINK)
        {
            buf_printf(&out, " ON_LINK");
        }
        if (rgi6->flags & RGI_NETMASK_DEFINED)
        {
            buf_printf(&out, "/%d", rgi6->gateway.netbits_ipv6);
        }
        if (rgi6->flags & RGI_IFACE_DEFINED)
        {
            buf_printf(&out, " IFACE=%s", rgi6->iface);
        }
        if (rgi6->flags & RGI_HWADDR_DEFINED)
        {
            buf_printf(&out, " HWADDR=%s", format_hex_ex(rgi6->hwaddr, 6, 0, 1, ":", &gc));
        }
        msg(msglevel, "%s", BSTR(&out));
    }
    gc_free(&gc);
}


static void
print_route(const struct route_ipv4 *r, int level)
{
    struct gc_arena gc = gc_new();
    if (r->flags & RT_DEFINED)
    {
        msg(level, "%s", route_string(r, &gc));
    }
    gc_free(&gc);
}

void
print_routes(const struct route_list *rl, int level)
{
    struct route_ipv4 *r;
    for (r = rl->routes; r; r = r->next)
    {
        print_route(r, level);
    }
}

static void
setenv_route(struct env_set *es, const struct route_ipv4 *r, int i)
{
    struct gc_arena gc = gc_new();
    if (r->flags & RT_DEFINED)
    {
        setenv_route_addr(es, "network", r->network, i);
        setenv_route_addr(es, "netmask", r->netmask, i);
        setenv_route_addr(es, "gateway", r->gateway, i);

        if (r->flags & RT_METRIC_DEFINED)
        {
            struct buffer name = alloc_buf_gc(256, &gc);
            buf_printf(&name, "route_metric_%d", i);
            setenv_int(es, BSTR(&name), r->metric);
        }
    }
    gc_free(&gc);
}

void
setenv_routes(struct env_set *es, const struct route_list *rl)
{
    int i = 1;
    struct route_ipv4 *r;
    for (r = rl->routes; r; r = r->next)
    {
        setenv_route(es, r, i++);
    }
}

static void
setenv_route_ipv6(struct env_set *es, const struct route_ipv6 *r6, int i)
{
    struct gc_arena gc = gc_new();
    if (r6->flags & RT_DEFINED)
    {
        struct buffer name1 = alloc_buf_gc( 256, &gc );
        struct buffer val = alloc_buf_gc( 256, &gc );
        struct buffer name2 = alloc_buf_gc( 256, &gc );

        buf_printf( &name1, "route_ipv6_network_%d", i );
        buf_printf( &val, "%s/%d", print_in6_addr( r6->network, 0, &gc ),
                    r6->netbits );
        setenv_str( es, BSTR(&name1), BSTR(&val) );

        buf_printf( &name2, "route_ipv6_gateway_%d", i );
        setenv_str( es, BSTR(&name2), print_in6_addr( r6->gateway, 0, &gc ));

        if (r6->flags & RT_METRIC_DEFINED)
        {
            struct buffer name3 = alloc_buf_gc( 256, &gc );
            buf_printf( &name3, "route_ipv6_metric_%d", i);
            setenv_int( es, BSTR(&name3), r6->metric);
        }
    }
    gc_free(&gc);
}
void
setenv_routes_ipv6(struct env_set *es, const struct route_ipv6_list *rl6)
{
    int i = 1;
    struct route_ipv6 *r6;
    for (r6 = rl6->routes_ipv6; r6; r6 = r6->next)
    {
        setenv_route_ipv6(es, r6, i++);
    }
}

/*
 * local_route() determines whether the gateway of a provided host
 * route is on the same interface that owns the default gateway.
 * It uses the data structure
 * returned by get_default_gateway() (struct route_gateway_info)
 * to determine this.  If the route is local, LR_MATCH is returned.
 * When adding routes into the kernel, if LR_MATCH is defined for
 * a given route, the route should explicitly reference the default
 * gateway interface as the route destination.  For example, here
 * is an example on Linux that uses LR_MATCH:
 *
 *   route add -net 10.10.0.1 netmask 255.255.255.255 dev eth0
 *
 * This capability is needed by the "default-gateway block-local"
 * directive, to allow client access to the local subnet to be
 * blocked but still allow access to the local default gateway.
 */

/* local_route() return values */
#define LR_NOMATCH 0 /* route is not local */
#define LR_MATCH   1 /* route is local */
#define LR_ERROR   2 /* caller should abort adding route */

static int
local_route(in_addr_t network,
            in_addr_t netmask,
            in_addr_t gateway,
            const struct route_gateway_info *rgi)
{
    /* set LR_MATCH on local host routes */
    const int rgi_needed = (RGI_ADDR_DEFINED|RGI_NETMASK_DEFINED|RGI_IFACE_DEFINED);
    if (rgi
        && (rgi->flags & rgi_needed) == rgi_needed
        && gateway == rgi->gateway.addr
        && netmask == 0xFFFFFFFF)
    {
        if (((network ^  rgi->gateway.addr) & rgi->gateway.netmask) == 0)
        {
            return LR_MATCH;
        }
        else
        {
            /* examine additional subnets on gateway interface */
            size_t i;
            for (i = 0; i < rgi->n_addrs; ++i)
            {
                const struct route_gateway_address *gwa = &rgi->addrs[i];
                if (((network ^ gwa->addr) & gwa->netmask) == 0)
                {
                    return LR_MATCH;
                }
            }
        }
    }
    return LR_NOMATCH;
}

/* Return true if the "on-link" form of the route should be used.  This is when the gateway for
 * a route is specified as an interface rather than an address. */

bool
add_route(struct route_ipv4 *r,
          const struct tuntap *tt,
          unsigned int flags,
          const struct route_gateway_info *rgi,  /* may be NULL */
          const struct env_set *es,
          openvpn_net_ctx_t *ctx)
{
    int status = 0;
    int is_local_route;

    if (!(r->flags & RT_DEFINED))
    {
        return true; /* no error */
    }

    struct argv argv = argv_new();
    struct gc_arena gc = gc_new();

    const char *network = print_in_addr_t(r->network, 0, &gc);
    const char *gateway = print_in_addr_t(r->gateway, 0, &gc);

    is_local_route = local_route(r->network, r->netmask, r->gateway, rgi);
    if (is_local_route == LR_ERROR)
    {
        goto done;
    }


    {
        int netbits = netmask_to_netbits2(r->netmask);
        argv_printf(&argv, "%s add -net %s/%d %s",
                    ROUTE_PATH,
                    network, netbits, gateway);
        argv_msg(D_ROUTE, &argv);
        bool ret = openvpn_execve_check(&argv, es, 0,
                                        "ERROR: AIX route add command failed");
        status = ret ? RTA_SUCCESS : RTA_ERROR;
    }


done:
    if (status == RTA_SUCCESS)
    {
        r->flags |= RT_ADDED;
    }
    else
    {
        r->flags &= ~RT_ADDED;
    }
    argv_free(&argv);
    gc_free(&gc);
    /* release resources potentially allocated during route setup */
    net_ctx_reset(ctx);

    return (status != RTA_ERROR);
}


void
route_ipv6_clear_host_bits( struct route_ipv6 *r6 )
{
    /* clear host bit parts of route
     * (needed if routes are specified improperly, or if we need to
     * explicitly setup/clear the "connected" network routes on some OSes)
     */
    int byte = 15;
    int bits_to_clear = 128 - r6->netbits;

    while (byte >= 0 && bits_to_clear > 0)
    {
        if (bits_to_clear >= 8)
        {
            r6->network.s6_addr[byte--] = 0; bits_to_clear -= 8;
        }
        else
        {
            r6->network.s6_addr[byte--] &= (0xff << bits_to_clear); bits_to_clear = 0;
        }
    }
}

bool
add_route_ipv6(struct route_ipv6 *r6, const struct tuntap *tt,
               unsigned int flags, const struct env_set *es,
               openvpn_net_ctx_t *ctx)
{
    int status = 0;
    bool gateway_needed = false;

    if (!(r6->flags & RT_DEFINED) )
    {
        return true; /* no error */
    }

    struct argv argv = argv_new();
    struct gc_arena gc = gc_new();

    const char *device = tt->actual_name;
    if (r6->iface != NULL)              /* vpn server special route */
    {
        device = r6->iface;
        if (!IN6_IS_ADDR_UNSPECIFIED(&r6->gateway) )
        {
            gateway_needed = true;
        }
    }

    route_ipv6_clear_host_bits(r6);
    const char *network = print_in6_addr( r6->network, 0, &gc);
    const char *gateway = print_in6_addr( r6->gateway, 0, &gc);


    msg(D_ROUTE, "add_route_ipv6(%s/%d -> %s metric %d) dev %s",
        network, r6->netbits, gateway, r6->metric, device );

    /*
     * Filter out routes which are essentially no-ops
     * (not currently done for IPv6)
     */

    /* On "tun" interface, we never set a gateway if the operating system
     * can do "route to interface" - it does not add value, as the target
     * dev already fully qualifies the route destination on point-to-point
     * interfaces.   OTOH, on "tap" interface, we must always set the
     * gateway unless the route is to be an on-link network
     */
    if (tt->type == DEV_TYPE_TAP
        && !( (r6->flags & RT_METRIC_DEFINED) && r6->metric == 0 ) )
    {
        gateway_needed = true;
    }

    if (gateway_needed && IN6_IS_ADDR_UNSPECIFIED(&r6->gateway))
    {
        msg(M_WARN, "ROUTE6 WARNING: " PACKAGE_NAME " needs a gateway "
            "parameter for a --route-ipv6 option and no default was set via "
            "--ifconfig-ipv6 or --route-ipv6-gateway option.  Not installing "
            "IPv6 route to %s/%d.", network, r6->netbits);
        status = 0;
        goto done;
    }


    argv_printf(&argv, "%s add -inet6 %s/%d %s",
                ROUTE_PATH,
                network, r6->netbits, gateway);
    argv_msg(D_ROUTE, &argv);
    bool ret = openvpn_execve_check(&argv, es, 0,
                                    "ERROR: AIX route add command failed");
    status = ret ? RTA_SUCCESS : RTA_ERROR;


done:
    if (status == RTA_SUCCESS)
    {
        r6->flags |= RT_ADDED;
    }
    else
    {
        r6->flags &= ~RT_ADDED;
    }
    argv_free(&argv);
    gc_free(&gc);
    /* release resources potentially allocated during route setup */
    net_ctx_reset(ctx);

    return (status != RTA_ERROR);
}

static void
delete_route(struct route_ipv4 *r,
             const struct tuntap *tt,
             unsigned int flags,
             const struct route_gateway_info *rgi,
             const struct env_set *es,
             openvpn_net_ctx_t *ctx)
{
    const char *network;
    const char *gateway;
    int is_local_route;

    if ((r->flags & (RT_DEFINED|RT_ADDED)) != (RT_DEFINED|RT_ADDED))
    {
        return;
    }

    struct gc_arena gc = gc_new();
    struct argv argv = argv_new();

    network = print_in_addr_t(r->network, 0, &gc);
    gateway = print_in_addr_t(r->gateway, 0, &gc);

    is_local_route = local_route(r->network, r->netmask, r->gateway, rgi);
    if (is_local_route == LR_ERROR)
    {
        goto done;
    }


    {
        int netbits = netmask_to_netbits2(r->netmask);
        argv_printf(&argv, "%s delete -net %s/%d %s",
                    ROUTE_PATH,
                    network, netbits, gateway);
        argv_msg(D_ROUTE, &argv);
        openvpn_execve_check(&argv, es, 0, "ERROR: AIX route delete command failed");
    }


done:
    r->flags &= ~RT_ADDED;
    argv_free(&argv);
    gc_free(&gc);
    /* release resources potentially allocated during route cleanup */
    net_ctx_reset(ctx);
}

void
delete_route_ipv6(const struct route_ipv6 *r6, const struct tuntap *tt,
                  unsigned int flags, const struct env_set *es,
                  openvpn_net_ctx_t *ctx)
{
    const char *network;

    if ((r6->flags & (RT_DEFINED|RT_ADDED)) != (RT_DEFINED|RT_ADDED))
    {
        return;
    }

    const char *gateway;
    bool gateway_needed = false;
    const char *device = tt->actual_name;
    if (r6->iface != NULL)              /* vpn server special route */
    {
        device = r6->iface;
        gateway_needed = true;
    }

    /* if we used a gateway on "add route", we also need to specify it on
     * delete, otherwise some OSes will refuse to delete the route
     */
    if (tt->type == DEV_TYPE_TAP
        && !( (r6->flags & RT_METRIC_DEFINED) && r6->metric == 0 ) )
    {
        gateway_needed = true;
    }

    struct gc_arena gc = gc_new();
    struct argv argv = argv_new();

    network = print_in6_addr( r6->network, 0, &gc);
    gateway = print_in6_addr( r6->gateway, 0, &gc);


    msg(D_ROUTE, "delete_route_ipv6(%s/%d)", network, r6->netbits );


    argv_printf(&argv, "%s delete -inet6 %s/%d %s",
                ROUTE_PATH,
                network, r6->netbits, gateway);
    argv_msg(D_ROUTE, &argv);
    openvpn_execve_check(&argv, es, 0, "ERROR: AIX route add command failed");

    argv_free(&argv);
    gc_free(&gc);
    /* release resources potentially allocated during route cleanup */
    net_ctx_reset(ctx);
}

/*
 * The --redirect-gateway option requires OS-specific code below
 * to get the current default gateway.
 */


/*
 * This is a platform-specific method that returns data about
 * the current default gateway.  Return data is placed into
 * a struct route_gateway_info object provided by caller.  The
 * implementation should CLEAR the structure before adding
 * data to it.
 *
 * Data returned includes:
 * 1. default gateway address (rgi->gateway.addr)
 * 2. netmask of interface that owns default gateway
 *    (rgi->gateway.netmask)
 * 3. hardware address (i.e. MAC address) of interface that owns
 *    default gateway (rgi->hwaddr)
 * 4. interface name (or adapter index on Windows) that owns default
 *    gateway (rgi->iface or rgi->adapter_index)
 * 5. an array of additional address/netmask pairs defined by
 *    interface that owns default gateway (rgi->addrs with length
 *    given in rgi->n_addrs)
 *
 * The flags RGI_x_DEFINED may be used to indicate which of the data
 * members were successfully returned (set in rgi->flags).  All of
 * the data members are optional, however certain OpenVPN functionality
 * may be disabled by missing items.
 */
void
get_default_gateway(struct route_gateway_info *rgi, openvpn_net_ctx_t *ctx)
{
    CLEAR(*rgi);
}
void
get_default_gateway_ipv6(struct route_ipv6_gateway_info *rgi6,
                         const struct in6_addr *dest, openvpn_net_ctx_t *ctx)
{
    msg(D_ROUTE, "no support for get_default_gateway_ipv6() on this system");
    CLEAR(*rgi6);
}


bool
netmask_to_netbits(const in_addr_t network, const in_addr_t netmask, int *netbits)
{
    int i;
    const int addrlen = sizeof(in_addr_t) * 8;

    if ((network & netmask) == network)
    {
        for (i = 0; i <= addrlen; ++i)
        {
            in_addr_t mask = netbits_to_netmask(i);
            if (mask == netmask)
            {
                if (i == addrlen)
                {
                    *netbits = -1;
                }
                else
                {
                    *netbits = i;
                }
                return true;
            }
        }
    }
    return false;
}

/* similar to netmask_to_netbits(), but don't mess with base address
 * etc., just convert to netbits - non-mappable masks are returned as "-1"
 */
int
netmask_to_netbits2(in_addr_t netmask)
{
    int i;
    const int addrlen = sizeof(in_addr_t) * 8;

    for (i = 0; i <= addrlen; ++i)
    {
        in_addr_t mask = netbits_to_netmask(i);
        if (mask == netmask)
        {
            return i;
        }
    }
    return -1;
}


/*
 * get_bypass_addresses() is used by the redirect-gateway bypass-x
 * functions to build a route bypass to selected DHCP/DNS servers,
 * so that outgoing packets to these servers don't end up in the tunnel.
 */


static void
get_bypass_addresses(struct route_bypass *rb, const unsigned int flags)   /* PLATFORM-SPECIFIC */
{
}


/*
 * Test if addr is reachable via a local interface (return ILA_LOCAL),
 * or if it needs to be routed via the default gateway (return
 * ILA_NONLOCAL).  If the target platform doesn't implement this
 * function, return ILA_NOT_IMPLEMENTED.
 *
 * Used by redirect-gateway autolocal feature
 */


int
test_local_addr(const in_addr_t addr, const struct route_gateway_info *rgi)  /* PLATFORM-SPECIFIC */
{
    if (rgi)
    {
        if (local_route(addr, 0xFFFFFFFF, rgi->gateway.addr, rgi))
        {
            return TLA_LOCAL;
        }
        else
        {
            return TLA_NONLOCAL;
        }
    }
    return TLA_NOT_IMPLEMENTED;
}
