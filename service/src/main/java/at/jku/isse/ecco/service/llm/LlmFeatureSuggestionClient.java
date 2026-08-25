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
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asks a local, OpenAI-compatible chat-completions endpoint (e.g. Ollama) to suggest a feature
 * configuration string for each of a batch of git commits, given their messages/diffs and the
 * features already known in the target ecco repository - the "Import from Git" feature's LLM
 * step. One request PER COMMIT, not one batched request for the whole range: classifying a single
 * commit is a small, self-contained task even a modest local model handles reliably, whereas
 * asking for a whole batch's worth of correctly-shaped, correctly-matched JSON entries in one
 * reply repeatedly proved unreliable in practice (a local 14B model was observed reformatting an
 * input commit's own fields back as fabricated output, or echoing hash fragments out of a diff's
 * "index" line, instead of classifying) - always on some commit that hadn't been part of whatever
 * batch had been tested before, i.e. an unbounded tail of failure modes rather than one fixable
 * bug. Per-commit calls remove the failure category entirely instead of chasing further instances
 * of it. Each commit is judged only against feature names that genuinely already existed before
 * the import started - growing that list with results discovered earlier in the same import was
 * tried and reverted: a local model would over-eagerly force-fit a later, unrelated commit into
 * whatever name had just been suggested, purely because it was the only one available yet, which
 * is worse than the naming drift it was meant to avoid.
 * <p>
 * Every suggestion is only ever a STARTING POINT a human reviews/edits before anything is
 * committed into ecco (see {@code ImportGitView}) - never applied automatically - so this class
 * is deliberately forgiving: a network failure, an unreachable endpoint, or a response that isn't
 * valid JSON all result in a blank suggestion for the affected commit(s) (the user still gets a
 * fully editable table and can fill configurations in by hand) rather than aborting the whole
 * import.
 */
public final class LlmFeatureSuggestionClient {

	private static final Logger LOGGER = Logger.getLogger(LlmFeatureSuggestionClient.class.getName());

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String SYSTEM_PROMPT = """
			You are a code-change analyst. Your task: classify a single software version-control \
			commit by which product-line feature(s) it belongs to, for a tool that reconstructs a \
			feature model from a sequence of variants. Read the actual change carefully before \
			deciding anything, the way you would investigate a real commit, not just skim its title.

			You will be given one commit's short id, its commit message, and a truncated diff, plus \
			the list of feature names already known in the target repository (may be empty for a \
			first import). Base your answer only on this given data - you have no ability to run \
			commands or fetch anything else, so never assume prior knowledge of this specific \
			project and never claim to have looked at files not shown here; everything you need is \
			already provided below.

			A "feature" is a user- or system-facing capability (e.g. "file-save", \
			"incremental-search"), not a single line, variable, or function in isolation. Read \
			through the whole diff - every changed file, function, and hunk - before deciding:
			- Genuinely NEW capability gets a new feature name.
			- A change that MODIFIES, FIXES a bug in, REFACTORS (behavior-preserving), or improves \
			the PERFORMANCE of something that already exists belongs to that EXISTING feature's \
			name, even if the diff looks quite different from the feature's original introduction - \
			do not invent a new name just because the code shape changed.
			- If the diff itself shows the change touching shared state, a shared file, or a common \
			data structure that another known feature also depends on, consider that feature \
			affected too and list it as well (comma-separated) - but only based on what the diff \
			actually shows here, never speculation about code not shown to you.

			Base your classification primarily on the actual code change in "change" - what files, \
			functions, and logic were actually added or modified - not just the wording of \
			"summary". A commit message can be vague, generic, or even misleading about what the \
			commit really does; the diff is the ground truth. Only fall back to the \
			message-derived guess if the diff itself is too sparse or unclear to tell (e.g. a pure \
			version-number or whitespace change).

			Pick a short, descriptive name of only ONE TO THREE words joined by underscore or \
			hyphen, using only letters, digits, underscore or hyphen - no spaces or other \
			punctuation - e.g. "login" or "shopping-cart", never something long or overspecific \
			like "add-password-hashing-to-login-form". Only reuse an existing name if this commit's \
			actual code change genuinely continues that same feature - never reuse one just because \
			it happens to be the only (or most recent) name already known; an unrelated change must \
			get its own new, accurately-descriptive name even when just one feature is known so far. \
			This applies even to a small or seemingly trivial commit (a version bump, a typo fix, a \
			comment tweak, ...) - never refuse to classify it.

			"documentation" and "misc" are two DIFFERENT fallbacks - do not blur them together: \
			- Use "documentation" ONLY when the changed file itself is a docs file: a README, a \
			markdown/text file, or a code comment. If the diff's file is source code (.c, .py, \
			.java, ...), it is NOT "documentation" just because the change is small or \
			administrative - e.g. bumping a "#define VERSION ..." line, removing a duplicate \
			#include, or a one-line build-config tweak are all source-code changes with no \
			documentation content, so they get "misc" (or a real feature name if the surrounding \
			code shows one), never "documentation".
			- Reserve "misc" for a source-code change with no other fit at all.

			Respond with ONLY a JSON object shaped exactly like the example below - always exactly \
			the one key "configuration" (a single string, never an object or array) - and nothing \
			else: no prose, no markdown code fences, no explanation, no questions back to the user, \
			no analysis report, no table. Never reformat or repeat the commit's "id", "summary" or \
			"change" back as JSON fields of your own - your only job is to output a "configuration" \
			guess, nothing about the commit itself.

			Example input:
			Known feature names: (none yet)

			Commit:
			id: a1b2c3d
			summary: Update code
			change:
			+def hash_password(password): return bcrypt.hashpw(password.encode(), bcrypt.gensalt())
			+def login(username, password): ...

			Example output:
			{"configuration": "login"}""";

	private final String endpointUrl;
	private final String modelName;
	private final HttpClient httpClient;

	public LlmFeatureSuggestionClient(String endpointUrl, String modelName) {
		this.endpointUrl = endpointUrl;
		this.modelName = modelName;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	/**
	 * Lists the model ids the endpoint's OpenAI-compatible {@code GET /v1/models} advertises
	 * (Ollama and most other OpenAI-compatible servers implement this), for the Settings dialog's
	 * model picker. Unlike {@link #suggestConfigurations}, this deliberately throws rather than
	 * swallowing failures - it's a direct, synchronous user action (clicking "Refresh"), not a
	 * background batch step that must keep going despite one bad commit.
	 */
	public List<String> listModels() throws IOException, InterruptedException {
		URI modelsUri = resolveModelsUri(this.endpointUrl);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(modelsUri)
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
		HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("LLM endpoint " + modelsUri + " returned HTTP " + response.statusCode() + ": " + response.body());
		}
		List<String> models = parseModelsResponse(response.body());
		if (models.isEmpty()) {
			throw new IOException("The endpoint's response did not list any models.");
		}
		return models;
	}

	/** {@code scheme://authority} of {@code endpointUrl} with {@code /v1/models} appended - mirrors {@link #resolveChatCompletionsUri}. Package-visible for testing. */
	static URI resolveModelsUri(String endpointUrl) {
		URI endpoint = URI.create(endpointUrl);
		return URI.create(endpoint.getScheme() + "://" + endpoint.getAuthority() + "/v1/models");
	}

	/** Parses an OpenAI-compatible {@code {"data":[{"id":"..."}]}} response into a sorted list of model ids. Package-visible for testing. */
	static List<String> parseModelsResponse(String responseBody) throws IOException {
		JsonNode root = MAPPER.readTree(responseBody);
		JsonNode data = root.path("data");
		List<String> modelIds = new ArrayList<>();
		if (data.isArray()) {
			for (JsonNode entry : data) {
				String id = entry.path("id").asText(null);
				if (id != null && !id.isBlank()) {
					modelIds.add(id);
				}
			}
		}
		modelIds.sort(String.CASE_INSENSITIVE_ORDER);
		return modelIds;
	}

	/** One commit's message/diff, as fed into the suggestion prompt. */
	public record CommitForSuggestion(String commitId, String message, String diff) {
	}

	/**
	 * One batch's outcome: {@code configurations} always has one entry per input commit, in the
	 * same order (blank where no suggestion is available), so callers can always populate a full,
	 * if partially-empty, review table. {@code failureReason} is non-null if the call for at least
	 * one commit failed outright (network error, non-2xx response, unparseable reply) - as opposed
	 * to the model legitimately leaving a commit blank - so a caller can tell those two very
	 * different situations apart instead of guessing from a blank entry alone.
	 */
	public record SuggestionBatch(List<String> configurations, String failureReason) {
	}

	/**
	 * Suggests one configuration string per commit, in the same order as {@code commitsOldestFirst},
	 * via one LLM call per commit. Deliberately does NOT grow {@code knownFeatureNames} with
	 * results discovered earlier in the same call: a local model was observed over-eagerly
	 * force-fitting a later, unrelated commit into whatever name had just been suggested, purely
	 * because it was the only (or most recent) one available - worse than the naming-drift it would
	 * otherwise avoid, since every suggestion here is human-reviewed anyway. Every commit is judged
	 * only against the feature names that genuinely already existed before this import started.
	 * Never throws.
	 */
	public SuggestionBatch suggestConfigurations(List<CommitForSuggestion> commitsOldestFirst, Collection<String> knownFeatureNames) {
		List<String> configurations = new ArrayList<>(commitsOldestFirst.size());
		List<String> failedCommitIds = new ArrayList<>();
		String lastFailureReason = null;

		for (CommitForSuggestion commit : commitsOldestFirst) {
			OneResult result = suggestOneConfiguration(commit, knownFeatureNames);
			configurations.add(result.configuration() == null ? "" : result.configuration());
			if (result.failureReason() != null) {
				failedCommitIds.add(commit.commitId());
				lastFailureReason = result.failureReason();
			}
		}

		String overallFailureReason = failedCommitIds.isEmpty() ? null :
				failedCommitIds.size() + " of " + commitsOldestFirst.size() + " commit(s) could not be classified (" +
						String.join(", ", failedCommitIds) + "). Last error: " + lastFailureReason;
		return new SuggestionBatch(configurations, overallFailureReason);
	}

	private record OneResult(String configuration, String failureReason) {
	}

	private OneResult suggestOneConfiguration(CommitForSuggestion commit, Collection<String> knownFeatureNames) {
		URI chatCompletionsUri = resolveChatCompletionsUri(this.endpointUrl);
		try {
			String requestBody = buildRequestBody(commit, knownFeatureNames, this.modelName);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(chatCompletionsUri)
					.header("Content-Type", "application/json")
					// generous on purpose: a local model that's been idle can take a while just to
					// load into memory before it answers at all, and that cold-start cost lands on
					// whichever commit happens to be first in the range - a real timeout observed
					// with the previous, tighter 2-minute budget
					.timeout(Duration.ofMinutes(5))
					.POST(HttpRequest.BodyPublishers.ofString(requestBody))
					.build();
			HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				String reason = "LLM endpoint " + chatCompletionsUri + " returned HTTP " + response.statusCode() + ": " + response.body();
				LOGGER.log(Level.WARNING, reason);
				return new OneResult(null, reason);
			}
			String configuration = parseResponse(response.body());
			if (configuration == null) {
				String reason = "The LLM's response for commit " + commit.commitId() + " could not be parsed as the " +
						"expected JSON shape. Raw reply: " + response.body();
				return new OneResult(null, reason);
			}
			return new OneResult(configuration, null);
		} catch (IOException | InterruptedException | RuntimeException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			String reason = "Error calling LLM endpoint " + chatCompletionsUri + " for commit " + commit.commitId() + ": " + e;
			LOGGER.log(Level.WARNING, reason, e);
			return new OneResult(null, reason);
		}
	}

	/**
	 * {@code scheme://authority} of {@code endpointUrl} with {@code /v1/chat/completions} appended -
	 * mirrors {@link #resolveModelsUri}, so the configured "endpoint" is always just the server's
	 * base address (whatever path the user happens to type after it, including none, is ignored and
	 * replaced) rather than something the user must get exactly right for chat vs. model-listing
	 * separately. Package-visible for testing.
	 */
	static URI resolveChatCompletionsUri(String endpointUrl) {
		URI endpoint = URI.create(endpointUrl);
		return URI.create(endpoint.getScheme() + "://" + endpoint.getAuthority() + "/v1/chat/completions");
	}

	/** Pure, network-free: the exact JSON body {@link #suggestConfigurations} POSTs for one commit. Package-visible for testing. */
	static String buildRequestBody(CommitForSuggestion commit, Collection<String> knownFeatureNames, String modelName) {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("model", modelName);
		root.put("stream", false);
		// deterministic, and steers the model away from a conversational/discursive reply
		root.put("temperature", 0);
		// standard OpenAI field, honored by Ollama and other compatible servers - without it, local
		// models can ignore the "respond with ONLY JSON" instruction
		root.putObject("response_format").put("type", "json_object");

		ArrayNode messages = root.putArray("messages");
		ObjectNode systemMessage = messages.addObject();
		systemMessage.put("role", "system");
		systemMessage.put("content", SYSTEM_PROMPT);

		ObjectNode userMessage = messages.addObject();
		userMessage.put("role", "user");
		userMessage.put("content", buildUserContent(commit, knownFeatureNames));

		return root.toString();
	}

	private static String buildUserContent(CommitForSuggestion commit, Collection<String> knownFeatureNames) {
		StringBuilder sb = new StringBuilder();
		sb.append("Known feature names: ");
		sb.append(knownFeatureNames.isEmpty() ? "(none yet)" : String.join(", ", knownFeatureNames));
		// deliberately NOT "commitId:"/"message:" as labels - those match output JSON keys closely
		// enough that a local model has been observed just reformatting this input record verbatim
		// into JSON instead of classifying it (see SYSTEM_PROMPT)
		sb.append("\n\nCommit:\nid: ").append(commit.commitId())
				.append("\nsummary: ").append(commit.message())
				.append("\nchange:\n").append(stripDiffNoise(commit.diff()));
		return sb.toString();
	}

	/**
	 * Strips the diff's "index &lt;sha&gt;..&lt;sha&gt; &lt;mode&gt;" line - pure git plumbing with
	 * no feature-classification signal, and one directly observed to distract a local model into
	 * echoing those hashes back as fabricated JSON fields instead of classifying the commit.
	 */
	static String stripDiffNoise(String diff) {
		StringBuilder result = new StringBuilder(diff.length());
		for (String line : diff.split("\n", -1)) {
			if (line.startsWith("index ")) {
				continue;
			}
			if (result.length() > 0) {
				result.append('\n');
			}
			result.append(line);
		}
		return result.toString();
	}

	/** Pure, network-free: parses one chat-completions response body into a configuration string, or null if unparseable. Package-visible for testing. */
	static String parseResponse(String responseBody) {
		try {
			JsonNode root = MAPPER.readTree(responseBody);
			JsonNode choices = root.path("choices");
			if (!choices.isArray() || choices.isEmpty()) {
				return null;
			}
			String content = choices.get(0).path("message").path("content").asText("");
			return extractConfiguration(content);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "LLM response was not valid JSON", e);
			return null;
		}
	}

	/**
	 * Local models don't always follow formatting instructions perfectly even with
	 * {@code response_format: json_object} requested (markdown fences, a leading/trailing
	 * sentence, ...), so this tries the whole reply as JSON first, then falls back to the
	 * outermost {@code {...}} substring found anywhere in it. Returns {@code null} if neither
	 * yields a usable "configuration" string.
	 */
	private static String extractConfiguration(String content) {
		String trimmed = content.trim();
		try {
			JsonNode whole = MAPPER.readTree(trimmed);
			if (whole.isObject() && whole.path("configuration").isTextual()) {
				return whole.path("configuration").asText();
			}
		} catch (IOException ignored) {
			// fall through to the more forgiving extraction below
		}

		int objStart = content.indexOf('{');
		int objEnd = content.lastIndexOf('}');
		if (objStart >= 0 && objEnd > objStart) {
			try {
				JsonNode obj = MAPPER.readTree(content.substring(objStart, objEnd + 1));
				if (obj.isObject() && obj.path("configuration").isTextual()) {
					return obj.path("configuration").asText();
				}
			} catch (IOException ignored) {
				// nothing usable anywhere in the reply
			}
		}

		return null;
	}

}
