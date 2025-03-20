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


#include "win32.h"
#include "init.h"
#include "run_command.h"
#include "sig.h"
#include "occ.h"
#include "list.h"
#include "otime.h"
#include "pool.h"
#include "gremlin.h"
#include "occ.h"
#include "pkcs11.h"
#include "ps.h"
#include "lladdr.h"
#include "ping.h"
#include "mstats.h"
#include "ssl_verify.h"
#include "ssl_ncp.h"
#include "tls_crypt.h"
#include "forward.h"
#include "auth_token.h"
#include "mss.h"
#include "mudp.h"
#include "dco.h"

#include "memdbg.h"


static struct context *static_context; /* GLOBAL */
static const char *saved_pid_file_name; /* GLOBAL */

/*
 * Crypto initialization flags
 */
#define CF_LOAD_PERSISTED_PACKET_ID (1<<0)
#define CF_INIT_TLS_MULTI           (1<<1)
#define CF_INIT_TLS_AUTH_STANDALONE (1<<2)

static void do_init_first_time(struct context *c);

static bool do_deferred_p2p_ncp(struct context *c);

void
context_clear(struct context *c)
{
    CLEAR(*c);
}

void
context_clear_1(struct context *c)
{
    CLEAR(c->c1);
}

void
context_clear_2(struct context *c)
{
    CLEAR(c->c2);
}

void
context_clear_all_except_first_time(struct context *c)
{
    const bool first_time_save = c->first_time;
    const struct context_persist cpsave = c->persist;
    context_clear(c);
    c->first_time = first_time_save;
    c->persist = cpsave;
}

/*
 * Pass tunnel endpoint and MTU parms to a user-supplied script.
 * Used to execute the up/down script/plugins.
 */
static void
run_up_down(const char *command,
            const struct plugin_list *plugins,
            int plugin_type,
            const char *arg,
            const char *dev_type,
            int tun_mtu,
            const char *ifconfig_local,
            const char *ifconfig_remote,
            const char *context,
            const char *signal_text,
            const char *script_type,
            struct env_set *es)
{
    struct gc_arena gc = gc_new();

    if (signal_text)
    {
        setenv_str(es, "signal", signal_text);
    }
    setenv_str(es, "script_context", context);
    setenv_int(es, "tun_mtu", tun_mtu);
    setenv_str(es, "dev", arg);
    if (dev_type)
    {
        setenv_str(es, "dev_type", dev_type);
    }

    if (!ifconfig_local)
    {
        ifconfig_local = "";
    }
    if (!ifconfig_remote)
    {
        ifconfig_remote = "";
    }
    if (!context)
    {
        context = "";
    }

    if (plugin_defined(plugins, plugin_type))
    {
        struct argv argv = argv_new();
        ASSERT(arg);
        argv_printf(&argv,
                    "%s %d 0 %s %s %s",
                    arg, tun_mtu, ifconfig_local, ifconfig_remote, context);

        if (plugin_call(plugins, plugin_type, &argv, NULL, es) != OPENVPN_PLUGIN_FUNC_SUCCESS)
        {
            msg(M_FATAL, "ERROR: up/down plugin call failed");
        }

        argv_free(&argv);
    }

    if (command)
    {
        struct argv argv = argv_new();
        ASSERT(arg);
        setenv_str(es, "script_type", script_type);
        argv_parse_cmd(&argv, command);
        argv_printf_cat(&argv, "%s %d 0 %s %s %s", arg, tun_mtu,
                        ifconfig_local, ifconfig_remote, context);
        argv_msg(M_INFO, &argv);
        openvpn_run_script(&argv, es, S_FATAL, "--up/--down");
        argv_free(&argv);
    }

    gc_free(&gc);
}

/*
 * Should be called after options->ce is modified at the top
 * of a SIGUSR1 restart.
 */
static void
update_options_ce_post(struct options *options)
{
    /*
     * In pull mode, we usually import --ping/--ping-restart parameters from
     * the server.  However we should also set an initial default --ping-restart
     * for the period of time before we pull the --ping-restart parameter
     * from the server.
     */
    if (options->pull
        && options->ping_rec_timeout_action == PING_UNDEF
        && proto_is_dgram(options->ce.proto))
    {
        options->ping_rec_timeout = PRE_PULL_INITIAL_PING_RESTART;
        options->ping_rec_timeout_action = PING_RESTART;
    }
}


/*
 * Initialize and possibly randomize connection list.
 */
static void
init_connection_list(struct context *c)
{
    struct connection_list *l = c->options.connection_list;

    l->current = -1;
    if (c->options.remote_random)
    {
        int i;
        for (i = 0; i < l->len; ++i)
        {
            const int j = get_random() % l->len;
            if (i != j)
            {
                struct connection_entry *tmp;
                tmp = l->array[i];
                l->array[i] = l->array[j];
                l->array[j] = tmp;
            }
        }
    }
}

/*
 * Clear the remote address list
 */
static void
clear_remote_addrlist(struct link_socket_addr *lsa, bool free)
{
    if (lsa->remote_list && free)
    {
        freeaddrinfo(lsa->remote_list);
    }
    lsa->remote_list = NULL;
    lsa->current_remote = NULL;
}

/*
 * Increment to next connection entry
 */
static void
next_connection_entry(struct context *c)
{
    struct connection_list *l = c->options.connection_list;
    bool ce_defined;
    struct connection_entry *ce;
    int n_cycles = 0;

    do
    {
        ce_defined = true;
        if (c->options.no_advance && l->current >= 0)
        {
            c->options.no_advance = false;
        }
        else
        {
            /* Check if there is another resolved address to try for
             * the current connection */
            if (c->c1.link_socket_addr.current_remote
                && c->c1.link_socket_addr.current_remote->ai_next
                && !c->options.advance_next_remote)
            {
                c->c1.link_socket_addr.current_remote =
                    c->c1.link_socket_addr.current_remote->ai_next;
            }
            else
            {
                c->options.advance_next_remote = false;
                /* FIXME (schwabe) fix the persist-remote-ip option for real,
                 * this is broken probably ever since connection lists and multiple
                 * remote existed
                 */
                if (!c->options.persist_remote_ip)
                {
                    /* Connection entry addrinfo objects might have been
                     * resolved earlier but the entry itself might have been
                     * skipped by management on the previous loop.
                     * If so, clear the addrinfo objects as close_instance does
                     */
                    if (c->c1.link_socket_addr.remote_list)
                    {
                        clear_remote_addrlist(&c->c1.link_socket_addr,
                                              !c->options.resolve_in_advance);
                    }

                    /* close_instance should have cleared the addrinfo objects */
                    ASSERT(c->c1.link_socket_addr.current_remote == NULL);
                    ASSERT(c->c1.link_socket_addr.remote_list == NULL);
                }
                else
                {
                    c->c1.link_socket_addr.current_remote =
                        c->c1.link_socket_addr.remote_list;
                }

                int advance_count = 1;

                /* If previous connection entry was skipped by management client
                 * with a count to advance by, apply it.
                 */
                if (c->options.ce_advance_count > 0)
                {
                    advance_count = c->options.ce_advance_count;
                }

                /*
                 * Increase the number of connection attempts
                 * If this is connect-retry-max * size(l)
                 * OpenVPN will quit
                 */

                c->options.unsuccessful_attempts += advance_count;
                l->current += advance_count;

                if (l->current >= l->len)
                {
                    l->current %= l->len;
                    if (++n_cycles >= 2)
                    {
                        msg(M_FATAL, "No usable connection profiles are present");
                    }
                }
            }
        }

        c->options.ce_advance_count = 1;
        ce = l->array[l->current];

        if (ce->flags & CE_DISABLED)
        {
            ce_defined = false;
        }

        c->options.ce = *ce;
    } while (!ce_defined);

    /* Check if this connection attempt would bring us over the limit */
    if (c->options.connect_retry_max > 0
        && c->options.unsuccessful_attempts > (l->len  * c->options.connect_retry_max))
    {
        msg(M_FATAL, "All connections have been connect-retry-max (%d) times unsuccessful, exiting",
            c->options.connect_retry_max);
    }
    update_options_ce_post(&c->options);
}

/*
 * Query for private key and auth-user-pass username/passwords
 */
void
init_query_passwords(const struct context *c)
{
    /* Certificate password input */
    if (c->options.key_pass_file)
    {
        pem_password_setup(c->options.key_pass_file);
    }

    /* Auth user/pass input */
    if (c->options.auth_user_pass_file)
    {
        enable_auth_user_pass();
        auth_user_pass_setup(c->options.auth_user_pass_file,
                             c->options.auth_user_pass_file_inline, NULL);
    }
}

/*
 * Initialize/Uninitialize HTTP or SOCKS proxy
 */

static void
uninit_proxy_dowork(struct context *c)
{
    if (c->c1.http_proxy_owned && c->c1.http_proxy)
    {
        http_proxy_close(c->c1.http_proxy);
        c->c1.http_proxy = NULL;
        c->c1.http_proxy_owned = false;
    }
    if (c->c1.socks_proxy_owned && c->c1.socks_proxy)
    {
        socks_proxy_close(c->c1.socks_proxy);
        c->c1.socks_proxy = NULL;
        c->c1.socks_proxy_owned = false;
    }
}

static void
init_proxy_dowork(struct context *c)
{
    bool did_http = false;

    uninit_proxy_dowork(c);

    if (c->options.ce.http_proxy_options)
    {
        /* Possible HTTP proxy user/pass input */
        c->c1.http_proxy = http_proxy_new(c->options.ce.http_proxy_options);
        if (c->c1.http_proxy)
        {
            did_http = true;
            c->c1.http_proxy_owned = true;
        }
    }

    if (!did_http && c->options.ce.socks_proxy_server)
    {
        c->c1.socks_proxy = socks_proxy_new(c->options.ce.socks_proxy_server,
                                            c->options.ce.socks_proxy_port,
                                            c->options.ce.socks_proxy_authfile);
        if (c->c1.socks_proxy)
        {
            c->c1.socks_proxy_owned = true;
        }
    }
}

static void
init_proxy(struct context *c)
{
    init_proxy_dowork(c);
}

static void
uninit_proxy(struct context *c)
{
    uninit_proxy_dowork(c);
}

void
context_init_1(struct context *c)
{
    context_clear_1(c);

    packet_id_persist_init(&c->c1.pid_persist);

    init_connection_list(c);




}

void
context_gc_free(struct context *c)
{
    gc_free(&c->c2.gc);
    gc_free(&c->options.gc);
    gc_free(&c->gc);
}



bool
init_static(void)
{
    /* configure_path (); */



    /*
     * Initialize random number seed.  random() is only used
     * when "weak" random numbers are acceptable.
     * SSL library routines are always used when cryptographically
     * strong random numbers are required.
     */
    struct timeval tv;
    if (!gettimeofday(&tv, NULL))
    {
        const unsigned int seed = (unsigned int) tv.tv_sec ^ tv.tv_usec;
        srandom(seed);
    }

    error_reset();              /* initialize error.c */
    reset_check_status();       /* initialize status check code in socket.c */



    update_time();

    init_ssl_lib();







    return true;
}

void
uninit_static(void)
{
    free_ssl_lib();



}

void
init_verb_mute(struct context *c, unsigned int flags)
{
    if (flags & IVM_LEVEL_1)
    {
        /* set verbosity and mute levels */
        set_check_status(D_LINK_ERRORS, D_READ_WRITE);
        set_debug_level(c->options.verbosity, SDL_CONSTRAIN);
        set_mute_cutoff(c->options.mute);
    }

    /* special D_LOG_RW mode */
    if (flags & IVM_LEVEL_2)
    {
        c->c2.log_rw = (check_debug_level(D_LOG_RW) && !check_debug_level(D_LOG_RW + 1));
    }
}

/*
 * Possibly set --dev based on --dev-node.
 * For example, if --dev-node /tmp/foo/tun, and --dev undefined,
 * set --dev to tun.
 */
void
init_options_dev(struct options *options)
{
    if (!options->dev && options->dev_node)
    {
        char *dev_node = string_alloc(options->dev_node, NULL); /* POSIX basename() implementations may modify its arguments */
        options->dev = basename(dev_node);
    }
}

bool
print_openssl_info(const struct options *options)
{
    /*
     * OpenSSL info print mode?
     */
    if (options->show_ciphers || options->show_digests || options->show_engines
        || options->show_tls_ciphers || options->show_curves)
    {
        if (options->show_ciphers)
        {
            show_available_ciphers();
        }
        if (options->show_digests)
        {
            show_available_digests();
        }
        if (options->show_engines)
        {
            show_available_engines();
        }
        if (options->show_tls_ciphers)
        {
            show_available_tls_ciphers(options->cipher_list,
                                       options->cipher_list_tls13,
                                       options->tls_cert_profile);
        }
        if (options->show_curves)
        {
            show_available_curves();
        }
        return true;
    }
    return false;
}

/*
 * Static pre-shared key generation mode?
 */
bool
do_genkey(const struct options *options)
{
    /* should we disable paging? */
    if (options->mlock && (options->genkey))
    {
        platform_mlockall(true);
    }

    /*
     * We do not want user to use --genkey with --secret. In the transistion
     * phase we for secret.
     */
    if (options->genkey && options->genkey_type != GENKEY_SECRET
        && options->shared_secret_file)
    {
        msg(M_USAGE, "Using --genkey type with --secret filename is "
            "not supported.  Use --genkey type filename instead.");
    }
    if (options->genkey && options->genkey_type == GENKEY_SECRET)
    {
        int nbits_written;
        const char *genkey_filename = options->genkey_filename;
        if (options->shared_secret_file && options->genkey_filename)
        {
            msg(M_USAGE, "You must provide a filename to either --genkey "
                "or --secret, not both");
        }

        /*
         * Copy filename from shared_secret_file to genkey_filename to support
         * the old --genkey --secret foo.file syntax.
         */
        if (options->shared_secret_file)
        {
            msg(M_WARN, "WARNING: Using --genkey --secret filename is "
                "DEPRECATED.  Use --genkey secret filename instead.");
            genkey_filename = options->shared_secret_file;
        }

        nbits_written = write_key_file(2, genkey_filename);
        if (nbits_written < 0)
        {
            msg(M_FATAL, "Failed to write key file");
        }

        msg(D_GENKEY | M_NOPREFIX,
            "Randomly generated %d bit key written to %s", nbits_written,
            options->shared_secret_file);
        return true;
    }
    else if (options->genkey && options->genkey_type == GENKEY_TLS_CRYPTV2_SERVER)
    {
        tls_crypt_v2_write_server_key_file(options->genkey_filename);
        return true;
    }
    else if (options->genkey && options->genkey_type == GENKEY_TLS_CRYPTV2_CLIENT)
    {
        if (!options->tls_crypt_v2_file)
        {
            msg(M_USAGE,
                "--genkey tls-crypt-v2-client requires a server key to be set via --tls-crypt-v2 to create a client key");
        }

        tls_crypt_v2_write_client_key_file(options->genkey_filename,
                                           options->genkey_extra_data, options->tls_crypt_v2_file,
                                           options->tls_crypt_v2_file_inline);
        return true;
    }
    else if (options->genkey && options->genkey_type == GENKEY_AUTH_TOKEN)
    {
        auth_token_write_server_key_file(options->genkey_filename);
        return true;
    }
    else
    {
        return false;
    }
}

/*
 * Persistent TUN/TAP device management mode?
 */
bool
do_persist_tuntap(struct options *options, openvpn_net_ctx_t *ctx)
{
    if (!options->persist_config)
    {
        return false;
    }

    /* sanity check on options for --mktun or --rmtun */
    notnull(options->dev, "TUN/TAP device (--dev)");
    if (options->ce.remote || options->ifconfig_local
        || options->ifconfig_remote_netmask
        || options->shared_secret_file
        || options->tls_server || options->tls_client
        )
    {
        msg(M_FATAL|M_OPTERR,
            "options --mktun or --rmtun should only be used together with --dev");
    }


    msg(M_FATAL|M_OPTERR,
        "options --mktun and --rmtun are not available on your operating "
        "system.  Please check 'man tun' (or 'tap'), whether your system "
        "supports using 'ifconfig %s create' / 'destroy' to create/remove "
        "persistent tunnel interfaces.", options->dev );
    return false;
}

/*
 * Should we become a daemon?
 * Return true if we did it.
 */
bool
possibly_become_daemon(const struct options *options)
{
    bool ret = false;


    if (options->daemon)
    {
        /* Don't chdir immediately, but the end of the init sequence, if needed */

        if (daemon(1, options->log) < 0)
        {
            msg(M_ERR, "daemon() failed or unsupported");
        }
        restore_signal_state();
        if (options->log)
        {
            set_std_files_to_null(true);
        }

        ret = true;
    }
    return ret;
}

/*
 * Actually do UID/GID downgrade, chroot and SELinux context switching, if requested.
 */
static void
do_uid_gid_chroot(struct context *c, bool no_delay)
{
    static const char why_not[] = "will be delayed because of --client, --pull, or --up-delay";
    struct context_0 *c0 = c->c0;

    if (c0 && !c0->uid_gid_chroot_set)
    {
        /* chroot if requested */
        if (c->options.chroot_dir)
        {
            if (no_delay)
            {
                platform_chroot(c->options.chroot_dir);
            }
            else if (c->first_time)
            {
                msg(M_INFO, "NOTE: chroot %s", why_not);
            }
        }

        /* set user and/or group if we want to setuid/setgid */
        if (c0->uid_gid_specified)
        {
            if (no_delay)
            {
                platform_user_group_set(&c0->platform_state_user,
                                        &c0->platform_state_group,
                                        c);
            }
            else if (c->first_time)
            {
                msg(M_INFO, "NOTE: UID/GID downgrade %s", why_not);
            }
        }



        /* Privileges are going to be dropped by now (if requested), be sure
         * to prevent any future privilege dropping attempts from now on.
         */
        if (no_delay)
        {
            c0->uid_gid_chroot_set = true;
        }
    }
}

/*
 * Return common name in a way that is formatted for
 * prepending to msg() output.
 */
const char *
format_common_name(struct context *c, struct gc_arena *gc)
{
    struct buffer out = alloc_buf_gc(256, gc);
    if (c->c2.tls_multi)
    {
        buf_printf(&out, "[%s] ", tls_common_name(c->c2.tls_multi, false));
    }
    return BSTR(&out);
}

void
pre_setup(const struct options *options)
{
}

void
reset_coarse_timers(struct context *c)
{
    c->c2.coarse_timer_wakeup = 0;
}

/*
 * Initialise the server poll timeout timer
 * This timer is used in the http/socks proxy setup so it needs to be setup
 * before
 */
static void
do_init_server_poll_timeout(struct context *c)
{
    update_time();
    if (c->options.ce.connect_timeout)
    {
        event_timeout_init(&c->c2.server_poll_interval, c->options.ce.connect_timeout, now);
    }
}

/*
 * Initialize timers
 */
static void
do_init_timers(struct context *c, bool deferred)
{
    update_time();
    reset_coarse_timers(c);

    /* initialize inactivity timeout */
    if (c->options.inactivity_timeout)
    {
        event_timeout_init(&c->c2.inactivity_interval, c->options.inactivity_timeout, now);
    }

    /* initialize inactivity timeout */
    if (c->options.session_timeout)
    {
        event_timeout_init(&c->c2.session_interval, c->options.session_timeout,
                           now);
    }

    /* initialize pings */
    if (dco_enabled(&c->options))
    {
        /* The DCO kernel module will send the pings instead of user space */
        event_timeout_clear(&c->c2.ping_rec_interval);
        event_timeout_clear(&c->c2.ping_send_interval);
    }
    else
    {
        if (c->options.ping_send_timeout)
        {
            event_timeout_init(&c->c2.ping_send_interval, c->options.ping_send_timeout, 0);
        }

        if (c->options.ping_rec_timeout)
        {
            event_timeout_init(&c->c2.ping_rec_interval, c->options.ping_rec_timeout, now);
        }
    }

    /* If the auth-token renewal interval is shorter than reneg-sec, arm
     * "auth-token renewal" timer to send additional auth-token to update the
     * token on the client more often.  If not, this happens automatically
     * at renegotiation time, without needing an extra event.
     */
    if (c->options.auth_token_generate
        && c->options.auth_token_renewal < c->options.renegotiate_seconds)
    {
        event_timeout_init(&c->c2.auth_token_renewal_interval,
                           c->options.auth_token_renewal, now);
    }

    if (!deferred)
    {
        /* initialize connection establishment timer */
        event_timeout_init(&c->c2.wait_for_connect, 1, now);

        /* initialize occ timers */

        if (c->options.occ
            && !TLS_MODE(c)
            && c->c2.options_string_local && c->c2.options_string_remote)
        {
            event_timeout_init(&c->c2.occ_interval, OCC_INTERVAL_SECONDS, now);
        }

        if (c->options.mtu_test)
        {
            event_timeout_init(&c->c2.occ_mtu_load_test_interval, OCC_MTU_LOAD_INTERVAL_SECONDS, now);
        }

        /* initialize packet_id persistence timer */
        if (c->options.packet_id_file)
        {
            event_timeout_init(&c->c2.packet_id_persist_interval, 60, now);
        }

        /* initialize tmp_int optimization that limits the number of times we call
         * tls_multi_process in the main event loop */
        interval_init(&c->c2.tmp_int, TLS_MULTI_HORIZON, TLS_MULTI_REFRESH);
    }
}

/*
 * Initialize traffic shaper.
 */
static void
do_init_traffic_shaper(struct context *c)
{
    /* initialize traffic shaper (i.e. transmit bandwidth limiter) */
    if (c->options.shaper)
    {
        shaper_init(&c->c2.shaper, c->options.shaper);
        shaper_msg(&c->c2.shaper);
    }
}

/*
 * Allocate route list structures for IPv4 and IPv6
 * (we do this for IPv4 even if no --route option has been seen, as other
 * parts of OpenVPN might want to fill the route-list with info, e.g. DHCP)
 */
static void
do_alloc_route_list(struct context *c)
{
    if (!c->c1.route_list)
    {
        ALLOC_OBJ_CLEAR_GC(c->c1.route_list, struct route_list, &c->gc);
    }
    if (c->options.routes_ipv6 && !c->c1.route_ipv6_list)
    {
        ALLOC_OBJ_CLEAR_GC(c->c1.route_ipv6_list, struct route_ipv6_list, &c->gc);
    }
}


/*
 * Initialize the route list, resolving any DNS names in route
 * options and saving routes in the environment.
 */
static void
do_init_route_list(const struct options *options,
                   struct route_list *route_list,
                   const struct link_socket_info *link_socket_info,
                   struct env_set *es,
                   openvpn_net_ctx_t *ctx)
{
    const char *gw = NULL;
    int dev = dev_type_enum(options->dev, options->dev_type);
    int metric = 0;

    /* if DCO is enabled we have both regular routes and iroutes in the system
     * routing table, and normal routes must have a higher metric for that to
     * work so that iroutes are always matched first
     */
    if (dco_enabled(options))
    {
        metric = DCO_DEFAULT_METRIC;
    }

    if (dev == DEV_TYPE_TUN && (options->topology == TOP_NET30 || options->topology == TOP_P2P))
    {
        gw = options->ifconfig_remote_netmask;
    }
    if (options->route_default_gateway)
    {
        gw = options->route_default_gateway;
    }
    if (options->route_default_metric)
    {
        metric = options->route_default_metric;
    }

    if (init_route_list(route_list,
                        options->routes,
                        gw,
                        metric,
                        link_socket_current_remote(link_socket_info),
                        es,
                        ctx))
    {
        /* copy routes to environment */
        setenv_routes(es, route_list);
    }
}

static void
do_init_route_ipv6_list(const struct options *options,
                        struct route_ipv6_list *route_ipv6_list,
                        const struct link_socket_info *link_socket_info,
                        struct env_set *es,
                        openvpn_net_ctx_t *ctx)
{
    const char *gw = NULL;
    int metric = -1;            /* no metric set */

    /* see explanation in do_init_route_list() */
    if (dco_enabled(options))
    {
        metric = DCO_DEFAULT_METRIC;
    }

    gw = options->ifconfig_ipv6_remote;         /* default GW = remote end */
    if (options->route_ipv6_default_gateway)
    {
        gw = options->route_ipv6_default_gateway;
    }

    if (options->route_default_metric)
    {
        metric = options->route_default_metric;
    }

    /* redirect (IPv6) gateway to VPN?  if yes, add a few more specifics
     */
    if (options->routes_ipv6->flags & RG_REROUTE_GW)
    {
        char *opt_list[] = { "::/3", "2000::/4", "3000::/4", "fc00::/7", NULL };
        int i;

        for (i = 0; opt_list[i]; i++)
        {
            add_route_ipv6_to_option_list( options->routes_ipv6,
                                           string_alloc(opt_list[i], options->routes_ipv6->gc),
                                           NULL, NULL );
        }
    }

    if (init_route_ipv6_list(route_ipv6_list,
                             options->routes_ipv6,
                             gw,
                             metric,
                             link_socket_current_remote_ipv6(link_socket_info),
                             es,
                             ctx))
    {
        /* copy routes to environment */
        setenv_routes_ipv6(es, route_ipv6_list);
    }
}


/*
 * Called after all initialization has been completed.
 */
void
initialization_sequence_completed(struct context *c, const unsigned int flags)
{
    static const char message[] = "Initialization Sequence Completed";

    /* Reset the unsuccessful connection counter on complete initialisation */
    c->options.unsuccessful_attempts = 0;

    /* If we delayed UID/GID downgrade or chroot, do it now */
    do_uid_gid_chroot(c, true);

    /* Test if errors */
    if (flags & ISC_ERRORS)
    {
        msg(M_INFO, "%s With Errors", message);
    }
    else
    {
        msg(M_INFO, "%s", message);
    }

    /* Flag that we initialized */
    if ((flags & (ISC_ERRORS|ISC_SERVER)) == 0)
    {
        c->options.no_advance = true;
    }


}

/*
 * Possibly add routes and/or call route-up script
 * based on options.
 */
bool
do_route(const struct options *options,
         struct route_list *route_list,
         struct route_ipv6_list *route_ipv6_list,
         const struct tuntap *tt,
         const struct plugin_list *plugins,
         struct env_set *es,
         openvpn_net_ctx_t *ctx)
{
    bool ret = true;
    if (!options->route_noexec && ( route_list || route_ipv6_list ) )
    {
        ret = add_routes(route_list, route_ipv6_list, tt, ROUTE_OPTION_FLAGS(options),
                         es, ctx);
        setenv_int(es, "redirect_gateway", route_did_redirect_default_gateway(route_list));
    }

    if (plugin_defined(plugins, OPENVPN_PLUGIN_ROUTE_UP))
    {
        if (plugin_call(plugins, OPENVPN_PLUGIN_ROUTE_UP, NULL, NULL, es) != OPENVPN_PLUGIN_FUNC_SUCCESS)
        {
            msg(M_WARN, "WARNING: route-up plugin call failed");
        }
    }

    if (options->route_script)
    {
        struct argv argv = argv_new();
        setenv_str(es, "script_type", "route-up");
        argv_parse_cmd(&argv, options->route_script);
        openvpn_run_script(&argv, es, 0, "--route-up");
        argv_free(&argv);
    }

    return ret;
}

/*
 * initialize tun/tap device object
 */
static void
do_init_tun(struct context *c)
{
    c->c1.tuntap = init_tun(c->options.dev,
                            c->options.dev_type,
                            c->options.topology,
                            c->options.ifconfig_local,
                            c->options.ifconfig_remote_netmask,
                            c->options.ifconfig_ipv6_local,
                            c->options.ifconfig_ipv6_netbits,
                            c->options.ifconfig_ipv6_remote,
                            c->c1.link_socket_addr.bind_local,
                            c->c1.link_socket_addr.remote_list,
                            !c->options.ifconfig_nowarn,
                            c->c2.es,
                            &c->net_ctx,
                            c->c1.tuntap);


    init_tun_post(c->c1.tuntap,
                  &c->c2.frame,
                  &c->options.tuntap_options);

    c->c1.tuntap_owned = true;
}

/*
 * Open tun/tap device, ifconfig, call up script, etc.
 */


static bool
can_preserve_tun(struct tuntap *tt)
{
    return is_tun_type_set(tt);
}

static bool
do_open_tun(struct context *c, int *error_flags)
{
    struct gc_arena gc = gc_new();
    bool ret = false;
    *error_flags = 0;

    if (!can_preserve_tun(c->c1.tuntap))
    {

        /* initialize (but do not open) tun/tap object */
        do_init_tun(c);

        /* inherit the dco context from the tuntap object */
        if (c->c2.tls_multi)
        {
            c->c2.tls_multi->dco = &c->c1.tuntap->dco;
        }


        /* allocate route list structure */
        do_alloc_route_list(c);

        /* parse and resolve the route option list */
        ASSERT(c->c2.link_socket);
        if (c->options.routes && c->c1.route_list)
        {
            do_init_route_list(&c->options, c->c1.route_list,
                               &c->c2.link_socket->info, c->c2.es, &c->net_ctx);
        }
        if (c->options.routes_ipv6 && c->c1.route_ipv6_list)
        {
            do_init_route_ipv6_list(&c->options, c->c1.route_ipv6_list,
                                    &c->c2.link_socket->info, c->c2.es,
                                    &c->net_ctx);
        }

        /* do ifconfig */
        if (!c->options.ifconfig_noexec
            && ifconfig_order() == IFCONFIG_BEFORE_TUN_OPEN)
        {
            /* guess actual tun/tap unit number that will be returned
             * by open_tun */
            const char *guess = guess_tuntap_dev(c->options.dev,
                                                 c->options.dev_type,
                                                 c->options.dev_node,
                                                 &gc);
            do_ifconfig(c->c1.tuntap, guess, c->c2.frame.tun_mtu, c->c2.es,
                        &c->net_ctx);
        }

        /* possibly add routes */
        if (route_order() == ROUTE_BEFORE_TUN)
        {
            /* Ignore route_delay, would cause ROUTE_BEFORE_TUN to be ignored */
            bool status = do_route(&c->options, c->c1.route_list, c->c1.route_ipv6_list,
                                   c->c1.tuntap, c->plugins, c->c2.es, &c->net_ctx);
            *error_flags |= (status ? 0 : ISC_ROUTE_ERRORS);
        }
        if (dco_enabled(&c->options))
        {
            ovpn_dco_init(c->mode, &c->c1.tuntap->dco);
        }

        /* open the tun device */
        open_tun(c->options.dev, c->options.dev_type, c->options.dev_node,
                 c->c1.tuntap, &c->net_ctx);

        /* set the hardware address */
        if (c->options.lladdr)
        {
            set_lladdr(&c->net_ctx, c->c1.tuntap->actual_name, c->options.lladdr,
                       c->c2.es);
        }

        /* do ifconfig */
        if (!c->options.ifconfig_noexec
            && ifconfig_order() == IFCONFIG_AFTER_TUN_OPEN)
        {
            do_ifconfig(c->c1.tuntap, c->c1.tuntap->actual_name,
                        c->c2.frame.tun_mtu, c->c2.es, &c->net_ctx);
        }

        /* run the up script */
        run_up_down(c->options.up_script,
                    c->plugins,
                    OPENVPN_PLUGIN_UP,
                    c->c1.tuntap->actual_name,
                    dev_type_string(c->options.dev, c->options.dev_type),
                    c->c2.frame.tun_mtu,
                    print_in_addr_t(c->c1.tuntap->local, IA_EMPTY_IF_UNDEF, &gc),
                    print_in_addr_t(c->c1.tuntap->remote_netmask, IA_EMPTY_IF_UNDEF, &gc),
                    "init",
                    NULL,
                    "up",
                    c->c2.es);


        /* possibly add routes */
        if ((route_order() == ROUTE_AFTER_TUN) && (!c->options.route_delay_defined))
        {
            int status = do_route(&c->options, c->c1.route_list, c->c1.route_ipv6_list,
                                  c->c1.tuntap, c->plugins, c->c2.es, &c->net_ctx);
            *error_flags |= (status ? 0 : ISC_ROUTE_ERRORS);
        }

        ret = true;
        static_context = c;
    }
    else
    {
        msg(M_INFO, "Preserving previous TUN/TAP instance: %s",
            c->c1.tuntap->actual_name);

        /* explicitly set the ifconfig_* env vars */
        do_ifconfig_setenv(c->c1.tuntap, c->c2.es);

        /* run the up script if user specified --up-restart */
        if (c->options.up_restart)
        {
            run_up_down(c->options.up_script,
                        c->plugins,
                        OPENVPN_PLUGIN_UP,
                        c->c1.tuntap->actual_name,
                        dev_type_string(c->options.dev, c->options.dev_type),
                        c->c2.frame.tun_mtu,
                        print_in_addr_t(c->c1.tuntap->local, IA_EMPTY_IF_UNDEF, &gc),
                        print_in_addr_t(c->c1.tuntap->remote_netmask, IA_EMPTY_IF_UNDEF, &gc),
                        "restart",
                        NULL,
                        "up",
                        c->c2.es);
        }

    }
    gc_free(&gc);
    return ret;
}

/*
 * Close TUN/TAP device
 */

static void
do_close_tun_simple(struct context *c)
{
    msg(D_CLOSE, "Closing %s interface",
        dco_enabled(&c->options) ? "DCO" : "TUN/TAP");

    if (c->c1.tuntap)
    {
        if (!c->options.ifconfig_noexec)
        {
            undo_ifconfig(c->c1.tuntap, &c->net_ctx);
        }
        close_tun(c->c1.tuntap, &c->net_ctx);
        c->c1.tuntap = NULL;
    }
    c->c1.tuntap_owned = false;
    CLEAR(c->c1.pulled_options_digest_save);
}

static void
do_close_tun(struct context *c, bool force)
{
    /* With dco-win we open tun handle in the very beginning.
     * In case when tun wasn't opened - like we haven't connected,
     * we still need to close tun handle
     */
    if (tuntap_is_dco_win(c->c1.tuntap) && !is_tun_type_set(c->c1.tuntap))
    {
        do_close_tun_simple(c);
        return;
    }

    if (!c->c1.tuntap || !c->c1.tuntap_owned)
    {
        return;
    }

    struct gc_arena gc = gc_new();
    const char *tuntap_actual = string_alloc(c->c1.tuntap->actual_name, &gc);
    const in_addr_t local = c->c1.tuntap->local;
    const in_addr_t remote_netmask = c->c1.tuntap->remote_netmask;

    if (force || !(c->sig->signal_received == SIGUSR1 && c->options.persist_tun))
    {
        static_context = NULL;


        /* delete any routes we added */
        if (c->c1.route_list || c->c1.route_ipv6_list)
        {
            run_up_down(c->options.route_predown_script,
                        c->plugins,
                        OPENVPN_PLUGIN_ROUTE_PREDOWN,
                        tuntap_actual,
                        NULL,
                        c->c2.frame.tun_mtu,
                        print_in_addr_t(local, IA_EMPTY_IF_UNDEF, &gc),
                        print_in_addr_t(remote_netmask, IA_EMPTY_IF_UNDEF, &gc),
                        "init",
                        signal_description(c->sig->signal_received,
                                           c->sig->signal_text),
                        "route-pre-down",
                        c->c2.es);

            delete_routes(c->c1.route_list, c->c1.route_ipv6_list,
                          c->c1.tuntap, ROUTE_OPTION_FLAGS(&c->options),
                          c->c2.es, &c->net_ctx);
        }

        /* actually close tun/tap device based on --down-pre flag */
        if (!c->options.down_pre)
        {
            do_close_tun_simple(c);
        }

        /* Run the down script -- note that it will run at reduced
         * privilege if, for example, "--user" was used. */
        run_up_down(c->options.down_script,
                    c->plugins,
                    OPENVPN_PLUGIN_DOWN,
                    tuntap_actual,
                    NULL,
                    c->c2.frame.tun_mtu,
                    print_in_addr_t(local, IA_EMPTY_IF_UNDEF, &gc),
                    print_in_addr_t(remote_netmask, IA_EMPTY_IF_UNDEF, &gc),
                    "init",
                    signal_description(c->sig->signal_received,
                                       c->sig->signal_text),
                    "down",
                    c->c2.es);


        /* actually close tun/tap device based on --down-pre flag */
        if (c->options.down_pre)
        {
            do_close_tun_simple(c);
        }
    }
    else
    {
        /* run the down script on this restart if --up-restart was specified */
        if (c->options.up_restart)
        {
            run_up_down(c->options.down_script,
                        c->plugins,
                        OPENVPN_PLUGIN_DOWN,
                        tuntap_actual,
                        NULL,
                        c->c2.frame.tun_mtu,
                        print_in_addr_t(local, IA_EMPTY_IF_UNDEF, &gc),
                        print_in_addr_t(remote_netmask, IA_EMPTY_IF_UNDEF, &gc),
                        "restart",
                        signal_description(c->sig->signal_received,
                                           c->sig->signal_text),
                        "down",
                        c->c2.es);
        }


    }
    gc_free(&gc);
}

void
tun_abort(void)
{
    struct context *c = static_context;
    if (c)
    {
        static_context = NULL;
        do_close_tun(c, true);
    }
}

/*
 * Handle delayed tun/tap interface bringup due to --up-delay or --pull
 */

/**
 * Helper for do_up().  Take two option hashes and return true if they are not
 * equal, or either one is all-zeroes.
 */
static bool
options_hash_changed_or_zero(const struct sha256_digest *a,
                             const struct sha256_digest *b)
{
    const struct sha256_digest zero = {{0}};
    return memcmp(a, b, sizeof(struct sha256_digest))
           || !memcmp(a, &zero, sizeof(struct sha256_digest));
}

static bool
p2p_set_dco_keepalive(struct context *c)
{
    if (dco_enabled(&c->options)
        && (c->options.ping_send_timeout || c->c2.frame.mss_fix))
    {
        int ret = dco_set_peer(&c->c1.tuntap->dco,
                               c->c2.tls_multi->dco_peer_id,
                               c->options.ping_send_timeout,
                               c->options.ping_rec_timeout,
                               c->c2.frame.mss_fix);
        if (ret < 0)
        {
            msg(D_DCO, "Cannot set parameters for DCO peer (id=%u): %s",
                c->c2.tls_multi->dco_peer_id, strerror(-ret));
            return false;
        }
    }
    return true;
}

/**
 * Helper function for tls_print_deferred_options_results
 * Adds the ", " delimitor if there already some data in the
 * buffer.
 */
static void
add_delim_if_non_empty(struct buffer *buf, const char *header)
{
    if (buf_len(buf) > strlen(header))
    {
        buf_printf(buf, ", ");
    }
}


/**
 * Prints the results of options imported for the data channel
 * @param o
 */
static void
tls_print_deferred_options_results(struct context *c)
{
    struct options *o = &c->options;

    struct buffer out;
    uint8_t line[1024] = { 0 };
    buf_set_write(&out, line, sizeof(line));


    if (cipher_kt_mode_aead(o->ciphername))
    {
        buf_printf(&out, "Data Channel: cipher '%s'",
                   cipher_kt_name(o->ciphername));
    }
    else
    {
        buf_printf(&out, "Data Channel: cipher '%s', auth '%s'",
                   cipher_kt_name(o->ciphername), md_kt_name(o->authname));
    }

    if (o->use_peer_id)
    {
        buf_printf(&out, ", peer-id: %d", o->peer_id);
    }


    msg(D_HANDSHAKE, "%s", BSTR(&out));

    buf_clear(&out);

    const char *header = "Timers: ";

    buf_printf(&out, "%s", header);

    if (o->ping_send_timeout)
    {
        buf_printf(&out, "ping %d", o->ping_send_timeout);
    }

    if (o->ping_rec_timeout_action != PING_UNDEF)
    {
        /* yes unidirectional ping is possible .... */
        add_delim_if_non_empty(&out, header);

        if (o->ping_rec_timeout_action == PING_EXIT)
        {
            buf_printf(&out, "ping-exit %d", o->ping_rec_timeout);
        }
        else
        {
            buf_printf(&out, "ping-restart %d", o->ping_rec_timeout);
        }
    }

    if (o->inactivity_timeout)
    {
        add_delim_if_non_empty(&out, header);

        buf_printf(&out, "inactive %d", o->inactivity_timeout);
        if (o->inactivity_minimum_bytes)
        {
            buf_printf(&out, " %" PRIu64, o->inactivity_minimum_bytes);
        }
    }

    if (o->session_timeout)
    {
        add_delim_if_non_empty(&out, header);
        buf_printf(&out, "session-timeout %d", o->session_timeout);
    }

    if (buf_len(&out) > strlen(header))
    {
        msg(D_HANDSHAKE, "%s", BSTR(&out));
    }

    buf_clear(&out);
    header = "Protocol options: ";
    buf_printf(&out, "%s", header);

    if (c->options.ce.explicit_exit_notification)
    {
        buf_printf(&out, "explicit-exit-notify %d",
                   c->options.ce.explicit_exit_notification);
    }
    if (c->options.imported_protocol_flags)
    {
        add_delim_if_non_empty(&out, header);

        buf_printf(&out, "protocol-flags");

        if (o->imported_protocol_flags & CO_USE_CC_EXIT_NOTIFY)
        {
            buf_printf(&out, " cc-exit");
        }
        if (o->imported_protocol_flags & CO_USE_TLS_KEY_MATERIAL_EXPORT)
        {
            buf_printf(&out, " tls-ekm");
        }
        if (o->imported_protocol_flags & CO_USE_DYNAMIC_TLS_CRYPT)
        {
            buf_printf(&out, " dyn-tls-crypt");
        }
    }

    if (buf_len(&out) > strlen(header))
    {
        msg(D_HANDSHAKE, "%s", BSTR(&out));
    }
}


/**
 * This function is expected to be invoked after open_tun() was performed.
 *
 * This kind of behaviour is required by DCO, because the following operations
 * can be done only after the DCO device was created and the new peer was
 * properly added.
 */
static bool
do_deferred_options_part2(struct context *c)
{
    struct frame *frame_fragment = NULL;

    struct tls_session *session = &c->c2.tls_multi->session[TM_ACTIVE];
    if (!tls_session_update_crypto_params(c->c2.tls_multi, session,
                                          &c->options, &c->c2.frame,
                                          frame_fragment,
                                          get_link_socket_info(c)))
    {
        msg(D_TLS_ERRORS, "OPTIONS ERROR: failed to import crypto options");
        return false;
    }

    return true;
}

bool
do_up(struct context *c, bool pulled_options, unsigned int option_types_found)
{
    int error_flags = 0;
    if (!c->c2.do_up_ran)
    {
        reset_coarse_timers(c);

        if (pulled_options)
        {
            if (!do_deferred_options(c, option_types_found))
            {
                msg(D_PUSH_ERRORS, "ERROR: Failed to apply push options");
                return false;
            }
        }

        /* if --up-delay specified, open tun, do ifconfig, and run up script now */
        if (c->options.up_delay || PULL_DEFINED(&c->options))
        {
            c->c2.did_open_tun = do_open_tun(c, &error_flags);
            update_time();

            /*
             * Was tun interface object persisted from previous restart iteration,
             * and if so did pulled options string change from previous iteration?
             */
            if (!c->c2.did_open_tun
                && PULL_DEFINED(&c->options)
                && c->c1.tuntap
                && options_hash_changed_or_zero(&c->c1.pulled_options_digest_save,
                                                &c->c2.pulled_options_digest))
            {
                /* if so, close tun, delete routes, then reinitialize tun and add routes */
                msg(M_INFO, "NOTE: Pulled options changed on restart, will need to close and reopen TUN/TAP device.");

                bool tt_dco_win = tuntap_is_dco_win(c->c1.tuntap);
                do_close_tun(c, true);

                if (tt_dco_win)
                {
                    msg(M_NONFATAL, "dco-win doesn't yet support reopening TUN device");
                    /* prevent link_socket_close() from closing handle with WinSock API */
                    c->c2.link_socket->sd = SOCKET_UNDEFINED;
                    return false;
                }
                else
                {
                    management_sleep(1);
                    c->c2.did_open_tun = do_open_tun(c, &error_flags);
                    update_time();
                }
            }
        }
    }

    /* This part needs to be run in p2p mode (without pull) when the client
     * reconnects to setup various things (like DCO and NCP cipher) that
     * might have changed from the previous connection.
     */
    if (!c->c2.do_up_ran || (c->c2.tls_multi && c->c2.tls_multi->multi_state == CAS_RECONNECT_PENDING))
    {
        if (c->mode == MODE_POINT_TO_POINT)
        {
            /* ovpn-dco requires adding the peer now, before any option can be set,
             * but *after* having parsed the pushed peer-id in do_deferred_options()
             */
            int ret = dco_p2p_add_new_peer(c);
            if (ret < 0)
            {
                msg(D_DCO, "Cannot add peer to DCO: %s (%d)", strerror(-ret), ret);
                return false;
            }
        }

        /* do_deferred_options_part2() and do_deferred_p2p_ncp() *must* be
         * invoked after open_tun().
         * This is required by DCO because we must have created the interface
         * and added the peer before we can fiddle with the keys or any other
         * data channel per-peer setting.
         */
        if (pulled_options)
        {
            if (!do_deferred_options_part2(c))
            {
                return false;
            }
        }
        else
        {
            if (c->mode == MODE_POINT_TO_POINT)
            {
                if (!do_deferred_p2p_ncp(c))
                {
                    msg(D_TLS_ERRORS, "ERROR: Failed to apply P2P negotiated protocol options");
                    return false;
                }
            }
        }

        if (c->mode == MODE_POINT_TO_POINT && !p2p_set_dco_keepalive(c))
        {
            msg(D_TLS_ERRORS, "ERROR: Failed to apply DCO keepalive or MSS fix parameters");
            return false;
        }

        if (c->c2.did_open_tun)
        {
            c->c1.pulled_options_digest_save = c->c2.pulled_options_digest;

            /* if --route-delay was specified, start timer */
            if ((route_order() == ROUTE_AFTER_TUN) && c->options.route_delay_defined)
            {
                event_timeout_init(&c->c2.route_wakeup, c->options.route_delay, now);
                event_timeout_init(&c->c2.route_wakeup_expire, c->options.route_delay + c->options.route_delay_window, now);
                if (c->c1.tuntap)
                {
                    tun_standby_init(c->c1.tuntap);
                }
            }
            else
            {
                initialization_sequence_completed(c, error_flags); /* client/p2p --route-delay undefined */
            }
        }
        else if (c->options.mode == MODE_POINT_TO_POINT)
        {
            initialization_sequence_completed(c, error_flags); /* client/p2p restart with --persist-tun */
        }

        tls_print_deferred_options_results(c);

        c->c2.do_up_ran = true;
        if (c->c2.tls_multi)
        {
            c->c2.tls_multi->multi_state = CAS_CONNECT_DONE;
        }
    }
    return true;
}

/*
 * These are the option categories which will be accepted by pull.
 */
unsigned int
pull_permission_mask(const struct context *c)
{
    unsigned int flags =
        OPT_P_UP
        | OPT_P_ROUTE_EXTRAS
        | OPT_P_SOCKBUF
        | OPT_P_SOCKFLAGS
        | OPT_P_SETENV
        | OPT_P_SHAPER
        | OPT_P_TIMER
        | OPT_P_COMP
        | OPT_P_PERSIST
        | OPT_P_MESSAGES
        | OPT_P_EXPLICIT_NOTIFY
        | OPT_P_ECHO
        | OPT_P_PULL_MODE
        | OPT_P_PEER_ID
        | OPT_P_NCP
        | OPT_P_PUSH_MTU;

    if (!c->options.route_nopull)
    {
        flags |= (OPT_P_ROUTE | OPT_P_DHCPDNS);
    }

    return flags;
}

static bool
do_deferred_p2p_ncp(struct context *c)
{
    if (!c->c2.tls_multi)
    {
        return true;
    }

    c->options.use_peer_id = c->c2.tls_multi->use_peer_id;

    struct tls_session *session = &c->c2.tls_multi->session[TM_ACTIVE];

    const char *ncp_cipher = get_p2p_ncp_cipher(session, c->c2.tls_multi->peer_info,
                                                &c->options.gc);

    if (ncp_cipher)
    {
        c->options.ciphername = ncp_cipher;
    }
    else if (!c->options.enable_ncp_fallback)
    {
        msg(D_TLS_ERRORS, "ERROR: failed to negotiate cipher with peer and "
            "--data-ciphers-fallback not enabled. No usable "
            "data channel cipher");
        return false;
    }

    struct frame *frame_fragment = NULL;

    if (!tls_session_update_crypto_params(c->c2.tls_multi, session, &c->options,
                                          &c->c2.frame, frame_fragment,
                                          get_link_socket_info(c)))
    {
        msg(D_TLS_ERRORS, "ERROR: failed to set crypto cipher");
        return false;
    }
    return true;
}

/*
 * Handle non-tun-related pulled options.
 */
bool
do_deferred_options(struct context *c, const unsigned int found)
{
    if (found & OPT_P_MESSAGES)
    {
        init_verb_mute(c, IVM_LEVEL_1|IVM_LEVEL_2);
        msg(D_PUSH, "OPTIONS IMPORT: --verb and/or --mute level changed");
    }
    if (found & OPT_P_TIMER)
    {
        do_init_timers(c, true);
        msg(D_PUSH_DEBUG, "OPTIONS IMPORT: timers and/or timeouts modified");
    }

    if (found & OPT_P_EXPLICIT_NOTIFY)
    {
        if (!proto_is_udp(c->options.ce.proto) && c->options.ce.explicit_exit_notification)
        {
            msg(D_PUSH, "OPTIONS IMPORT: --explicit-exit-notify can only be used with --proto udp");
            c->options.ce.explicit_exit_notification = 0;
        }
        else
        {
            msg(D_PUSH_DEBUG, "OPTIONS IMPORT: explicit notify parm(s) modified");
        }
    }

    if (found & OPT_P_COMP)
    {
        if (!check_compression_settings_valid(&c->options.comp, D_PUSH_ERRORS))
        {
            msg(D_PUSH_ERRORS, "OPTIONS ERROR: server pushed compression "
                "settings that are not allowed and will result "
                "in a non-working connection. "
                "See also allow-compression in the manual.");
            return false;
        }
    }

    if (found & OPT_P_SHAPER)
    {
        msg(D_PUSH, "OPTIONS IMPORT: traffic shaper enabled");
        do_init_traffic_shaper(c);
    }

    if (found & OPT_P_SOCKBUF)
    {
        msg(D_PUSH, "OPTIONS IMPORT: --sndbuf/--rcvbuf options modified");
        link_socket_update_buffer_sizes(c->c2.link_socket, c->options.rcvbuf, c->options.sndbuf);
    }

    if (found & OPT_P_SOCKFLAGS)
    {
        msg(D_PUSH, "OPTIONS IMPORT: --socket-flags option modified");
        link_socket_update_flags(c->c2.link_socket, c->options.sockflags);
    }

    if (found & OPT_P_PERSIST)
    {
        msg(D_PUSH, "OPTIONS IMPORT: --persist options modified");
    }
    if (found & OPT_P_UP)
    {
        msg(D_PUSH, "OPTIONS IMPORT: --ifconfig/up options modified");
    }
    if (found & OPT_P_ROUTE)
    {
        msg(D_PUSH, "OPTIONS IMPORT: route options modified");
    }
    if (found & OPT_P_ROUTE_EXTRAS)
    {
        msg(D_PUSH, "OPTIONS IMPORT: route-related options modified");
    }
    if (found & OPT_P_DHCPDNS)
    {
        msg(D_PUSH, "OPTIONS IMPORT: --ip-win32 and/or --dhcp-option options modified");
    }
    if (found & OPT_P_SETENV)
    {
        msg(D_PUSH, "OPTIONS IMPORT: environment modified");
    }

    if (found & OPT_P_PEER_ID)
    {
        msg(D_PUSH_DEBUG, "OPTIONS IMPORT: peer-id set");
        c->c2.tls_multi->use_peer_id = true;
        c->c2.tls_multi->peer_id = c->options.peer_id;
    }

    /* process (potentially) pushed options */
    if (c->options.pull)
    {
        if (!check_pull_client_ncp(c, found))
        {
            return false;
        }

        /* Check if pushed options are compatible with DCO, if enabled */
        if (dco_enabled(&c->options)
            && !dco_check_pull_options(D_PUSH_ERRORS, &c->options))
        {
            msg(D_PUSH_ERRORS, "OPTIONS ERROR: pushed options are incompatible "
                "with data channel offload. Use --disable-dco to connect to "
                "this server");
            return false;
        }
    }

    if (found & OPT_P_PUSH_MTU)
    {
        /* MTU has changed, check that the pushed MTU is small enough to
         * be able to change it */
        msg(D_PUSH, "OPTIONS IMPORT: tun-mtu set to %d", c->options.ce.tun_mtu);

        struct frame *frame = &c->c2.frame;

        if (c->options.ce.tun_mtu > frame->tun_max_mtu)
        {
            msg(D_PUSH_ERRORS, "Server-pushed tun-mtu is too large, please add "
                "tun-mtu-max %d in the client configuration",
                c->options.ce.tun_mtu);
        }
        frame->tun_mtu = min_int(frame->tun_max_mtu, c->options.ce.tun_mtu);
    }

    return true;
}

/*
 * Possible hold on initialization, holdtime is the
 * time OpenVPN would wait without management
 */
static bool
do_hold(int holdtime)
{
    return false;
}

/*
 * Sleep before restart.
 */
static void
socket_restart_pause(struct context *c)
{
    int sec = 2;
    int backoff = 0;

    switch (c->options.ce.proto)
    {
        case PROTO_TCP_SERVER:
            sec = 1;
            break;

        case PROTO_UDP:
        case PROTO_TCP_CLIENT:
            sec = c->options.ce.connect_retry_seconds;
            break;
    }


    if (auth_retry_get() == AR_NOINTERACT)
    {
        sec = 10;
    }

    /* Slow down reconnection after 5 retries per remote -- for TCP client or UDP tls-client only */
    if (c->options.ce.proto == PROTO_TCP_CLIENT
        || (c->options.ce.proto == PROTO_UDP && c->options.tls_client))
    {
        backoff = (c->options.unsuccessful_attempts / c->options.connection_list->len) - 4;
        if (backoff > 0)
        {
            /* sec is less than 2^16; we can left shift it by up to 15 bits without overflow */
            sec = max_int(sec, 1) << min_int(backoff, 15);
        }
        if (c->options.server_backoff_time)
        {
            sec = max_int(sec, c->options.server_backoff_time);
            c->options.server_backoff_time = 0;
        }

        if (sec > c->options.ce.connect_retry_seconds_max)
        {
            sec = c->options.ce.connect_retry_seconds_max;
        }
    }

    if (c->persist.restart_sleep_seconds > 0 && c->persist.restart_sleep_seconds > sec)
    {
        sec = c->persist.restart_sleep_seconds;
    }
    else if (c->persist.restart_sleep_seconds == -1)
    {
        sec = 0;
    }
    c->persist.restart_sleep_seconds = 0;

    /* do management hold on context restart, i.e. second, third, fourth, etc. initialization */
    if (do_hold(sec))
    {
        sec = 0;
    }

    if (sec)
    {
        msg(D_RESTART, "Restart pause, %d second(s)", sec);
        management_sleep(sec);
    }
}

/*
 * Do a possible pause on context_2 initialization.
 */
static void
do_startup_pause(struct context *c)
{
    if (!c->first_time)
    {
        socket_restart_pause(c);
    }
    else
    {
        do_hold(0); /* do management hold on first context initialization */
    }
}

static size_t
get_frame_mtu(struct context *c, const struct options *o)
{
    size_t mtu;

    if (o->ce.link_mtu_defined)
    {
        ASSERT(o->ce.link_mtu_defined);
        /* if we have a link mtu defined we calculate what the old code
         * would have come up with as tun-mtu */
        size_t overhead = frame_calculate_protocol_header_size(&c->c1.ks.key_type,
                                                               o, true);
        mtu = o->ce.link_mtu - overhead;

    }
    else
    {
        ASSERT(o->ce.tun_mtu_defined);
        mtu = o->ce.tun_mtu;
    }

    if (mtu < TUN_MTU_MIN)
    {
        msg(M_WARN, "TUN MTU value (%zu) must be at least %d", mtu, TUN_MTU_MIN);
        frame_print(&c->c2.frame, M_FATAL, "MTU is too small");
    }
    return mtu;
}

/*
 * Finalize MTU parameters based on command line or config file options.
 */
static void
frame_finalize_options(struct context *c, const struct options *o)
{
    if (!o)
    {
        o = &c->options;
    }

    struct frame *frame = &c->c2.frame;

    frame->tun_mtu = get_frame_mtu(c, o);
    frame->tun_max_mtu = o->ce.tun_mtu_max;

    /* max mtu needs to be at least as large as the tun mtu */
    frame->tun_max_mtu = max_int(frame->tun_mtu, frame->tun_max_mtu);

    /* We always allow at least 1600 MTU packets to be received in our buffer
     * space to allow server to push "baby giant" MTU sizes */
    frame->tun_max_mtu = max_int(1600, frame->tun_max_mtu);

    size_t payload_size = frame->tun_max_mtu;

    /* we need to be also large enough to hold larger control channel packets
     * if configured */
    payload_size = max_int(payload_size, o->ce.tls_mtu);

    /* The extra tun needs to be added to the payload size */
    if (o->ce.tun_mtu_defined)
    {
        payload_size += o->ce.tun_mtu_extra;
    }

    /* Add 32 byte of extra space in the buffer to account for small errors
     * in the calculation */
    payload_size += 32;


    /* the space that is reserved before the payload to add extra headers to it
     * we always reserve the space for the worst case */
    size_t headroom = 0;

    /* includes IV and packet ID */
    headroom += crypto_max_overhead();

    /* peer id + opcode */
    headroom += 4;

    /* socks proxy header */
    headroom += 10;

    /* compression header and fragment header (part of the encrypted payload) */
    headroom += 1 + 1;

    /* Round up headroom to the next multiple of 4 to ensure alignment */
    headroom = (headroom + 3) & ~3;

    /* Add the headroom to the payloadsize as a received (IP) packet can have
     * all the extra headers in it */
    payload_size += headroom;

    /* the space after the payload, this needs some extra buffer space for
     * encryption so headroom is probably too much but we do not really care
     * the few extra bytes */
    size_t tailroom = headroom;


    frame->buf.payload_size = payload_size;
    frame->buf.headroom = headroom;
    frame->buf.tailroom = tailroom;
}

/*
 * Free a key schedule, including OpenSSL components.
 */
static void
key_schedule_free(struct key_schedule *ks, bool free_ssl_ctx)
{
    free_key_ctx_bi(&ks->static_key);
    if (tls_ctx_initialised(&ks->ssl_ctx) && free_ssl_ctx)
    {
        tls_ctx_free(&ks->ssl_ctx);
        free_key_ctx(&ks->auth_token_key);
    }
    CLEAR(*ks);
}

static void
init_crypto_pre(struct context *c, const unsigned int flags)
{
    if (c->options.engine)
    {
        crypto_init_lib_engine(c->options.engine);
    }

    if (flags & CF_LOAD_PERSISTED_PACKET_ID)
    {
        /* load a persisted packet-id for cross-session replay-protection */
        if (c->options.packet_id_file)
        {
            packet_id_persist_load(&c->c1.pid_persist, c->options.packet_id_file);
        }
    }

}

/*
 * Static Key Mode (using a pre-shared key)
 */
static void
do_init_crypto_static(struct context *c, const unsigned int flags)
{
    const struct options *options = &c->options;
    ASSERT(options->shared_secret_file);

    init_crypto_pre(c, flags);

    /* Initialize flags */
    if (c->options.mute_replay_warnings)
    {
        c->c2.crypto_options.flags |= CO_MUTE_REPLAY_WARNINGS;
    }

    /* Initialize packet ID tracking */
    packet_id_init(&c->c2.crypto_options.packet_id,
                   options->replay_window,
                   options->replay_time,
                   "STATIC", 0);
    c->c2.crypto_options.pid_persist = &c->c1.pid_persist;
    c->c2.crypto_options.flags |= CO_PACKET_ID_LONG_FORM;
    packet_id_persist_load_obj(&c->c1.pid_persist,
                               &c->c2.crypto_options.packet_id);

    if (!key_ctx_bi_defined(&c->c1.ks.static_key))
    {
        /* Get cipher & hash algorithms */
        init_key_type(&c->c1.ks.key_type, options->ciphername, options->authname,
                      options->test_crypto, true);

        /* Read cipher and hmac keys from shared secret file */
        crypto_read_openvpn_key(&c->c1.ks.key_type, &c->c1.ks.static_key,
                                options->shared_secret_file,
                                options->shared_secret_file_inline,
                                options->key_direction, "Static Key Encryption",
                                "secret", NULL);
    }
    else
    {
        msg(M_INFO, "Re-using pre-shared static key");
    }

    /* Get key schedule */
    c->c2.crypto_options.key_ctx_bi = c->c1.ks.static_key;
}

/*
 * Initialize the tls-auth/crypt key context
 */
static void
do_init_tls_wrap_key(struct context *c)
{
    const struct options *options = &c->options;

    /* TLS handshake authentication (--tls-auth) */
    if (options->ce.tls_auth_file)
    {
        /* Initialize key_type for tls-auth with auth only */
        CLEAR(c->c1.ks.tls_auth_key_type);
        c->c1.ks.tls_auth_key_type.cipher = "none";
        c->c1.ks.tls_auth_key_type.digest = options->authname;
        if (!md_valid(options->authname))
        {
            msg(M_FATAL, "ERROR: tls-auth enabled, but no valid --auth "
                "algorithm specified ('%s')", options->authname);
        }

        crypto_read_openvpn_key(&c->c1.ks.tls_auth_key_type,
                                &c->c1.ks.tls_wrap_key,
                                options->ce.tls_auth_file,
                                options->ce.tls_auth_file_inline,
                                options->ce.key_direction,
                                "Control Channel Authentication", "tls-auth",
                                &c->c1.ks.original_wrap_keydata);
    }

    /* TLS handshake encryption+authentication (--tls-crypt) */
    if (options->ce.tls_crypt_file)
    {
        tls_crypt_init_key(&c->c1.ks.tls_wrap_key,
                           &c->c1.ks.original_wrap_keydata,
                           options->ce.tls_crypt_file,
                           options->ce.tls_crypt_file_inline,
                           options->tls_server);
    }

    /* tls-crypt with client-specific keys (--tls-crypt-v2) */
    if (options->ce.tls_crypt_v2_file)
    {
        if (options->tls_server)
        {
            tls_crypt_v2_init_server_key(&c->c1.ks.tls_crypt_v2_server_key,
                                         true, options->ce.tls_crypt_v2_file,
                                         options->ce.tls_crypt_v2_file_inline);
        }
        else
        {
            tls_crypt_v2_init_client_key(&c->c1.ks.tls_wrap_key,
                                         &c->c1.ks.original_wrap_keydata,
                                         &c->c1.ks.tls_crypt_v2_wkc,
                                         options->ce.tls_crypt_v2_file,
                                         options->ce.tls_crypt_v2_file_inline);
        }
        /* We have to ensure that the loaded tls-crypt key is small enough
         * to fit into the initial hard reset v3 packet */
        int wkc_len = buf_len(&c->c1.ks.tls_crypt_v2_wkc);

        /* empty ACK/message id, tls-crypt, Opcode, UDP, ipv6 */
        int required_size = 5 + wkc_len + tls_crypt_buf_overhead() + 1 + 8 + 40;

        if (required_size > c->options.ce.tls_mtu)
        {
            msg(M_WARN, "ERROR: tls-crypt-v2 client key too large to work with "
                "requested --max-packet-size %d, requires at least "
                "--max-packet-size %d. Packets will ignore requested "
                "maximum packet size", c->options.ce.tls_mtu,
                required_size);
        }
    }


}

/*
 * Initialize the persistent component of OpenVPN's TLS mode,
 * which is preserved across SIGUSR1 resets.
 */
static void
do_init_crypto_tls_c1(struct context *c)
{
    const struct options *options = &c->options;

    if (!tls_ctx_initialised(&c->c1.ks.ssl_ctx))
    {
        /*
         * Initialize the OpenSSL library's global
         * SSL context.
         */
        init_ssl(options, &(c->c1.ks.ssl_ctx), c->c0 && c->c0->uid_gid_chroot_set);
        if (!tls_ctx_initialised(&c->c1.ks.ssl_ctx))
        {
            switch (auth_retry_get())
            {
                case AR_NONE:
                    msg(M_FATAL, "Error: private key password verification failed");
                    break;

                case AR_INTERACT:
                    ssl_purge_auth(false);
                /* Intentional [[fallthrough]]; */

                case AR_NOINTERACT:
                    /* SOFT-SIGUSR1 -- Password failure error */
                    register_signal(c->sig, SIGUSR1, "private-key-password-failure");
                    break;

                default:
                    ASSERT(0);
            }
            return;
        }

        /*
         * BF-CBC is allowed to be used only when explicitly configured
         * as NCP-fallback or when NCP has been disabled or explicitly
         * allowed in the in ncp_ciphers list.
         * In all other cases do not attempt to initialize BF-CBC as it
         * may not even be supported by the underlying SSL library.
         *
         * Therefore, the key structure has to be initialized when:
         * - any non-BF-CBC cipher was selected; or
         * - BF-CBC is selected, NCP is enabled and fallback is enabled
         *   (BF-CBC will be the fallback).
         * - BF-CBC is in data-ciphers and we negotiate to use BF-CBC:
         *   If the negotiated cipher and options->ciphername are the
         *   same we do not reinit the cipher
         *
         * Note that BF-CBC will still be part of the OCC string to retain
         * backwards compatibility with older clients.
         */
        const char *ciphername = options->ciphername;
        if (streq(options->ciphername, "BF-CBC")
            && !tls_item_in_cipher_list("BF-CBC", options->ncp_ciphers)
            && !options->enable_ncp_fallback)
        {
            ciphername = "none";
        }

        /* Do not warn if the cipher is used only in OCC */
        bool warn = options->enable_ncp_fallback;
        init_key_type(&c->c1.ks.key_type, ciphername, options->authname,
                      true, warn);

        /* initialize tls-auth/crypt/crypt-v2 key */
        do_init_tls_wrap_key(c);

        /* initialise auth-token crypto support */
        if (c->options.auth_token_generate)
        {
            auth_token_init_secret(&c->c1.ks.auth_token_key,
                                   c->options.auth_token_secret_file,
                                   c->options.auth_token_secret_file_inline);
        }

    }
    else
    {
        msg(D_INIT_MEDIUM, "Re-using SSL/TLS context");

        /*
         * tls-auth/crypt key can be configured per connection block, therefore
         * we must reload it as it may have changed
         */
        do_init_tls_wrap_key(c);
    }
}

static void
do_init_crypto_tls(struct context *c, const unsigned int flags)
{
    const struct options *options = &c->options;
    struct tls_options to;
    bool packet_id_long_form;

    ASSERT(options->tls_server || options->tls_client);
    ASSERT(!options->test_crypto);

    init_crypto_pre(c, flags);

    /* Make sure we are either a TLS client or server but not both */
    ASSERT(options->tls_server == !options->tls_client);

    /* initialize persistent component */
    do_init_crypto_tls_c1(c);
    if (IS_SIG(c))
    {
        return;
    }

    /* In short form, unique datagram identifier is 32 bits, in long form 64 bits */
    packet_id_long_form = cipher_kt_mode_ofb_cfb(c->c1.ks.key_type.cipher);

    /* Set all command-line TLS-related options */
    CLEAR(to);

    if (options->mute_replay_warnings)
    {
        to.crypto_flags |= CO_MUTE_REPLAY_WARNINGS;
    }

    to.crypto_flags &= ~(CO_PACKET_ID_LONG_FORM);
    if (packet_id_long_form)
    {
        to.crypto_flags |= CO_PACKET_ID_LONG_FORM;
    }

    to.ssl_ctx = c->c1.ks.ssl_ctx;
    to.key_type = c->c1.ks.key_type;
    to.server = options->tls_server;
    to.replay_window = options->replay_window;
    to.replay_time = options->replay_time;
    to.tcp_mode = link_socket_proto_connection_oriented(options->ce.proto);
    to.config_ciphername = c->options.ciphername;
    to.config_ncp_ciphers = c->options.ncp_ciphers;
    to.transition_window = options->transition_window;
    to.handshake_window = options->handshake_window;
    to.packet_timeout = options->tls_timeout;
    to.renegotiate_bytes = options->renegotiate_bytes;
    to.renegotiate_packets = options->renegotiate_packets;
    if (options->renegotiate_seconds_min < 0)
    {
        /* Add 10% jitter to reneg-sec by default (server side only) */
        int auto_jitter = options->mode != MODE_SERVER ? 0 :
                          get_random() % max_int(options->renegotiate_seconds / 10, 1);
        to.renegotiate_seconds = options->renegotiate_seconds - auto_jitter;
    }
    else
    {
        /* Add user-specified jitter to reneg-sec */
        to.renegotiate_seconds = options->renegotiate_seconds
                                 -(get_random() % max_int(options->renegotiate_seconds
                                                          - options->renegotiate_seconds_min, 1));
    }
    to.single_session = options->single_session;
    to.mode = options->mode;
    to.pull = options->pull;
    if (options->push_peer_info)        /* all there is */
    {
        to.push_peer_info_detail = 3;
    }
    else if (options->pull)             /* pull clients send some details */
    {
        to.push_peer_info_detail = 2;
    }
    else if (options->mode == MODE_SERVER) /* server: no peer info at all */
    {
        to.push_peer_info_detail = 0;
    }
    else                  /* default: minimal info to allow NCP in P2P mode */
    {
        to.push_peer_info_detail = 1;
    }


    /* should we not xmit any packets until we get an initial
     * response from client? */
    if (to.server && options->ce.proto == PROTO_TCP_SERVER)
    {
        to.xmit_hold = true;
    }

    to.verify_command = options->tls_verify;
    to.verify_x509_type = (options->verify_x509_type & 0xff);
    to.verify_x509_name = options->verify_x509_name;
    to.crl_file = options->crl_file;
    to.crl_file_inline = options->crl_file_inline;
    to.ssl_flags = options->ssl_flags;
    to.ns_cert_type = options->ns_cert_type;
    memcpy(to.remote_cert_ku, options->remote_cert_ku, sizeof(to.remote_cert_ku));
    to.remote_cert_eku = options->remote_cert_eku;
    to.verify_hash = options->verify_hash;
    to.verify_hash_algo = options->verify_hash_algo;
    to.verify_hash_depth = options->verify_hash_depth;
    to.verify_hash_no_ca = options->verify_hash_no_ca;
    to.x509_username_field[0] = X509_USERNAME_FIELD_DEFAULT;
    to.es = c->c2.es;
    to.net_ctx = &c->net_ctx;


    to.plugins = c->plugins;


    to.auth_user_pass_verify_script = options->auth_user_pass_verify_script;
    to.auth_user_pass_verify_script_via_file = options->auth_user_pass_verify_script_via_file;
    to.client_crresponse_script = options->client_crresponse_script;
    to.tmp_dir = options->tmp_dir;
    to.export_peer_cert_dir = options->tls_export_peer_cert_dir;
    if (options->ccd_exclusive)
    {
        to.client_config_dir_exclusive = options->client_config_dir;
    }
    to.auth_user_pass_file = options->auth_user_pass_file;
    to.auth_user_pass_file_inline = options->auth_user_pass_file_inline;
    to.auth_token_generate = options->auth_token_generate;
    to.auth_token_lifetime = options->auth_token_lifetime;
    to.auth_token_renewal = options->auth_token_renewal;
    to.auth_token_call_auth = options->auth_token_call_auth;
    to.auth_token_key = c->c1.ks.auth_token_key;

    to.x509_track = options->x509_track;




    /* TLS handshake authentication (--tls-auth) */
    if (options->ce.tls_auth_file)
    {
        to.tls_wrap.mode = TLS_WRAP_AUTH;
    }

    /* TLS handshake encryption (--tls-crypt) */
    if (options->ce.tls_crypt_file
        || (options->ce.tls_crypt_v2_file && options->tls_client))
    {
        to.tls_wrap.mode = TLS_WRAP_CRYPT;
    }

    if (to.tls_wrap.mode == TLS_WRAP_AUTH || to.tls_wrap.mode == TLS_WRAP_CRYPT)
    {
        to.tls_wrap.opt.key_ctx_bi = c->c1.ks.tls_wrap_key;
        to.tls_wrap.opt.pid_persist = &c->c1.pid_persist;
        to.tls_wrap.opt.flags |= CO_PACKET_ID_LONG_FORM;
        to.tls_wrap.original_wrap_keydata = c->c1.ks.original_wrap_keydata;
    }

    if (options->ce.tls_crypt_v2_file)
    {
        to.tls_crypt_v2 = true;
        to.tls_wrap.tls_crypt_v2_wkc = &c->c1.ks.tls_crypt_v2_wkc;

        if (options->tls_server)
        {
            to.tls_wrap.tls_crypt_v2_server_key = c->c1.ks.tls_crypt_v2_server_key;
            to.tls_crypt_v2_verify_script = c->options.tls_crypt_v2_verify_script;
            if (options->ce.tls_crypt_v2_force_cookie)
            {
                to.tls_wrap.opt.flags |= CO_FORCE_TLSCRYPTV2_COOKIE;
            }
        }
    }

    /* let the TLS engine know if keys have to be installed in DCO or not */
    to.dco_enabled = dco_enabled(options);

    /*
     * Initialize OpenVPN's master TLS-mode object.
     */
    if (flags & CF_INIT_TLS_MULTI)
    {
        c->c2.tls_multi = tls_multi_init(&to);
        /* inherit the dco context from the tuntap object */
        if (c->c1.tuntap)
        {
            c->c2.tls_multi->dco = &c->c1.tuntap->dco;
        }
    }

    if (flags & CF_INIT_TLS_AUTH_STANDALONE)
    {
        c->c2.tls_auth_standalone = tls_auth_standalone_init(&to, &c->c2.gc);
        c->c2.session_id_hmac = session_id_hmac_init();
    }
}

static void
do_init_frame_tls(struct context *c)
{
    if (c->c2.tls_multi)
    {
        tls_multi_init_finalize(c->c2.tls_multi, c->options.ce.tls_mtu);
        ASSERT(c->c2.tls_multi->opt.frame.buf.payload_size <=
               c->c2.frame.buf.payload_size);
        frame_print(&c->c2.tls_multi->opt.frame, D_MTU_INFO,
                    "Control Channel MTU parms");

        /* Keep the max mtu also in the frame of tls multi so it can access
         * it in push_peer_info */
        c->c2.tls_multi->opt.frame.tun_max_mtu = c->c2.frame.tun_max_mtu;
    }
    if (c->c2.tls_auth_standalone)
    {
        tls_init_control_channel_frame_parameters(&c->c2.tls_auth_standalone->frame, c->options.ce.tls_mtu);
        frame_print(&c->c2.tls_auth_standalone->frame, D_MTU_INFO,
                    "TLS-Auth MTU parms");
        c->c2.tls_auth_standalone->tls_wrap.work = alloc_buf_gc(BUF_SIZE(&c->c2.frame), &c->c2.gc);
        c->c2.tls_auth_standalone->workbuf = alloc_buf_gc(BUF_SIZE(&c->c2.frame), &c->c2.gc);
    }
}

/*
 * No encryption or authentication.
 */
static void
do_init_crypto_none(struct context *c)
{
    ASSERT(!c->options.test_crypto);

    /* Initialise key_type with auth/cipher "none", so the key_type struct is
     * valid */
    init_key_type(&c->c1.ks.key_type, "none", "none",
                  c->options.test_crypto, true);

    msg(M_WARN,
        "******* WARNING *******: All encryption and authentication features "
        "disabled -- All data will be tunnelled as clear text and will not be "
        "protected against man-in-the-middle changes. "
        "PLEASE DO RECONSIDER THIS CONFIGURATION!");
}

static void
do_init_crypto(struct context *c, const unsigned int flags)
{
    if (c->options.shared_secret_file)
    {
        do_init_crypto_static(c, flags);
    }
    else if (c->options.tls_server || c->options.tls_client)
    {
        do_init_crypto_tls(c, flags);
    }
    else                        /* no encryption or authentication. */
    {
        do_init_crypto_none(c);
    }
}

static void
do_init_frame(struct context *c)
{
    /*
     * Adjust frame size based on the --tun-mtu-extra parameter.
     */
    if (c->options.ce.tun_mtu_extra_defined)
    {
        c->c2.frame.extra_tun += c->options.ce.tun_mtu_extra;
    }

    /*
     * Fill in the blanks in the frame parameters structure,
     * make sure values are rational, etc.
     */
    frame_finalize_options(c, NULL);



}

static void
do_option_warnings(struct context *c)
{
    const struct options *o = &c->options;

    if (o->ping_send_timeout && !o->ping_rec_timeout)
    {
        msg(M_WARN, "WARNING: --ping should normally be used with --ping-restart or --ping-exit");
    }

    if (o->username || o->groupname || o->chroot_dir
        )
    {
        if (!o->persist_tun)
        {
            msg(M_WARN, "WARNING: you are using user/group/chroot/setcon without persist-tun -- this may cause restarts to fail");
        }
    }

    if (o->chroot_dir && !(o->username && o->groupname))
    {
        msg(M_WARN, "WARNING: you are using chroot without specifying user and group -- this may cause the chroot jail to be insecure");
    }

    if (o->pull && o->ifconfig_local && c->first_time)
    {
        msg(M_WARN, "WARNING: using --pull/--client and --ifconfig together is probably not what you want");
    }

    if (o->server_bridge_defined || o->server_bridge_proxy_dhcp)
    {
        msg(M_WARN, "NOTE: when bridging your LAN adapter with the TAP adapter, note that the new bridge adapter will often take on its own IP address that is different from what the LAN adapter was previously set to");
    }

    if (o->mode == MODE_SERVER)
    {
        if (o->duplicate_cn && o->client_config_dir)
        {
            msg(M_WARN, "WARNING: using --duplicate-cn and --client-config-dir together is probably not what you want");
        }
        if (o->duplicate_cn && o->ifconfig_pool_persist_filename)
        {
            msg(M_WARN, "WARNING: --ifconfig-pool-persist will not work with --duplicate-cn");
        }
        if (!o->keepalive_ping || !o->keepalive_timeout)
        {
            msg(M_WARN, "WARNING: --keepalive option is missing from server config");
        }
    }

    if (o->tls_server)
    {
        warn_on_use_of_common_subnets(&c->net_ctx);
    }
    if (o->tls_client
        && !o->tls_verify
        && o->verify_x509_type == VERIFY_X509_NONE
        && !(o->ns_cert_type & NS_CERT_CHECK_SERVER)
        && !o->remote_cert_eku
        && !(o->verify_hash_depth == 0 && o->verify_hash))
    {
        msg(M_WARN, "WARNING: No server certificate verification method has been enabled.  See http://openvpn.net/howto.html#mitm for more info.");
    }
    if (o->ns_cert_type)
    {
        msg(M_WARN, "WARNING: --ns-cert-type is DEPRECATED.  Use --remote-cert-tls instead.");
    }

    /* If a script is used, print appropriate warnings */
    if (o->user_script_used)
    {
        if (script_security() >= SSEC_SCRIPTS)
        {
            msg(M_WARN, "NOTE: the current --script-security setting may allow this configuration to call user-defined scripts");
        }
        else if (script_security() >= SSEC_PW_ENV)
        {
            msg(M_WARN, "WARNING: the current --script-security setting may allow passwords to be passed to scripts via environmental variables");
        }
        else
        {
            msg(M_WARN, "NOTE: starting with " PACKAGE_NAME " 2.1, '--script-security 2' or higher is required to call user-defined scripts or executables");
        }
    }
}

struct context_buffers *
init_context_buffers(const struct frame *frame)
{
    struct context_buffers *b;

    ALLOC_OBJ_CLEAR(b, struct context_buffers);

    size_t buf_size = BUF_SIZE(frame);

    b->read_link_buf = alloc_buf(buf_size);
    b->read_tun_buf = alloc_buf(buf_size);

    b->aux_buf = alloc_buf(buf_size);

    b->encrypt_buf = alloc_buf(buf_size);
    b->decrypt_buf = alloc_buf(buf_size);


    return b;
}

void
free_context_buffers(struct context_buffers *b)
{
    if (b)
    {
        free_buf(&b->read_link_buf);
        free_buf(&b->read_tun_buf);
        free_buf(&b->aux_buf);


        free_buf(&b->encrypt_buf);
        free_buf(&b->decrypt_buf);

        free(b);
    }
}

/*
 * Now that we know all frame parameters, initialize
 * our buffers.
 */
static void
do_init_buffers(struct context *c)
{
    c->c2.buffers = init_context_buffers(&c->c2.frame);
    c->c2.buffers_owned = true;
}


/*
 * Allocate our socket object.
 */
static void
do_link_socket_new(struct context *c)
{
    ASSERT(!c->c2.link_socket);
    c->c2.link_socket = link_socket_new();
    c->c2.link_socket_owned = true;
}

/*
 * Print MTU INFO
 */
static void
do_print_data_channel_mtu_parms(struct context *c)
{
    frame_print(&c->c2.frame, D_MTU_INFO, "Data Channel MTU parms");
}

/*
 * Get local and remote options compatibility strings.
 */
static void
do_compute_occ_strings(struct context *c)
{
    struct gc_arena gc = gc_new();

    c->c2.options_string_local =
        options_string(&c->options, &c->c2.frame, c->c1.tuntap, &c->net_ctx,
                       false, &gc);
    c->c2.options_string_remote =
        options_string(&c->options, &c->c2.frame, c->c1.tuntap, &c->net_ctx,
                       true, &gc);

    msg(D_SHOW_OCC, "Local Options String (VER=%s): '%s'",
        options_string_version(c->c2.options_string_local, &gc),
        c->c2.options_string_local);
    msg(D_SHOW_OCC, "Expected Remote Options String (VER=%s): '%s'",
        options_string_version(c->c2.options_string_remote, &gc),
        c->c2.options_string_remote);

    if (c->c2.tls_multi)
    {
        tls_multi_init_set_options(c->c2.tls_multi,
                                   c->c2.options_string_local,
                                   c->c2.options_string_remote);
    }

    gc_free(&gc);
}

/*
 * These things can only be executed once per program instantiation.
 * Set up for possible UID/GID downgrade, but don't do it yet.
 * Daemonize if requested.
 */
static void
do_init_first_time(struct context *c)
{
    if (c->first_time && !c->c0)
    {
        struct context_0 *c0;

        ALLOC_OBJ_CLEAR_GC(c->c0, struct context_0, &c->gc);
        c0 = c->c0;

        /* get user and/or group that we want to setuid/setgid to,
         * sets also platform_x_state */
        bool group_defined =  platform_group_get(c->options.groupname,
                                                 &c0->platform_state_group);
        bool user_defined = platform_user_get(c->options.username,
                                              &c0->platform_state_user);

        c0->uid_gid_specified = user_defined || group_defined;

        /* perform postponed chdir if --daemon */
        if (c->did_we_daemonize && c->options.cd_dir == NULL)
        {
            platform_chdir("/");
        }

        /* should we change scheduling priority? */
        platform_nice(c->options.nice);
    }
}

/*
 * free buffers
 */
static void
do_close_free_buf(struct context *c)
{
    if (c->c2.buffers_owned)
    {
        free_context_buffers(c->c2.buffers);
        c->c2.buffers = NULL;
        c->c2.buffers_owned = false;
    }
}

/*
 * close TLS
 */
static void
do_close_tls(struct context *c)
{
    if (c->c2.tls_multi)
    {
        tls_multi_free(c->c2.tls_multi, true);
        c->c2.tls_multi = NULL;
    }

    /* free options compatibility strings */
    free(c->c2.options_string_local);
    free(c->c2.options_string_remote);

    c->c2.options_string_local = c->c2.options_string_remote = NULL;

    if (c->c2.pulled_options_state)
    {
        md_ctx_cleanup(c->c2.pulled_options_state);
        md_ctx_free(c->c2.pulled_options_state);
    }

    tls_auth_standalone_free(c->c2.tls_auth_standalone);
}

/*
 * Free key schedules
 */
static void
do_close_free_key_schedule(struct context *c, bool free_ssl_ctx)
{
    /*
     * always free the tls_auth/crypt key. The key will
     * be reloaded from memory (pre-cached)
     */
    free_key_ctx(&c->c1.ks.tls_crypt_v2_server_key);
    free_key_ctx_bi(&c->c1.ks.tls_wrap_key);
    CLEAR(c->c1.ks.tls_wrap_key);
    buf_clear(&c->c1.ks.tls_crypt_v2_wkc);
    free_buf(&c->c1.ks.tls_crypt_v2_wkc);

    if (!(c->sig->signal_received == SIGUSR1))
    {
        key_schedule_free(&c->c1.ks, free_ssl_ctx);
    }
}

/*
 * Close TCP/UDP connection
 */
static void
do_close_link_socket(struct context *c)
{
    /* in dco-win case, link socket is a tun handle which is
     * closed in do_close_tun(). Set it to UNDEFINED so
     * we won't use WinSock API to close it. */
    if (tuntap_is_dco_win(c->c1.tuntap) && c->c2.link_socket)
    {
        c->c2.link_socket->sd = SOCKET_UNDEFINED;
    }

    if (c->c2.link_socket && c->c2.link_socket_owned)
    {
        link_socket_close(c->c2.link_socket);
        c->c2.link_socket = NULL;
    }


    /* Preserve the resolved list of remote if the user request to or if we want
     * reconnect to the same host again or there are still addresses that need
     * to be tried */
    if (!(c->sig->signal_received == SIGUSR1
          && ( (c->options.persist_remote_ip)
               ||
               ( c->sig->source != SIG_SOURCE_HARD
                 && ((c->c1.link_socket_addr.current_remote
                      && c->c1.link_socket_addr.current_remote->ai_next)
                     || c->options.no_advance))
               )))
    {
        clear_remote_addrlist(&c->c1.link_socket_addr, !c->options.resolve_in_advance);
    }

    /* Clear the remote actual address when persist_remote_ip is not in use */
    if (!(c->sig->signal_received == SIGUSR1 && c->options.persist_remote_ip))
    {
        CLEAR(c->c1.link_socket_addr.actual);
    }

    if (!(c->sig->signal_received == SIGUSR1 && c->options.persist_local_ip))
    {
        if (c->c1.link_socket_addr.bind_local && !c->options.resolve_in_advance)
        {
            freeaddrinfo(c->c1.link_socket_addr.bind_local);
        }

        c->c1.link_socket_addr.bind_local = NULL;
    }
}

/*
 * Close packet-id persistence file
 */
static void
do_close_packet_id(struct context *c)
{
    packet_id_free(&c->c2.crypto_options.packet_id);
    packet_id_persist_save(&c->c1.pid_persist);
    if (!(c->sig->signal_received == SIGUSR1))
    {
        packet_id_persist_close(&c->c1.pid_persist);
    }
}


/*
 * Open and close our event objects.
 */

static void
do_event_set_init(struct context *c,
                  bool need_us_timeout)
{
    unsigned int flags = 0;

    c->c2.event_set_max = BASE_N_EVENTS;

    flags |= EVENT_METHOD_FAST;

    if (need_us_timeout)
    {
        flags |= EVENT_METHOD_US_TIMEOUT;
    }

    c->c2.event_set = event_set_init(&c->c2.event_set_max, flags);
    c->c2.event_set_owned = true;
}

static void
do_close_event_set(struct context *c)
{
    if (c->c2.event_set && c->c2.event_set_owned)
    {
        event_free(c->c2.event_set);
        c->c2.event_set = NULL;
        c->c2.event_set_owned = false;
    }
}

/*
 * Open and close --status file
 */

static void
do_open_status_output(struct context *c)
{
    if (!c->c1.status_output)
    {
        c->c1.status_output = status_open(c->options.status_file,
                                          c->options.status_file_update_freq,
                                          -1,
                                          NULL,
                                          STATUS_OUTPUT_WRITE);
        c->c1.status_output_owned = true;
    }
}

static void
do_close_status_output(struct context *c)
{
    if (!(c->sig->signal_received == SIGUSR1))
    {
        if (c->c1.status_output_owned && c->c1.status_output)
        {
            status_close(c->c1.status_output);
            c->c1.status_output = NULL;
            c->c1.status_output_owned = false;
        }
    }
}

/*
 * Handle ifconfig-pool persistence object.
 */
static void
do_open_ifconfig_pool_persist(struct context *c)
{
    if (!c->c1.ifconfig_pool_persist && c->options.ifconfig_pool_persist_filename)
    {
        c->c1.ifconfig_pool_persist = ifconfig_pool_persist_init(c->options.ifconfig_pool_persist_filename,
                                                                 c->options.ifconfig_pool_persist_refresh_freq);
        c->c1.ifconfig_pool_persist_owned = true;
    }
}

static void
do_close_ifconfig_pool_persist(struct context *c)
{
    if (!(c->sig->signal_received == SIGUSR1))
    {
        if (c->c1.ifconfig_pool_persist && c->c1.ifconfig_pool_persist_owned)
        {
            ifconfig_pool_persist_close(c->c1.ifconfig_pool_persist);
            c->c1.ifconfig_pool_persist = NULL;
            c->c1.ifconfig_pool_persist_owned = false;
        }
    }
}

/*
 * Inherit environmental variables
 */

static void
do_inherit_env(struct context *c, const struct env_set *src)
{
    c->c2.es = env_set_create(NULL);
    c->c2.es_owned = true;
    env_set_inherit(c->c2.es, src);
}

static void
do_env_set_destroy(struct context *c)
{
    if (c->c2.es && c->c2.es_owned)
    {
        env_set_destroy(c->c2.es);
        c->c2.es = NULL;
        c->c2.es_owned = false;
    }
}

/*
 * Fast I/O setup.  Fast I/O is an optimization which only works
 * if all of the following are true:
 *
 * (1) The platform is not Windows
 * (2) --proto udp is enabled
 * (3) --shaper is disabled
 */
static void
do_setup_fast_io(struct context *c)
{
    if (c->options.fast_io)
    {
        if (!proto_is_udp(c->options.ce.proto))
        {
            msg(M_INFO, "NOTE: --fast-io is disabled since we are not using UDP");
        }
        else
        {
            if (c->options.shaper)
            {
                msg(M_INFO, "NOTE: --fast-io is disabled since we are using --shaper");
            }
            else
            {
                c->c2.fast_io = true;
            }
        }
    }
}

static void
do_signal_on_tls_errors(struct context *c)
{
    if (c->options.tls_exit)
    {
        c->c2.tls_exit_signal = SIGTERM;
    }
    else
    {
        c->c2.tls_exit_signal = SIGUSR1;
    }
}



void
init_management_callback_p2p(struct context *c)
{
}



void
uninit_management_callback(void)
{
}

void
persist_client_stats(struct context *c)
{
}

/*
 * Initialize a tunnel instance, handle pre and post-init
 * signal settings.
 */
void
init_instance_handle_signals(struct context *c, const struct env_set *env, const unsigned int flags)
{
    pre_init_signal_catch();
    init_instance(c, env, flags);
    post_init_signal_catch();

    /*
     * This is done so that signals thrown during
     * initialization can bring us back to
     * a management hold.
     */
    if (IS_SIG(c))
    {
        remap_signal(c);
        uninit_management_callback();
    }
}

/*
 * Initialize a tunnel instance.
 */
void
init_instance(struct context *c, const struct env_set *env, const unsigned int flags)
{
    const struct options *options = &c->options;
    const bool child = (c->mode == CM_CHILD_TCP || c->mode == CM_CHILD_UDP);
    int link_socket_mode = LS_MODE_DEFAULT;

    /* init garbage collection level */
    gc_init(&c->c2.gc);

    /* inherit environmental variables */
    if (env)
    {
        do_inherit_env(c, env);
    }

    if (c->mode == CM_P2P)
    {
        init_management_callback_p2p(c);
    }

    /* possible sleep or management hold if restart */
    if (c->mode == CM_P2P || c->mode == CM_TOP)
    {
        do_startup_pause(c);
        if (IS_SIG(c))
        {
            goto sig;
        }
    }

    if (c->options.resolve_in_advance)
    {
        do_preresolve(c);
        if (IS_SIG(c))
        {
            goto sig;
        }
    }

    /* Resets all values to the initial values from the config where needed */
    pre_connect_restore(&c->options, &c->c2.gc);

    /* map in current connection entry */
    next_connection_entry(c);

    /* link_socket_mode allows CM_CHILD_TCP
     * instances to inherit acceptable fds
     * from a top-level parent */
    if (c->options.ce.proto == PROTO_TCP_SERVER)
    {
        if (c->mode == CM_TOP)
        {
            link_socket_mode = LS_MODE_TCP_LISTEN;
        }
        else if (c->mode == CM_CHILD_TCP)
        {
            link_socket_mode = LS_MODE_TCP_ACCEPT_FROM;
        }
    }

    /* should we disable paging? */
    if (c->first_time && options->mlock)
    {
        platform_mlockall(true);
    }

    /* get passwords if undefined */
    if (auth_retry_get() == AR_INTERACT)
    {
        init_query_passwords(c);
    }

    /* initialize context level 2 --verb/--mute parms */
    init_verb_mute(c, IVM_LEVEL_2);

    /* set error message delay for non-server modes */
    if (c->mode == CM_P2P)
    {
        set_check_status_error_delay(P2P_ERROR_DELAY_MS);
    }

    /* warn about inconsistent options */
    if (c->mode == CM_P2P || c->mode == CM_TOP)
    {
        do_option_warnings(c);
    }


    /* should we enable fast I/O? */
    if (c->mode == CM_P2P || c->mode == CM_TOP)
    {
        do_setup_fast_io(c);
    }

    /* should we throw a signal on TLS errors? */
    do_signal_on_tls_errors(c);

    /* open --status file */
    if (c->mode == CM_P2P || c->mode == CM_TOP)
    {
        do_open_status_output(c);
    }

    /* open --ifconfig-pool-persist file */
    if (c->mode == CM_TOP)
    {
        do_open_ifconfig_pool_persist(c);
    }

    /* reset OCC state */
    if (c->mode == CM_P2P || child)
    {
        c->c2.occ_op = occ_reset_op();
    }

    /* our wait-for-i/o objects, different for posix vs. win32 */
    if (c->mode == CM_P2P)
    {
        do_event_set_init(c, SHAPER_DEFINED(&c->options));
    }
    else if (c->mode == CM_CHILD_TCP)
    {
        do_event_set_init(c, false);
    }

    /* initialize HTTP or SOCKS proxy object at scope level 2 */
    init_proxy(c);

    /* allocate our socket object */
    if (c->mode == CM_P2P || c->mode == CM_TOP || c->mode == CM_CHILD_TCP)
    {
        do_link_socket_new(c);
    }


    /* init crypto layer */
    {
        unsigned int crypto_flags = 0;
        if (c->mode == CM_TOP)
        {
            crypto_flags = CF_INIT_TLS_AUTH_STANDALONE;
        }
        else if (c->mode == CM_P2P)
        {
            crypto_flags = CF_LOAD_PERSISTED_PACKET_ID | CF_INIT_TLS_MULTI;
        }
        else if (child)
        {
            crypto_flags = CF_INIT_TLS_MULTI;
        }
        do_init_crypto(c, crypto_flags);
        if (IS_SIG(c) && !child)
        {
            goto sig;
        }
    }


    /* initialize MTU variables */
    do_init_frame(c);

    /* initialize TLS MTU variables */
    do_init_frame_tls(c);

    /* init workspace buffers whose size is derived from frame size */
    if (c->mode == CM_P2P || c->mode == CM_CHILD_TCP)
    {
        do_init_buffers(c);
    }


    /* bind the TCP/UDP socket */
    if (c->mode == CM_P2P || c->mode == CM_TOP || c->mode == CM_CHILD_TCP)
    {
        link_socket_init_phase1(c, link_socket_mode);
    }

    /* initialize tun/tap device object,
     * open tun/tap device, ifconfig, run up script, etc. */
    if (!(options->up_delay || PULL_DEFINED(options)) && (c->mode == CM_P2P || c->mode == CM_TOP))
    {
        int error_flags = 0;
        c->c2.did_open_tun = do_open_tun(c, &error_flags);
    }

    /* print MTU info */
    do_print_data_channel_mtu_parms(c);

    /* get local and remote options compatibility strings */
    if (c->mode == CM_P2P || child)
    {
        do_compute_occ_strings(c);
    }

    /* initialize output speed limiter */
    if (c->mode == CM_P2P)
    {
        do_init_traffic_shaper(c);
    }

    /* do one-time inits, and possibly become a daemon here */
    do_init_first_time(c);


    /* initialise connect timeout timer */
    do_init_server_poll_timeout(c);

    /* finalize the TCP/UDP socket */
    if (c->mode == CM_P2P || c->mode == CM_TOP || c->mode == CM_CHILD_TCP)
    {
        link_socket_init_phase2(c);


        /* Update dynamic frame calculation as exact transport socket information
         * (IP vs IPv6) may be only available after socket phase2 has finished.
         * This is only needed for --static or no crypto, NCP will recalculate this
         * in tls_session_update_crypto_params (P2MP) */
        frame_calculate_dynamic(&c->c2.frame, &c->c1.ks.key_type, &c->options,
                                get_link_socket_info(c));
    }

    /*
     * Actually do UID/GID downgrade, and chroot, if requested.
     * May be delayed by --client, --pull, or --up-delay.
     */
    do_uid_gid_chroot(c, c->c2.did_open_tun);

    /* initialize timers */
    if (c->mode == CM_P2P || child)
    {
        do_init_timers(c, false);
    }



    /* Check for signals */
    if (IS_SIG(c))
    {
        goto sig;
    }

    return;

sig:
    if (!c->sig->signal_text)
    {
        c->sig->signal_text = "init_instance";
    }
    close_context(c, -1, flags);
    return;
}

/*
 * Close a tunnel instance.
 */
void
close_instance(struct context *c)
{
    /* close event objects */
    do_close_event_set(c);

    if (c->mode == CM_P2P
        || c->mode == CM_CHILD_TCP
        || c->mode == CM_CHILD_UDP
        || c->mode == CM_TOP)
    {

        /* free buffers */
        do_close_free_buf(c);

        /* close peer for DCO if enabled, needs peer-id so must be done before
         * closing TLS contexts */
        dco_remove_peer(c);

        /* close TLS */
        do_close_tls(c);

        /* free key schedules */
        do_close_free_key_schedule(c, (c->mode == CM_P2P || c->mode == CM_TOP));

        /* close TCP/UDP connection */
        do_close_link_socket(c);

        /* close TUN/TAP device */
        do_close_tun(c, false);



        /* close packet-id persistence file */
        do_close_packet_id(c);

        /* close --status file */
        do_close_status_output(c);


        /* close --ifconfig-pool-persist obj */
        do_close_ifconfig_pool_persist(c);

        /* free up environmental variable store */
        do_env_set_destroy(c);

        /* close HTTP or SOCKS proxy */
        uninit_proxy(c);

        /* garbage collect */
        gc_free(&c->c2.gc);
    }
}

void
inherit_context_child(struct context *dest,
                      const struct context *src)
{
    CLEAR(*dest);

    /* proto_is_dgram will ASSERT(0) if proto is invalid */
    dest->mode = proto_is_dgram(src->options.ce.proto) ? CM_CHILD_UDP : CM_CHILD_TCP;

    dest->gc = gc_new();

    ALLOC_OBJ_CLEAR_GC(dest->sig, struct signal_info, &dest->gc);

    /* c1 init */
    packet_id_persist_init(&dest->c1.pid_persist);

    dest->c1.ks.key_type = src->c1.ks.key_type;
    /* inherit SSL context */
    dest->c1.ks.ssl_ctx = src->c1.ks.ssl_ctx;
    dest->c1.ks.tls_wrap_key = src->c1.ks.tls_wrap_key;
    dest->c1.ks.tls_auth_key_type = src->c1.ks.tls_auth_key_type;
    dest->c1.ks.tls_crypt_v2_server_key = src->c1.ks.tls_crypt_v2_server_key;
    /* inherit pre-NCP ciphers */
    dest->options.ciphername = src->options.ciphername;
    dest->options.authname = src->options.authname;

    /* inherit auth-token */
    dest->c1.ks.auth_token_key = src->c1.ks.auth_token_key;

    /* options */
    dest->options = src->options;
    options_detach(&dest->options);

    if (dest->mode == CM_CHILD_TCP)
    {
        /*
         * The CM_TOP context does the socket listen(),
         * and the CM_CHILD_TCP context does the accept().
         */
        dest->c2.accept_from = src->c2.link_socket;
    }


    /* context init */

    /* inherit tun/tap interface object now as it may be required
     * to initialize the DCO context in init_instance()
     */
    dest->c1.tuntap = src->c1.tuntap;

    init_instance(dest, src->c2.es, CC_NO_CLOSE | CC_USR1_TO_HUP);
    if (IS_SIG(dest))
    {
        return;
    }

    /* UDP inherits some extra things which TCP does not */
    if (dest->mode == CM_CHILD_UDP)
    {
        /* inherit buffers */
        dest->c2.buffers = src->c2.buffers;

        /* inherit parent link_socket and tuntap */
        dest->c2.link_socket = src->c2.link_socket;

        ALLOC_OBJ_GC(dest->c2.link_socket_info, struct link_socket_info, &dest->gc);
        *dest->c2.link_socket_info = src->c2.link_socket->info;

        /* locally override some link_socket_info fields */
        dest->c2.link_socket_info->lsa = &dest->c1.link_socket_addr;
        dest->c2.link_socket_info->connection_established = false;
    }
}

void
inherit_context_top(struct context *dest,
                    const struct context *src)
{
    /* copy parent */
    *dest = *src;

    /*
     * CM_TOP_CLONE will prevent close_instance from freeing or closing
     * resources owned by the parent.
     *
     * Also note that CM_TOP_CLONE context objects are
     * closed by multi_top_free in multi.c.
     */
    dest->mode = CM_TOP_CLONE;

    dest->first_time = false;
    dest->c0 = NULL;

    options_detach(&dest->options);
    gc_detach(&dest->gc);
    gc_detach(&dest->c2.gc);

    /* detach plugins */
    dest->plugins_owned = false;

    dest->c2.tls_multi = NULL;

    /* detach c1 ownership */
    dest->c1.tuntap_owned = false;
    dest->c1.status_output_owned = false;
    dest->c1.ifconfig_pool_persist_owned = false;

    /* detach c2 ownership */
    dest->c2.event_set_owned = false;
    dest->c2.link_socket_owned = false;
    dest->c2.buffers_owned = false;
    dest->c2.es_owned = false;

    dest->c2.event_set = NULL;
    if (proto_is_dgram(src->options.ce.proto))
    {
        do_event_set_init(dest, false);
    }

}

void
close_context(struct context *c, int sig, unsigned int flags)
{
    ASSERT(c);
    ASSERT(c->sig);

    if (sig >= 0)
    {
        register_signal(c->sig, sig, "close_context");
    }

    if (c->sig->signal_received == SIGUSR1)
    {
        if ((flags & CC_USR1_TO_HUP)
            || (c->sig->source == SIG_SOURCE_HARD && (flags & CC_HARD_USR1_TO_HUP)))
        {
            register_signal(c->sig, SIGHUP, "close_context usr1 to hup");
        }
    }

    if (!(flags & CC_NO_CLOSE))
    {
        close_instance(c);
    }

    if (flags & CC_GC_FREE)
    {
        context_gc_free(c);
    }
}

/* Write our PID to a file */
void
write_pid_file(const char *filename, const char *chroot_dir)
{
    if (filename)
    {
        unsigned int pid = 0;
        FILE *fp = platform_fopen(filename, "w");
        if (!fp)
        {
            msg(M_ERR, "Open error on pid file %s", filename);
            return;
        }

        pid = platform_getpid();
        fprintf(fp, "%u\n", pid);
        if (fclose(fp))
        {
            msg(M_ERR, "Close error on pid file %s", filename);
        }

        /* remember file name so it can be deleted "out of context" later */
        /* (the chroot case is more complex and not handled today) */
        if (!chroot_dir)
        {
            saved_pid_file_name = strdup(filename);
        }
    }
}

/* remove PID file on exit, called from openvpn_exit() */
void
remove_pid_file(void)
{
    if (saved_pid_file_name)
    {
        platform_unlink(saved_pid_file_name);
    }
}


/*
 * Do a loopback test
 * on the crypto subsystem.
 */
static void *
test_crypto_thread(void *arg)
{
    struct context *c = (struct context *) arg;
    const struct options *options = &c->options;

    ASSERT(options->test_crypto);
    init_verb_mute(c, IVM_LEVEL_1);
    context_init_1(c);
    next_connection_entry(c);
    do_init_crypto_static(c, 0);

    frame_finalize_options(c, options);

    test_crypto(&c->c2.crypto_options, &c->c2.frame);

    key_schedule_free(&c->c1.ks, true);
    packet_id_free(&c->c2.crypto_options.packet_id);

    context_gc_free(c);
    return NULL;
}

bool
do_test_crypto(const struct options *o)
{
    if (o->test_crypto)
    {
        struct context c;

        /* print version number */
        msg(M_INFO, "%s", title_string);

        context_clear(&c);
        c.options = *o;
        options_detach(&c.options);
        c.first_time = true;
        test_crypto_thread((void *) &c);
        return true;
    }
    return false;
}