package at.jku.isse.ecco.service.llm;

import at.jku.isse.ecco.service.git.GitHistoryReader;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
 * of it. Each commit is judged only against {@link KnownFeature}s that reflect commits ALREADY
 * IMPORTED (human-reviewed, actually committed) before this one - never against this same commit's
 * own suggestion, and never against a later commit's. Growing the known-feature list with results
 * merely SUGGESTED (not yet reviewed/imported) earlier in the same run was tried and reverted: a
 * local model would over-eagerly force-fit a later, unrelated commit into whatever name had just
 * been suggested, purely because it was the only one available yet, which is worse than the naming
 * drift it was meant to avoid. It's the caller's job (see {@code ImportGitView}) to only ever
 * (re)compute the known-feature list from the repository's real, already-committed state between
 * one commit's review and the next commit's suggestion request.
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
			the feature names already known in the target repository, each shown together with the \
			more general feature it was inferred to be built on top of (its parent, when one is \
			known - may be empty for a first import). Base your answer only on this given data - \
			you have no ability to run commands or fetch anything else, so never assume prior \
			knowledge of this specific project and never claim to have looked at files not shown \
			here; everything you need is already provided below.

			A "feature" is a user- or system-facing capability, not a single line, variable, or \
			function in isolation. Read through the whole diff - every changed file, function, and \
			hunk - before deciding:
			- Aim for the MOST SPECIFIC capability the diff actually supports, not the broadest area \
			it happens to live in - prefer "password-reset" over "auth", "csv-export" over "export", \
			"dark-mode" over "settings". A broad, umbrella-sized name is usually a sign you stopped \
			reading the diff too early, not a correct classification. Only use a broader name when \
			the diff is genuinely about that broad thing itself (e.g. a change to the login flow's \
			shared entry point, not to one specific thing login does).
			- Genuinely NEW capability gets a new feature name - including one that specializes or \
			extends an EXISTING feature into something distinct. Use the given parent information: if \
			this commit's diff clearly builds a new, narrower capability on top of a known feature \
			(rather than modifying that feature's own existing behavior), name it as a new feature - \
			never fold it back into the broader parent's name just because it's related or lives \
			nearby in the code. The parent's own name is reserved for that base capability itself.
			- A change that MODIFIES, FIXES a bug in, REFACTORS (behavior-preserving), or improves \
			the PERFORMANCE of something that already exists belongs to that EXISTING feature's \
			name (parent or child, whichever one the diff is actually changing), even if the diff \
			looks quite different from that feature's original introduction - do not invent a new \
			name just because the code shape changed, and do not reuse a name for a genuinely \
			different, newly-appearing capability just because it's convenient.
			- If the diff shows two or more genuinely SEPARATE capabilities being added or changed - \
			not just one feature's own internals touching a shared file - list all of them, \
			comma-separated, rather than picking only the most prominent one.
			- If the diff shows the change touching shared state, a shared file, or a common data \
			structure that another known feature also depends on, consider that feature affected too \
			and list it as well (comma-separated) - but only based on what the diff actually shows \
			here, never speculation about code not shown to you.
			- If the diff clearly shows a KNOWN feature's own implementation being deleted, removed, \
			or fully replaced by something unrelated - the feature genuinely stops existing as of \
			this commit, not merely refactored, renamed, or modified in place - list that feature \
			prefixed with a minus sign instead of its bare name, e.g. "-login", so it drops out of \
			the tracked configuration going forward. Be CONSERVATIVE here: only ever do this when the \
			diff itself is unambiguous about removal (its files/functions are deleted outright, or a \
			commit message like "remove X"/"drop X" is directly confirmed by the diff). A feature \
			simply not being touched by this commit is the ordinary case for almost every commit and \
			must NEVER be listed at all, with or without a minus sign - only mention a feature when \
			this commit is actually adding to it or removing it, nothing else needs to be repeated.

			Base your classification primarily on the actual code change in "change" - what files, \
			functions, and logic were actually added or modified - not just the wording of \
			"summary". A commit message can be vague, generic, or even misleading about what the \
			commit really does; the diff is the ground truth. Only fall back to the \
			message-derived guess if the diff itself is too sparse or unclear to tell (e.g. a pure \
			version-number or whitespace change).

			Pick a short, descriptive name of only ONE TO THREE words joined by underscore or \
			hyphen, using only letters, digits, underscore or hyphen - no spaces or other \
			punctuation - e.g. "login" or "password-reset", never something long or overspecific \
			like "add-password-hashing-to-login-form". Only reuse an existing name (parent or child) \
			if this commit's actual code change genuinely continues that exact same capability - \
			never reuse one just because it happens to be the only (or most recent, or nearest \
			parent) name already known; a distinct capability must get its own new, \
			accurately-descriptive name even when just one feature is known so far. This applies \
			even to a small or seemingly trivial commit (a version bump, a typo fix, a comment \
			tweak, ...) - never refuse to classify it.

			Features are cross-cutting: one feature's changes can be source code, tests, documentation \
			and build files alike. A change to documentation, tests or build files belongs to the feature \
			it documents, tests or builds - the kind of a file is never a feature by itself. Use \
			"documentation" or "misc" only for a change that serves no feature at all: "documentation" when \
			it only changes documentation (a README, docs files, code comments) about the product as a \
			whole, "misc" for anything else (build or CI setup, version bumps and releases, dependency \
			updates, license files).

			Respond with ONLY a JSON object shaped exactly like the example below - always exactly \
			the one key "configuration" (a single string, never an object or array) - and nothing \
			else: no prose, no markdown code fences, no explanation, no questions back to the user, \
			no analysis report, no table. "configuration" is a comma-separated list of feature names \
			to ADD, each optionally prefixed with "-" for a feature to REMOVE (see above) - leave it \
			an empty string "" if this commit neither adds nor removes anything a human should notice \
			(rare - almost every commit belongs to at least "documentation" or "misc" if nothing more \
			specific fits). Never reformat or repeat the commit's "id", "summary" or "change" back as \
			JSON fields of your own - your only job is to output a "configuration" guess, nothing \
			about the commit itself.

			Each known feature may be shown with the files and positions (classes, methods, sections, \
			...) its earlier commits changed, and the commit with evidence computed from the repository, \
			not guessed: each changed file's kind (source, test, documentation, build, ci, resource), how \
			much changed, and where in the file; which known features' earlier commits changed the same \
			places; and hints from the commit message. Weigh it with the diff and the message. The same \
			positions as a known feature's earlier changes are strong evidence for that feature; other \
			positions in the same enclosing one (another method of the same class, another subsection of \
			the same section) are medium; the same files only are weak. A commit changing places no known \
			feature changed, about something none of them covers, is most likely a NEW feature - give it \
			its own name instead of the nearest broad one.

			Example input:
			Known feature names, each with the more general feature it was built on top of, when known:
			(none yet)

			Commit:
			id: a1b2c3d
			summary: Update code
			change:
			+def hash_password(password): return bcrypt.hashpw(password.encode(), bcrypt.gensalt())
			+def login(username, password): ...

			Example output:
			{"configuration": "login"}

			Example input (later commit, "login" now known):
			Known feature names, each with the more general feature it was built on top of, when known:
			- login

			Commit:
			id: e4f5a6b
			summary: Add reset link
			change:
			+def send_password_reset_email(user): ...
			+def reset_password(token, new_password): ...

			Example output:
			{"configuration": "password-reset"}

			Example input (later commit, "login" and "password-reset" both known):
			Known feature names, each with the more general feature it was built on top of, when known:
			- login
			- password-reset (built on: login)

			Commit:
			id: f7a8b9c
			summary: Drop password-reset flow
			change:
			-def send_password_reset_email(user): ...
			-def reset_password(token, new_password): ...
			+# password reset removed - handled by SSO provider now

			Example output:
			{"configuration": "-password-reset"}""";

	private final String endpointUrl;
	private final String modelName;

	public LlmFeatureSuggestionClient(String endpointUrl, String modelName) {
		this.endpointUrl = endpointUrl;
		this.modelName = modelName;
	}

	/**
	 * A fresh {@link HttpClient} per call, closed by the caller (try-with-resources; {@code
	 * HttpClient} has implemented {@link AutoCloseable} since JDK 21) rather than one shared instance
	 * reused across an entire batch - observed directly: after one request hung for far longer than
	 * either timeout below should have allowed (a real network outage, not a bug in this class) every
	 * later request on the same shared client immediately failed with {@code BindException: Can't
	 * assign requested address}, i.e. local port/connection-pool exhaustion from the one stuck
	 * connection took the rest of the batch down with it. A fresh client per request can't carry that
	 * kind of damage forward - worth the (negligible, next to LLM response latency) cost of not
	 * reusing connections.
	 */
	private static HttpClient newHttpClient() {
		return HttpClient.newBuilder()
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
		HttpResponse<String> response;
		try (HttpClient httpClient = newHttpClient()) {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		}
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
	/**
	 * One commit, with the files it changed ({@code changes}: lines added and removed, positions -
	 * see {@link GitHistoryReader#getChangedPositions}) and how they overlap the known features'
	 * earlier changes ({@code overlap}, strongest first - see {@link ImportedFeatureTraces#overlap});
	 * without changes, the prompt has no evidence section.
	 */
	public record CommitForSuggestion(String commitId, String message, String diff, Map<String, GitHistoryReader.ChangedFile> changes,
									  List<ImportedFeatureTraces.Overlap> overlap) {

		public CommitForSuggestion(String commitId, String message, String diff) {
			this(commitId, message, diff, Map.of(), List.of());
		}
	}

	/**
	 * One feature already known in the target repository, together with the more general feature
	 * it was inferred to be built on top of ({@code parentName}, or {@code null} for a root/unknown
	 * parent) - lets the prompt judge how specific a NEW feature name should be (see SYSTEM_PROMPT's
	 * granularity guidance) instead of only ever seeing a flat list of names. This class has no
	 * opinion on how {@code parentName} is computed; the caller supplies it (e.g. from a feature
	 * hierarchy inferred from commit history).
	 */
	public record KnownFeature(String name, String parentName, Set<String> files, Set<String> positions) {

		public KnownFeature(String name, String parentName) {
			this(name, parentName, Set.of(), Set.of());
		}
	}

	/** At most this many of a known feature's files go into the prompt. */
	private static final int MAX_FILES_PER_FEATURE = 8;
	/** At most this many of a known feature's positions, or of a changed file's, go into the prompt. */
	private static final int MAX_POSITIONS = 4;
	/** At most this many changed files are listed. */
	private static final int MAX_CHANGED_FILES = 30;

	/**
	 * One batch's outcome: {@code configurations} always has one entry per input commit, in the
	 * same order (blank where no suggestion is available), so callers can always populate a full,
	 * if partially-empty, review table. {@code failureReason} is non-null if the call for at least
	 * one commit failed outright (network error, non-2xx response, unparseable reply) - as opposed
	 * to the model legitimately leaving a commit blank - so a caller can tell those two very
	 * different situations apart instead of guessing from a blank entry alone.
	 */
	public record SuggestionBatch(List<String> configurations, String failureReason, List<Usage> usages) {

		public SuggestionBatch(List<String> configurations, String failureReason) {
			this(configurations, failureReason, List.of());
		}
	}

	/**
	 * What one commit's request cost: the prompt and reply sizes the endpoint reported ({@code -1}
	 * where it reported none) and the time from sending the request to having the reply. Recorded
	 * with each suggestion (see {@link FeatureSuggestionLog}) to tell where the time goes.
	 */
	public record Usage(int promptTokens, int completionTokens, long millis) {
	}

	/**
	 * Reports progress through {@link #suggestConfigurations} - one call per commit, right before
	 * that commit's request is sent (not after), since a single request can take minutes (see
	 * {@link #suggestOneConfiguration}'s timeout) and a caller driving a progress bar/label wants to
	 * show which commit is currently in flight, not just a count of ones already finished.
	 */
	@FunctionalInterface
	public interface ProgressListener {
		void onCommitStarting(int commitIndex, int totalCommits, String commitId);
	}

	/**
	 * Suggests one configuration string per commit, in the same order as {@code commitsOldestFirst},
	 * via one LLM call per commit. Deliberately does NOT grow {@code knownFeatures} with results
	 * discovered earlier in the same call: a local model was observed over-eagerly force-fitting a
	 * later, unrelated commit into whatever name had just been suggested, purely because it was the
	 * only (or most recent) one available - worse than the naming-drift it would otherwise avoid,
	 * since every suggestion here is human-reviewed anyway. Every commit is judged only against the
	 * {@link KnownFeature}s the caller passed in for THIS call. Never throws.
	 *
	 * @param onProgress may be {@code null} if the caller doesn't care about progress.
	 */
	public SuggestionBatch suggestConfigurations(List<CommitForSuggestion> commitsOldestFirst, Collection<KnownFeature> knownFeatures, ProgressListener onProgress) {
		List<String> configurations = new ArrayList<>(commitsOldestFirst.size());
		List<Usage> usages = new ArrayList<>(commitsOldestFirst.size());
		List<String> failedCommitIds = new ArrayList<>();
		String lastFailureReason = null;

		int total = commitsOldestFirst.size();
		int index = 0;
		for (CommitForSuggestion commit : commitsOldestFirst) {
			if (onProgress != null) {
				onProgress.onCommitStarting(index, total, commit.commitId());
			}
			OneResult result = suggestOneConfiguration(commit, knownFeatures);
			configurations.add(result.configuration() == null ? "" : result.configuration());
			usages.add(result.usage());
			if (result.failureReason() != null) {
				failedCommitIds.add(commit.commitId());
				lastFailureReason = result.failureReason();
			}
			index++;
		}

		String overallFailureReason = failedCommitIds.isEmpty() ? null :
				failedCommitIds.size() + " of " + commitsOldestFirst.size() + " commit(s) could not be classified (" +
						String.join(", ", failedCommitIds) + "). Last error: " + lastFailureReason;
		return new SuggestionBatch(configurations, overallFailureReason, usages);
	}

	private record OneResult(String configuration, String failureReason, Usage usage) {
	}

	private OneResult suggestOneConfiguration(CommitForSuggestion commit, Collection<KnownFeature> knownFeatures) {
		URI chatCompletionsUri = resolveChatCompletionsUri(this.endpointUrl);
		long start = System.currentTimeMillis();
		try {
			String requestBody = buildRequestBody(commit, knownFeatures, this.modelName);
			// generous on purpose: a local model that's been idle can take a while just to load into
			// memory before it answers at all, and that cold-start cost lands on whichever commit
			// happens to be first in the range - a real timeout observed with the previous, tighter
			// 2-minute budget
			ChatEndpoint.Reply reply = new ChatEndpoint(this.endpointUrl).send((ObjectNode) MAPPER.readTree(requestBody), Duration.ofMinutes(5));
			long millis = System.currentTimeMillis() - start;
			if (reply.status() / 100 != 2) {
				String reason = "LLM endpoint " + chatCompletionsUri + " returned HTTP " + reply.status() + ": " + reply.body();
				LOGGER.log(Level.WARNING, reason);
				return new OneResult(null, reason, new Usage(-1, -1, millis));
			}
			Usage usage = parseUsage(reply.body(), millis);
			String configuration = parseResponse(reply.body());
			if (configuration == null) {
				String reason = "The LLM's response for commit " + commit.commitId() + " could not be parsed as the " +
						"expected JSON shape. Raw reply: " + reply.body();
				return new OneResult(null, reason, usage);
			}
			return new OneResult(configuration, null, usage);
		} catch (IOException | InterruptedException | RuntimeException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			String reason = "Error calling LLM endpoint " + chatCompletionsUri + " for commit " + commit.commitId() + ": " + e;
			LOGGER.log(Level.WARNING, reason, e);
			return new OneResult(null, reason, new Usage(-1, -1, System.currentTimeMillis() - start));
		}
	}

	/** The OpenAI-style {@code usage} of a chat-completions response body, {@code -1} for what it lacks. Package-visible for testing. */
	static Usage parseUsage(String responseBody, long millis) {
		try {
			JsonNode usage = MAPPER.readTree(responseBody).path("usage");
			return new Usage(usage.path("prompt_tokens").asInt(-1), usage.path("completion_tokens").asInt(-1), millis);
		} catch (IOException e) {
			return new Usage(-1, -1, millis);
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
	static String buildRequestBody(CommitForSuggestion commit, Collection<KnownFeature> knownFeatures, String modelName) {
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
		userMessage.put("content", buildUserContent(commit, knownFeatures));

		return root.toString();
	}

	private static String buildUserContent(CommitForSuggestion commit, Collection<KnownFeature> knownFeatures) {
		StringBuilder sb = new StringBuilder();
		sb.append("Known feature names, each with the more general feature it was built on top of, when known:\n");
		if (knownFeatures.isEmpty()) {
			sb.append("(none yet)");
		} else {
			sb.append(knownFeatures.stream()
					.map(feature -> (feature.parentName() == null || feature.parentName().isBlank()
							? "- " + feature.name()
							: "- " + feature.name() + " (built on: " + feature.parentName() + ")") + filesLine(feature))
					.collect(Collectors.joining("\n")));
		}
		if (!commit.changes().isEmpty())
			sb.append("\n\n").append(evidence(commit));
		// deliberately NOT "commitId:"/"message:" as labels - those match output JSON keys closely
		// enough that a local model has been observed just reformatting this input record verbatim
		// into JSON instead of classifying it (see SYSTEM_PROMPT)
		sb.append("\n\nCommit:\nid: ").append(commit.commitId())
				.append("\nsummary: ").append(commit.message())
				.append("\nchange:\n").append(stripDiffNoise(commit.diff()));
		return sb.toString();
	}

	/** The evidence section: the changed files, their overlap with the known features' earlier changes, the message's hints. */
	static String evidence(CommitForSuggestion commit) {
		StringBuilder sb = new StringBuilder("Changed files (kind, lines added/removed, where in the file):");
		Map<String, Integer> kinds = new LinkedHashMap<>();
		int listed = 0;
		for (GitHistoryReader.ChangedFile file : commit.changes().values()) {
			String kind = ChangeEvidence.kind(file.path());
			kinds.merge(kind, 1, Integer::sum);
			if (listed++ >= MAX_CHANGED_FILES)
				continue;
			sb.append("\n- ").append(file.path()).append(" (").append(kind).append(", +").append(file.added()).append(" -").append(file.removed()).append(")");
			if (!file.positions().isEmpty())
				sb.append(" in: ").append(limited(file.positions(), MAX_POSITIONS));
		}
		if (commit.changes().size() > MAX_CHANGED_FILES)
			sb.append("\n- ... ").append(commit.changes().size() - MAX_CHANGED_FILES).append(" more files");
		sb.append("\nKinds: ").append(kinds.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.joining(", ")));
		sb.append("\n\nKnown features whose earlier commits changed the same places:");
		if (commit.overlap().isEmpty())
			sb.append("\n(none - this commit changes no place a known feature changed before)");
		for (ImportedFeatureTraces.Overlap overlap : commit.overlap()) {
			List<String> parts = new ArrayList<>();
			if (!overlap.samePositions().isEmpty())
				parts.add("same positions " + limited(overlap.samePositions(), MAX_POSITIONS));
			if (!overlap.sameEnclosing().isEmpty())
				parts.add("in the same " + limited(overlap.sameEnclosing(), MAX_POSITIONS));
			parts.add(overlap.sameFiles() + " of " + commit.changes().size() + " changed files");
			sb.append("\n- ").append(overlap.feature()).append(": ").append(String.join("; ", parts));
		}
		List<String> hints = ChangeEvidence.messageHints(commit.message());
		if (!hints.isEmpty())
			sb.append("\n\nCommit message hints: ").append(String.join(", ", hints));
		return sb.toString();
	}

	private static String limited(java.util.Collection<String> items, int max) {
		List<String> list = new ArrayList<>(items);
		return String.join(", ", list.subList(0, Math.min(max, list.size()))) + (list.size() > max ? " (+" + (list.size() - max) + " more)" : "");
	}

	private static String filesLine(KnownFeature feature) {
		if (feature.files().isEmpty())
			return "";
		List<String> files = new ArrayList<>(feature.files());
		String shown = String.join(", ", files.subList(0, Math.min(MAX_FILES_PER_FEATURE, files.size())));
		String line = "\n  files: " + shown + (files.size() > MAX_FILES_PER_FEATURE ? " (+" + (files.size() - MAX_FILES_PER_FEATURE) + " more)" : "");
		if (!feature.positions().isEmpty())
			line += "\n  positions: " + limited(feature.positions(), MAX_POSITIONS);
		return line;
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
