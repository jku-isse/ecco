/*
 * Support routine for configuring link layer address
 */


#include "syshead.h"
#include "error.h"
#include "misc.h"
#include "run_command.h"
#include "lladdr.h"
#include "proto.h"

int
set_lladdr(openvpn_net_ctx_t *ctx, const char *ifname, const char *lladdr,
           const struct env_set *es)
{
    int r;

    if (!ifname || !lladdr)
    {
        return -1;
    }

    struct argv argv = argv_new();
    msg(M_WARN, "Sorry, but I don't know how to configure link layer addresses on this operating system.");
    return -1;
    argv_msg(M_INFO, &argv);
    r = openvpn_execve_check(&argv, es, M_WARN, "ERROR: Unable to set link layer address.");
    argv_free(&argv);

    if (r)
    {
        msg(M_INFO, "TUN/TAP link layer address set to %s", lladdr);
    }

    return r;
}