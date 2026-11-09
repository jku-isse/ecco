package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.AdapterPreferences;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;

/**
 * Persists the local LLM endpoint the "Import from Git" feature calls to suggest a feature
 * configuration per imported commit (see {@code at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient}
 * in the same module). Deliberately no API key field: the endpoint is expected to be a local,
 * unauthenticated OpenAI-compatible server (e.g. Ollama). One that is not on the user's machine
 * gets code only once the user confirmed it ({@link #mayReceiveCode}, {@link #confirmEndpoint}). {@code endpointUrl} is just the
 * server's base address ({@code scheme://host:port}, no path) - {@link
 * at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient} always appends the specific
 * {@code /v1/chat/completions} or {@code /v1/models} path itself (see its {@code
 * resolveChatCompletionsUri}/{@code resolveModelsUri}), so any path the user does type here is
 * ignored rather than silently breaking one of the two calls. Matches the same small, per-user
 * runtime setting pattern {@link AdapterPreferences} already established (backed by {@link
 * Preferences}, e.g. the platform registry/plist) rather than the bundled {@code ecco.properties}
 * classpath resource.
 */
public final class LlmPreferences {

	private static final String ENDPOINT_URL_KEY = "llmEndpointUrl";
	private static final String MODEL_NAME_KEY = "llmModelName";
	private static final String CONFIRMED_ENDPOINT_KEY = "llmConfirmedEndpoint";

	private static final String DEFAULT_ENDPOINT_URL = "http://localhost:11434";

	/** A system property naming the endpoint for this process instead of the one stored (ecco git-import --llm-endpoint); not stored. */
	public static final String ENDPOINT_URL_PROPERTY = "ecco.llm.endpoint";
	/** A system property naming the model for this process instead of the one stored (ecco git-import --llm-model); not stored. */
	public static final String MODEL_NAME_PROPERTY = "ecco.llm.model";

	private LlmPreferences() {
	}

	public static String getEndpointUrl() {
		String given = System.getProperty(ENDPOINT_URL_PROPERTY);
		return given != null ? given : prefs().get(ENDPOINT_URL_KEY, DEFAULT_ENDPOINT_URL);
	}

	public static void setEndpointUrl(String endpointUrl) {
		prefs().put(ENDPOINT_URL_KEY, endpointUrl);
	}

	/** Blank by default, deliberately - forces the user to name a model they've actually pulled rather than guessing one for them. */
	public static String getModelName() {
		String given = System.getProperty(MODEL_NAME_PROPERTY);
		return given != null ? given : prefs().get(MODEL_NAME_KEY, "");
	}

	public static void setModelName(String modelName) {
		prefs().put(MODEL_NAME_KEY, modelName);
	}

	/**
	 * Whether [endpointUrl] is a server on the user's machine: its host is "localhost" (or a name
	 * under it), or an address of this machine (loopback, or one of its network interfaces). Host
	 * names are not looked up - another name counts as another machine, so it is confirmed once
	 * rather than trusted (NFR-S6d, gap #90).
	 */
	public static boolean isOnThisMachine(String endpointUrl) {
		String host = host(endpointUrl);
		if (host == null)
			return false;
		if (host.equals("localhost") || host.endsWith(".localhost"))
			return true;
		if (!IP_LITERAL.matcher(host).matches())
			return false;
		try {
			InetAddress address = InetAddress.getByName(host); // a literal: no lookup
			return address.isLoopbackAddress() || address.isAnyLocalAddress() || NetworkInterface.getByInetAddress(address) != null;
		} catch (UnknownHostException | SocketException e) {
			return false;
		}
	}

	/**
	 * Whether code may be sent to [endpointUrl] without asking: it is on the user's machine, or the
	 * user confirmed sending to it (see {@link #confirmEndpoint}).
	 */
	public static boolean mayReceiveCode(String endpointUrl) {
		if (isOnThisMachine(endpointUrl))
			return true;
		String server = server(endpointUrl);
		return server != null && server.equals(prefs().get(CONFIRMED_ENDPOINT_KEY, null));
	}

	/** Records that the user agreed to send code to [endpointUrl]'s server; asked again once another server is set. */
	public static void confirmEndpoint(String endpointUrl) {
		String server = server(endpointUrl);
		if (server != null)
			prefs().put(CONFIRMED_ENDPOINT_KEY, server);
	}

	/** Forgets the server confirmed with {@link #confirmEndpoint}. */
	public static void clearConfirmedEndpoint() {
		prefs().remove(CONFIRMED_ENDPOINT_KEY);
	}

	/** What the user is asked before code first goes to [endpointUrl], a server not on their machine. */
	public static String remoteEndpointWarning(String endpointUrl) {
		String host = host(endpointUrl);
		return "The LLM server " + (host != null ? host : endpointUrl) + " is not on this machine. Asking it sends the code, "
				+ "diffs and commit messages concerned to that server, and whoever runs it can keep them.";
	}

	/** IPv4 dotted quads and IPv6 addresses (which contain a colon), the only hosts checked without a lookup. */
	private static final Pattern IP_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}|.*:.*");

	/** [endpointUrl]'s host, lower case, without the brackets of an IPv6 address; null if it has none. */
	private static String host(String endpointUrl) {
		try {
			String host = URI.create(endpointUrl.trim()).getHost();
			if (host == null || host.isEmpty())
				return null;
			if (host.startsWith("[") && host.endsWith("]"))
				host = host.substring(1, host.length() - 1);
			return host.toLowerCase(Locale.ROOT);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	/** [endpointUrl]'s scheme, host and port - what a confirmation is for; null if it has no host. */
	private static String server(String endpointUrl) {
		String host = host(endpointUrl);
		if (host == null)
			return null;
		URI uri = URI.create(endpointUrl.trim());
		String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
		int port = uri.getPort() >= 0 ? uri.getPort() : scheme.equals("https") ? 443 : 80;
		return scheme + "://" + host + ":" + port;
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(LlmPreferences.class);
	}

}
