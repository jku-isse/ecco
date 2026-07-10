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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asks a local, OpenAI-compatible chat-completions endpoint (e.g. Ollama) to suggest a feature
 * configuration string for each of a batch of git commits, given their messages/diffs and the
 * features already known in the target ecco repository - the "Import from Git" feature's LLM
 * step. One batched request per import (not one per commit): cheaper, and the model sees the
 * whole ordered sequence at once so it can recognize "this looks like the same feature commit 3
 * introduced" rather than guessing blind each time.
 * <p>
 * Every suggestion is only ever a STARTING POINT a human reviews/edits before anything is
 * committed into ecco (see {@code ImportGitView}) - never applied automatically - so this class
 * is deliberately forgiving: a network failure, an unreachable endpoint, or a response that isn't
 * valid JSON all result in blank suggestions (the user still gets a fully editable table and can
 * fill configurations in by hand) rather than aborting the whole import.
 */
public final class LlmFeatureSuggestionClient {

	private static final Logger LOGGER = Logger.getLogger(LlmFeatureSuggestionClient.class.getName());

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String SYSTEM_PROMPT = """
			You classify software version-control commits by which product-line feature(s) they \
			belong to, for a tool that reconstructs a feature model from a sequence of variants.

			You will be given an ordered list of commits (oldest first), each with a short commit \
			id, its commit message, and a truncated diff, plus the list of feature names already \
			known in the target repository (may be empty for a first import).

			For each commit, decide whether it continues an EXISTING feature (reuse its exact name) \
			or introduces a NEW one (pick a short, descriptive name using only letters, digits, \
			underscore or hyphen - no spaces or other punctuation). A commit may belong to more \
			than one feature.

			Respond with ONLY a JSON array, one object per input commit, in the same order, each \
			shaped exactly like: {"commitId": "<the commit's short id, copied exactly>", \
			"configuration": "<comma-separated feature names, e.g. \\"setup, notes\\" - no revision \
			numbers>"}. No prose, no markdown code fences, no explanation - just the JSON array.""";

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
	 * One commit's message/diff, as fed into the batched suggestion prompt. {@code commitId}
	 * should be short (e.g. a 7-character short hash, not a full 40-character one) - it's what the
	 * model is asked to copy back verbatim to identify which suggestion belongs to which commit,
	 * and a long random hex string is needlessly easy for a local model to transcribe wrong.
	 */
	public record CommitForSuggestion(String commitId, String message, String diff) {
	}

	/**
	 * Suggests one configuration string per commit, in the same order as {@code commitsOldestFirst}
	 * (by position, not by echoed id - callers never need to match ids back up themselves). Never
	 * throws: any failure (network, malformed JSON, a commit the model's response didn't cover)
	 * yields a blank string for the affected commit(s), so the caller can always populate a full,
	 * if partially-empty, review table.
	 */
	public List<String> suggestConfigurations(List<CommitForSuggestion> commitsOldestFirst, Collection<String> knownFeatureNames) {
		if (commitsOldestFirst.isEmpty()) {
			return List.of();
		}
		try {
			String requestBody = buildRequestBody(commitsOldestFirst, knownFeatureNames, this.modelName);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(this.endpointUrl))
					.header("Content-Type", "application/json")
					.timeout(Duration.ofMinutes(5))
					.POST(HttpRequest.BodyPublishers.ofString(requestBody))
					.build();
			HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				LOGGER.log(Level.WARNING, "LLM endpoint " + this.endpointUrl + " returned HTTP " + response.statusCode() + ": " + response.body());
				return blankSuggestions(commitsOldestFirst.size());
			}
			return parseResponse(response.body(), commitsOldestFirst);
		} catch (IOException | InterruptedException | RuntimeException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			LOGGER.log(Level.WARNING, "Error calling LLM endpoint " + this.endpointUrl + " for feature suggestions", e);
			return blankSuggestions(commitsOldestFirst.size());
		}
	}

	/** Pure, network-free: the exact JSON body {@link #suggestConfigurations} POSTs. Package-visible for testing. */
	static String buildRequestBody(List<CommitForSuggestion> commitsOldestFirst, Collection<String> knownFeatureNames, String modelName) {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("model", modelName);
		root.put("stream", false);

		ArrayNode messages = root.putArray("messages");
		ObjectNode systemMessage = messages.addObject();
		systemMessage.put("role", "system");
		systemMessage.put("content", SYSTEM_PROMPT);

		ObjectNode userMessage = messages.addObject();
		userMessage.put("role", "user");
		userMessage.put("content", buildUserContent(commitsOldestFirst, knownFeatureNames));

		return root.toString();
	}

	private static String buildUserContent(List<CommitForSuggestion> commitsOldestFirst, Collection<String> knownFeatureNames) {
		StringBuilder sb = new StringBuilder();
		sb.append("Known feature names: ");
		sb.append(knownFeatureNames.isEmpty() ? "(none yet)" : String.join(", ", knownFeatureNames));
		sb.append("\n\nCommits (oldest first):\n");
		for (CommitForSuggestion commit : commitsOldestFirst) {
			sb.append("\n---\ncommitId: ").append(commit.commitId())
					.append("\nmessage: ").append(commit.message())
					.append("\ndiff:\n").append(commit.diff())
					.append('\n');
		}
		return sb.toString();
	}

	/** Pure, network-free: parses one chat-completions response body into a per-commit suggestion list. Package-visible for testing. */
	static List<String> parseResponse(String responseBody, List<CommitForSuggestion> commitsOldestFirst) {
		try {
			JsonNode root = MAPPER.readTree(responseBody);
			JsonNode choices = root.path("choices");
			if (!choices.isArray() || choices.isEmpty()) {
				return blankSuggestions(commitsOldestFirst.size());
			}
			String content = choices.get(0).path("message").path("content").asText("");
			return parseSuggestionArray(content, commitsOldestFirst);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "LLM response was not valid JSON", e);
			return blankSuggestions(commitsOldestFirst.size());
		}
	}

	/**
	 * Local models don't always follow formatting instructions perfectly (markdown fences, a
	 * leading/trailing sentence, ...), so this extracts the outermost {@code [...]} block from the
	 * model's reply text rather than assuming the whole string is clean JSON, and matches
	 * suggestions back to commits by id (not by array position) so a model that reorders or drops
	 * an entry still yields correct-if-partial results instead of silently shifting every later
	 * commit's suggestion.
	 */
	private static List<String> parseSuggestionArray(String content, List<CommitForSuggestion> commitsOldestFirst) {
		int start = content.indexOf('[');
		int end = content.lastIndexOf(']');
		if (start < 0 || end < start) {
			return blankSuggestions(commitsOldestFirst.size());
		}

		try {
			JsonNode array = MAPPER.readTree(content.substring(start, end + 1));
			if (!array.isArray()) {
				return blankSuggestions(commitsOldestFirst.size());
			}

			Map<String, String> configByCommitId = new HashMap<>();
			for (JsonNode entry : array) {
				String commitId = entry.path("commitId").asText(null);
				String configuration = entry.path("configuration").asText(null);
				if (commitId != null && configuration != null) {
					configByCommitId.put(commitId, configuration);
				}
			}

			List<String> suggestions = new ArrayList<>(commitsOldestFirst.size());
			for (CommitForSuggestion commit : commitsOldestFirst) {
				suggestions.add(configByCommitId.getOrDefault(commit.commitId(), ""));
			}
			return suggestions;
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not parse the LLM's suggested feature array", e);
			return blankSuggestions(commitsOldestFirst.size());
		}
	}

	private static List<String> blankSuggestions(int count) {
		return new ArrayList<>(Collections.nCopies(count, ""));
	}

}
