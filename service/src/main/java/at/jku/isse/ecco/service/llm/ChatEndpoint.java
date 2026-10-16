package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends a chat request in the OpenAI chat-completions shape to the configured server, and gives
 * the reply in that shape. For Ollama it goes to Ollama's own /api/chat instead, with the context
 * the prompt needs: through the OpenAI-compatible endpoint the context cannot be set, and Ollama
 * loads a model with a 4,096-token context unless its server is told otherwise - a longer prompt
 * then silently loses its beginning, the system prompt (a Git import's prompts have 5,000-9,000
 * tokens; on a MacBook every suggestion came back as nonsense).
 * <p>
 * The context asked for is the largest of what the prompt needs, what the model is loaded with,
 * and what was asked for before with this server and model - Ollama reloads a model whenever the
 * context changes, which on a shared server would also hit everyone else - at most the model's own
 * maximum.
 */
final class ChatEndpoint {

	private static final Logger LOGGER = Logger.getLogger(ChatEndpoint.class.getName());

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Per server: whether it is an Ollama server. */
	private static final Map<String, Boolean> OLLAMA = new ConcurrentHashMap<>();
	/** Per server and model: the model's maximum context, 0 if unknown. */
	private static final Map<String, Integer> MAX_CONTEXT = new ConcurrentHashMap<>();
	/** Per server and model: the largest context asked for so far. */
	private static final Map<String, Integer> ASKED = new ConcurrentHashMap<>();

	/** Tokens kept free for the answer if the request does not cap it. */
	private static final int ANSWER_TOKENS = 1024;
	/** The smallest context asked for. */
	private static final int MIN_CONTEXT = 8192;

	/** A reply: its HTTP status, and its body in the OpenAI chat-completions shape (as the server sent it, if not 2xx). */
	record Reply(int status, String body) {
	}

	private final String base;

	/** @param endpointUrl the server's address; any path is ignored */
	ChatEndpoint(String endpointUrl) {
		URI endpoint = URI.create(endpointUrl);
		this.base = endpoint.getScheme() + "://" + endpoint.getAuthority();
	}

	/** Sends {@code body}, an OpenAI chat-completions request. */
	Reply send(ObjectNode body, Duration timeout) throws IOException, InterruptedException {
		if (this.isOllama())
			return this.sendToOllama(body, timeout);
		HttpResponse<String> response = post(this.base + "/v1/chat/completions", body.toString(), timeout);
		return new Reply(response.statusCode(), response.body());
	}

	private boolean isOllama() {
		return OLLAMA.computeIfAbsent(this.base, base -> {
			try {
				HttpResponse<String> response = get(base + "/api/version");
				return response.statusCode() / 100 == 2 && MAPPER.readTree(response.body()).path("version").isTextual();
			} catch (IOException | RuntimeException e) {
				return false;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		});
	}

	private Reply sendToOllama(ObjectNode body, Duration timeout) throws IOException, InterruptedException {
		String model = body.path("model").asText();
		int answer = body.path("max_tokens").asInt(ANSWER_TOKENS);
		int needed = estimateTokens(body) + answer;
		int context = context(needed, this.loadedContext(model), ASKED.getOrDefault(this.base + " " + model, 0), this.maxContext(model));
		ASKED.merge(this.base + " " + model, context, Math::max);
		if (needed > context)
			LOGGER.warning("The prompt needs about " + needed + " tokens, but " + model + " takes at most " + context + ": its beginning is cut off.");

		HttpResponse<String> response = post(this.base + "/api/chat", ollamaRequest(body, context).toString(), timeout);
		if (response.statusCode() / 100 != 2)
			return new Reply(response.statusCode(), response.body());
		return new Reply(response.statusCode(), openAiReply(response.body()));
	}

	/** The context to ask for: the largest of what is needed (a power of two, at least {@value #MIN_CONTEXT}), what is loaded and what was asked for - at most {@code max} if known. Package-visible for testing. */
	static int context(int needed, int loaded, int asked, int max) {
		int size = MIN_CONTEXT;
		while (size < needed)
			size *= 2;
		size = Math.max(size, Math.max(loaded, asked));
		return max > 0 ? Math.min(size, max) : size;
	}

	/** Generous: about three characters per token, as for code. Package-visible for testing. */
	static int estimateTokens(ObjectNode body) {
		int characters = 0;
		for (JsonNode message : body.path("messages"))
			characters += message.path("content").asText().length();
		return characters / 3 + 1;
	}

	/** The OpenAI request {@code body} as an Ollama /api/chat request with context {@code context}. Package-visible for testing. */
	static ObjectNode ollamaRequest(ObjectNode body, int context) {
		ObjectNode request = MAPPER.createObjectNode();
		request.put("model", body.path("model").asText());
		request.set("messages", body.path("messages").deepCopy());
		request.put("stream", false);
		if ("json_object".equals(body.path("response_format").path("type").asText()))
			request.put("format", "json");
		ObjectNode options = request.putObject("options");
		options.put("num_ctx", context);
		if (body.has("temperature"))
			options.set("temperature", body.get("temperature"));
		if (body.has("max_tokens"))
			options.put("num_predict", body.get("max_tokens").asInt());
		return request;
	}

	/** An Ollama /api/chat reply in the OpenAI chat-completions shape. Package-visible for testing. */
	static String openAiReply(String ollamaReply) throws IOException {
		JsonNode reply = MAPPER.readTree(ollamaReply);
		ObjectNode converted = MAPPER.createObjectNode();
		converted.putArray("choices").addObject().putObject("message")
				.put("role", "assistant").put("content", reply.path("message").path("content").asText(""));
		ObjectNode usage = converted.putObject("usage");
		usage.put("prompt_tokens", reply.path("prompt_eval_count").asInt(-1));
		usage.put("completion_tokens", reply.path("eval_count").asInt(-1));
		return converted.toString();
	}

	/** The context {@code model} is loaded with now (Ollama's /api/ps), 0 if it is not loaded or that cannot be told. */
	private int loadedContext(String model) {
		try {
			HttpResponse<String> response = get(this.base + "/api/ps");
			if (response.statusCode() / 100 == 2)
				for (JsonNode loaded : MAPPER.readTree(response.body()).path("models"))
					if (model.equals(loaded.path("name").asText()) || model.equals(loaded.path("model").asText())
							|| (model + ":latest").equals(loaded.path("name").asText()))
						return loaded.path("context_length").asInt(0);
		} catch (IOException | RuntimeException e) {
			LOGGER.log(Level.FINE, "Could not ask " + this.base + " what is loaded", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return 0;
	}

	/** The model's maximum context (Ollama's /api/show, "*.context_length"), 0 if unknown. */
	private int maxContext(String model) {
		return MAX_CONTEXT.computeIfAbsent(this.base + " " + model, key -> {
			try {
				ObjectNode request = MAPPER.createObjectNode().put("model", model);
				HttpResponse<String> response = post(this.base + "/api/show", request.toString(), Duration.ofSeconds(10));
				if (response.statusCode() / 100 == 2)
					for (var fields = MAPPER.readTree(response.body()).path("model_info").fields(); fields.hasNext(); ) {
						var field = fields.next();
						if (field.getKey().endsWith(".context_length"))
							return field.getValue().asInt(0);
					}
			} catch (IOException | RuntimeException e) {
				LOGGER.log(Level.FINE, "Could not ask " + this.base + " about " + model, e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return 0;
		});
	}

	/**
	 * A fresh client per call: after one request hung during a network outage, every later one on a
	 * shared client failed with "Can't assign requested address" (see LlmFeatureSuggestionClient).
	 */
	private static HttpResponse<String> post(String uri, String body, Duration timeout) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).header("Content-Type", "application/json")
				.timeout(timeout).POST(HttpRequest.BodyPublishers.ofString(body)).build();
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	private static HttpResponse<String> get(String uri) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).timeout(Duration.ofSeconds(10)).GET().build();
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	/** Forgets what was learned about servers - for tests. */
	static void reset() {
		OLLAMA.clear();
		MAX_CONTEXT.clear();
		ASKED.clear();
	}
}
