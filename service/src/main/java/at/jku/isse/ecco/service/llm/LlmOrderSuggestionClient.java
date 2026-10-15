package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asks a local, OpenAI-compatible chat-completions endpoint (e.g. Ollama) - the one the "Import
 * from Git" feature suggestions use, see {@link LlmFeatureSuggestionClient} - for a plausible order
 * of the children of a node whose order no commit fixed (an ORDER warning of a checkout): lines a
 * checkout combining variants got from different variants at the same place, say.
 * <p>
 * Only ever a suggestion, which the user reviews in the reorder dialog and records (or not) - never
 * applied on its own; and only ever an order the node's order graph allows: {@link #respecting}
 * keeps every order the graph has fixed, taking the model's order only where the graph leaves a
 * choice. A failure (no endpoint, an unusable reply) is reported, never thrown.
 */
public final class LlmOrderSuggestionClient {

	private static final Logger LOGGER = Logger.getLogger(LlmOrderSuggestionClient.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** The most rows sent in one request: a suggestion for a window of a long file, not the whole file. */
	public static final int MAX_ROWS = 300;

	private static final String SYSTEM_PROMPT = """
			You order the lines of a source file. The file is part of a software product line: \
			several variants of it were committed, and a checkout combining variants got some lines \
			from different variants at the same place, so their order is open. Propose the order a \
			developer would most plausibly write - e.g. a declaration before its use, an opening \
			before its closing, related lines together, the order of similar neighbouring code.

			You are given the file's path and its lines in their current order, numbered from 1. \
			Lines marked with "*" may move; lines without it keep their position relative to each \
			other (moving one "*" line past them is fine). Use every number exactly once.

			Respond with ONLY a JSON object, exactly the key "order": the line numbers in your \
			order - no prose, no code fences, no explanation.

			Example input:
			File: src/main.c
			1   int main() {
			2 * init_logging();
			3 * init_config();
			4   run();

			Example output:
			{"order": [1, 3, 2, 4]}""";

	private final String endpointUrl;
	private final String modelName;

	public LlmOrderSuggestionClient(String endpointUrl, String modelName) {
		this.endpointUrl = endpointUrl;
		this.modelName = modelName;
	}

	/** One line (child) of the node: its text, and whether it may move. */
	public record Row(String text, boolean movable) {
	}

	/** The suggested order (indices into the rows, a permutation of them), or why there is none. */
	public record Suggestion(List<Integer> order, String failureReason) {
		public boolean succeeded() {
			return this.order != null;
		}
	}

	/** The model's order for [rows] of the file [path] (0-based indices); never throws. */
	public Suggestion suggestOrder(String path, List<Row> rows) {
		if (rows.size() > MAX_ROWS)
			return new Suggestion(null, rows.size() + " lines are too many for a suggestion (at most " + MAX_ROWS + ")");
		URI uri = LlmFeatureSuggestionClient.resolveChatCompletionsUri(this.endpointUrl);
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(uri)
					.header("Content-Type", "application/json")
					.timeout(Duration.ofMinutes(5)) // a local model may first have to load
					.POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(path, rows, this.modelName)))
					.build();
			HttpResponse<String> response;
			try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
				response = client.send(request, HttpResponse.BodyHandlers.ofString());
			}
			if (response.statusCode() / 100 != 2)
				return new Suggestion(null, "The LLM endpoint " + uri + " returned HTTP " + response.statusCode() + ": " + response.body());
			List<Integer> order = parseOrder(response.body(), rows.size());
			if (order == null)
				return new Suggestion(null, "The LLM's reply is no order of the " + rows.size() + " lines: " + response.body());
			return new Suggestion(order, null);
		} catch (IOException | InterruptedException | RuntimeException e) {
			if (e instanceof InterruptedException)
				Thread.currentThread().interrupt();
			LOGGER.log(Level.WARNING, "Error asking " + uri + " for an order", e);
			return new Suggestion(null, "Could not ask the LLM endpoint " + uri + ": " + e.getMessage());
		}
	}

	/** Network-free: the JSON body posted for [rows]. Package-visible for testing. */
	static String buildRequestBody(String path, List<Row> rows, String modelName) {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("model", modelName);
		root.put("stream", false);
		root.put("temperature", 0);
		root.putObject("response_format").put("type", "json_object");
		ArrayNode messages = root.putArray("messages");
		messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
		StringBuilder user = new StringBuilder("File: ").append(path).append('\n');
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			user.append(i + 1).append(row.movable() ? " * " : "   ").append(row.text().replace('\n', ' ')).append('\n');
		}
		messages.addObject().put("role", "user").put("content", user.toString());
		return root.toString();
	}

	/**
	 * Network-free: the order in a chat-completions reply, as 0-based indices - null unless it is a
	 * permutation of 1..[count] (as JSON, also inside prose or code fences). Package-visible for testing.
	 */
	static List<Integer> parseOrder(String responseBody, int count) {
		try {
			JsonNode choices = MAPPER.readTree(responseBody).path("choices");
			if (!choices.isArray() || choices.isEmpty())
				return null;
			String content = choices.get(0).path("message").path("content").asText("");
			int start = content.indexOf('{'), end = content.lastIndexOf('}');
			if (start < 0 || end <= start)
				return null;
			JsonNode order = MAPPER.readTree(content.substring(start, end + 1)).path("order");
			if (!order.isArray() || order.size() != count)
				return null;
			boolean[] seen = new boolean[count];
			List<Integer> indices = new ArrayList<>(count);
			for (JsonNode n : order) {
				if (!n.canConvertToInt())
					return null;
				int i = n.asInt() - 1;
				if (i < 0 || i >= count || seen[i])
					return null;
				seen[i] = true;
				indices.add(i);
			}
			return indices;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * The order closest to [preferred] (0-based indices) that keeps every fixed precedence:
	 * [mustPrecede][a][b] means row a stays before row b (the order graph fixed it). A topological
	 * sort of those precedences that takes the next row in [preferred]'s order whenever there is a
	 * choice - so a model that ignores the rules cannot break an order a commit fixed.
	 */
	public static List<Integer> respecting(List<Integer> preferred, boolean[][] mustPrecede) {
		int n = preferred.size();
		int[] rank = new int[n];
		for (int r = 0; r < n; r++)
			rank[preferred.get(r)] = r;
		int[] before = new int[n];
		for (int a = 0; a < n; a++)
			for (int b = 0; b < n; b++)
				if (a != b && mustPrecede[a][b])
					before[b]++;
		PriorityQueue<Integer> ready = new PriorityQueue<>(Comparator.comparingInt(i -> rank[i]));
		for (int i = 0; i < n; i++)
			if (before[i] == 0)
				ready.add(i);
		List<Integer> order = new ArrayList<>(n);
		while (!ready.isEmpty()) {
			int next = ready.poll();
			order.add(next);
			for (int b = 0; b < n; b++)
				if (b != next && mustPrecede[next][b] && --before[b] == 0)
					ready.add(b);
		}
		if (order.size() != n) // precedences in a cycle: cannot be, the order graph is acyclic
			throw new IllegalArgumentException("The fixed precedences contain a cycle.");
		return order;
	}
}
