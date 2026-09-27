package at.jku.isse.ecco.service;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes "host:port" remote addresses - host names, IPv4 addresses and bracketed IPv6
 * addresses. Remotes, fork and sync all used "[a-zA-Z]+:[0-9]+", so an address like
 * "127.0.0.1:3770" or "my-host.example.org:80" was treated as a local path instead.
 */
public final class RemoteAddress {

    private static final Pattern HOST_PORT = Pattern.compile(
            "(?:\\[(?<ipv6>[0-9A-Fa-f:.]+)]|(?<host>[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*)):(?<port>[0-9]{1,5})");

    private RemoteAddress() {
    }

    /**
     * @return the (unresolved) socket address if {@code address} is a "host:port" address, empty
     * otherwise (e.g. a local repository path).
     */
    public static Optional<InetSocketAddress> parseHostPort(String address) {
        if (address == null)
            return Optional.empty();
        Matcher matcher = HOST_PORT.matcher(address.trim());
        if (!matcher.matches())
            return Optional.empty();
        int port = Integer.parseInt(matcher.group("port"));
        if (port < 1 || port > 65535)
            return Optional.empty();
        String host = matcher.group("ipv6") != null ? matcher.group("ipv6") : matcher.group("host");
        return Optional.of(InetSocketAddress.createUnresolved(host, port));
    }
}
