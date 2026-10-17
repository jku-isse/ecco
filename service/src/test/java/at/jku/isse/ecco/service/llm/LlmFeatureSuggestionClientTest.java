package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LlmFeatureSuggestionClient#buildRequestBody}, {@link LlmFeatureSuggestionClient#parseResponse},
 * {@link LlmFeatureSuggestionClient#stripDiffNoise}, {@link LlmFeatureSuggestionClient#resolveModelsUri}
 * and {@link LlmFeatureSuggestionClient#parseModelsResponse} are pure, network-free functions
 * specifically so they can be exercised like this - no real LLM endpoint (local or otherwise)
 * needs to be running for these tests to mean something.
 */
public class LlmFeatureSuggestionClientTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final LlmFeatureSuggestionClient.CommitForSuggestion COMMIT =
			new LlmFeatureSuggestionClient.CommitForSuggestion("aaa1111", "add setup", "diff --git a/setup.txt ...");

	@Test
	public void buildRequestBody_includesModelCommitAndKnownFeatures() throws Exception {
		String body = LlmFeatureSuggestionClient.buildRequestBody(COMMIT, List.of(new LlmFeatureSuggestionClient.KnownFeature("setup", null)), "llama3.1");

		JsonNode root = MAPPER.readTree(body);
		assertEquals("llama3.1", root.path("model").asText());
		assertEquals(false, root.path("stream").asBoolean());
		assertEquals(0, root.path("temperature").asInt());
		assertEquals("json_object", root.path("response_format").path("type").asText());

		JsonNode messages = root.path("messages");
		assertEquals(2, messages.size());
		assertEquals("system", messages.get(0).path("role").asText());
		assertEquals("user", messages.get(1).path("role").asText());

		String userContent = messages.get(1).path("content").asText();
		assertTrue(userContent.contains("setup"), "known feature name should appear in the prompt");
		assertTrue(userContent.contains("aaa1111"), "commit id should appear in the prompt");
		assertTrue(userContent.contains("add setup"), "commit message should appear in the prompt");
	}

	@Test
	public void buildRequestBody_includesParentFeatureWhenKnown() throws Exception {
		String body = LlmFeatureSuggestionClient.buildRequestBody(COMMIT,
				List.of(new LlmFeatureSuggestionClient.KnownFeature("password-reset", "login")), "llama3.1");

		JsonNode root = MAPPER.readTree(body);
		String userContent = root.path("messages").get(1).path("content").asText();
		assertTrue(userContent.contains("password-reset"), "known feature name should appear in the prompt");
		assertTrue(userContent.contains("built on: login"), "parent feature should appear in the prompt");
	}

	@Test
	public void buildRequestBody_showsTheKnownFeaturesTracesAndTheCommitsEvidence() throws Exception {
		java.util.Map<String, at.jku.isse.ecco.service.git.GitHistoryReader.ChangedFile> changes = new java.util.LinkedHashMap<>();
		changes.put("src/Login.java", new at.jku.isse.ecco.service.git.GitHistoryReader.ChangedFile("src/Login.java", 3, 1, java.util.Set.of("Login.reset")));
		changes.put("README.md", new at.jku.isse.ecco.service.git.GitHistoryReader.ChangedFile("README.md", 2, 0, java.util.Set.of()));
		LlmFeatureSuggestionClient.CommitForSuggestion commit = new LlmFeatureSuggestionClient.CommitForSuggestion(
				"aaa1111", "fix reset (#12)", "diff --git a/src/Login.java b/src/Login.java", changes,
				List.of(new ImportedFeatureTraces.Overlap("login", List.of("Login.reset"), List.of("Login"), 1)));
		java.util.Set<String> many = new java.util.TreeSet<>();
		for (int i = 0; i < 10; i++)
			many.add("ui/f" + i + ".c");
		String body = LlmFeatureSuggestionClient.buildRequestBody(commit, List.of(
				new LlmFeatureSuggestionClient.KnownFeature("login", null, new java.util.TreeSet<>(java.util.Set.of("src/Login.java", "src/auth.h")),
						new java.util.LinkedHashSet<>(List.of("Login.reset", "Login.check"))),
				new LlmFeatureSuggestionClient.KnownFeature("ui", null, many, java.util.Set.of())), "m");

		String user = MAPPER.readTree(body).path("messages").get(1).path("content").asText();
		assertTrue(user.contains("- login\n  files: src/Login.java, src/auth.h\n  positions: Login.reset, Login.check\n"), user);
		assertTrue(user.contains("files: ui/f0.c, ui/f1.c, ui/f2.c, ui/f3.c, ui/f4.c, ui/f5.c, ui/f6.c, ui/f7.c (+2 more)\n"), user);
		assertTrue(user.contains("""
				Changed files (kind, lines added/removed, where in the file):
				- src/Login.java (source, +3 -1) in: Login.reset
				- README.md (documentation, +2 -0)
				Kinds: 1 source, 1 documentation

				Known features whose earlier commits changed the same places:
				- login: same positions Login.reset; in the same Login; 1 of 2 changed files

				Commit message hints: issue reference

				Commit:"""), user);
	}

	@Test
	public void buildRequestBody_withAnOverlapWithNoFeature_saysSo() throws Exception {
		LlmFeatureSuggestionClient.CommitForSuggestion commit = new LlmFeatureSuggestionClient.CommitForSuggestion("aaa1111", "add", "d",
				java.util.Map.of("new.c", new at.jku.isse.ecco.service.git.GitHistoryReader.ChangedFile("new.c", 5, 0, java.util.Set.of("add"))), List.of());

		String user = MAPPER.readTree(LlmFeatureSuggestionClient.buildRequestBody(commit, List.of(), "m")).path("messages").get(1).path("content").asText();
		assertTrue(user.contains("Known features whose earlier commits changed the same places:\n(none - this commit changes no place a known feature changed before)\n\nCommit:"), user);
	}

	@Test
	public void systemPromptSaysFeaturesAreCrossCutting() throws Exception {
		String system = MAPPER.readTree(LlmFeatureSuggestionClient.buildRequestBody(COMMIT, List.of(), "m")).path("messages").get(0).path("content").asText();
		assertTrue(system.contains("Features are cross-cutting"), system);
		assertTrue(system.contains("same positions as a known feature's earlier changes are strong evidence"), system);
	}

	@Test
	public void buildRequestBody_withoutChanges_hasNoEvidenceSection() throws Exception {
		String body = LlmFeatureSuggestionClient.buildRequestBody(COMMIT, List.of(new LlmFeatureSuggestionClient.KnownFeature("setup", null)), "m");

		String user = MAPPER.readTree(body).path("messages").get(1).path("content").asText();
		assertTrue(user.startsWith("Known feature names, each with the more general feature it was built on top of, when known:\n- setup\n\nCommit:"), user);
	}

	@Test
	public void buildRequestBody_withNoKnownFeatures_saysSo() {
		String body = LlmFeatureSuggestionClient.buildRequestBody(COMMIT, List.of(), "llama3.1");
		assertTrue(body.contains("none yet"));
	}

	@Test
	public void buildRequestBody_stripsIndexLineFromDiff() {
		LlmFeatureSuggestionClient.CommitForSuggestion commit = new LlmFeatureSuggestionClient.CommitForSuggestion(
				"aaa1111", "fix", "diff --git a/f.c b/f.c\nindex 94f5d6e..a6bbd55 100644\n--- a/f.c\n+++ b/f.c\n+int x;");
		String body = LlmFeatureSuggestionClient.buildRequestBody(commit, List.of(), "llama3.1");

		assertTrue(body.contains("+int x;"), "actual diff content should still be present");
		assertTrue(!body.contains("94f5d6e"), "the noisy index-line hashes should have been stripped");
	}

	@Test
	public void stripDiffNoise_removesOnlyTheIndexLine() {
		String diff = "diff --git a/f.c b/f.c\nindex 94f5d6e..a6bbd55 100644\n--- a/f.c\n+++ b/f.c\n+int x;";
		String stripped = LlmFeatureSuggestionClient.stripDiffNoise(diff);

		assertEquals("diff --git a/f.c b/f.c\n--- a/f.c\n+++ b/f.c\n+int x;", stripped);
	}

	@Test
	public void parseResponse_cleanJsonObject_returnsConfiguration() {
		String response = chatCompletionsResponse("{\"configuration\": \"setup\"}");

		assertEquals("setup", LlmFeatureSuggestionClient.parseResponse(response));
	}

	/** The reply names a parent for each new feature: read leniently, as the configuration is. */
	@Test
	public void parseParents_readsTheParentsObject() {
		String response = chatCompletionsResponse("Sure! {\"configuration\": \"aes, zip64\", \"parents\": {\"aes\": \"encryption\", \"zip64\": \"BASE\", \"x\": 3}}");

		assertEquals("aes, zip64", LlmFeatureSuggestionClient.parseResponse(response));
		assertEquals(java.util.Map.of("aes", "encryption", "zip64", "BASE"), LlmFeatureSuggestionClient.parseParents(response), "a parent that is no name is left out");
		assertEquals(java.util.Map.of(), LlmFeatureSuggestionClient.parseParents(chatCompletionsResponse("{\"configuration\": \"aes\"}")));
		assertEquals(java.util.Map.of(), LlmFeatureSuggestionClient.parseParents(chatCompletionsResponse("{\"configuration\": \"aes\", \"parents\": \"encryption\"}")));
	}

	/** Only a new feature's parent counts, and only one that is known, BASE, or new in the same configuration. */
	@Test
	public void parentsOf_keepsTheParentsOfNewFeaturesThatMakeSense() {
		List<LlmFeatureSuggestionClient.KnownFeature> known = List.of(
				new LlmFeatureSuggestionClient.KnownFeature("BASE", null), new LlmFeatureSuggestionClient.KnownFeature("encryption", "BASE"));
		java.util.Map<String, String> named = java.util.Map.of(
				"aes", "encryption", "aes-gcm", "aes", "zip64", "BASE", "split", "nowhere", "self", "self", "encryption", "BASE");

		assertEquals(java.util.Map.of("aes", "encryption", "aes-gcm", "aes", "zip64", "BASE"),
				LlmFeatureSuggestionClient.parentsOf("aes, aes-gcm, zip64, split, self, encryption, -old", named, known));
	}

	/** The prompt tells what BASE is and asks for the parents of new features. */
	@Test
	public void buildRequestBody_asksForTheParentsOfNewFeatures() throws Exception {
		JsonNode body = MAPPER.readTree(LlmFeatureSuggestionClient.buildRequestBody(
				new LlmFeatureSuggestionClient.CommitForSuggestion("abc", "Add AES", "+aes"), List.of(new LlmFeatureSuggestionClient.KnownFeature("BASE", null)), "m"));
		String system = body.path("messages").get(0).path("content").asText();
		assertTrue(system.contains("\"parents\""), system);
		assertTrue(system.contains("BASE, when known, is the code base as it was first imported"), system);
		assertTrue(system.contains("{\"configuration\": \"password-reset\", \"parents\": {\"password-reset\": \"login\"}}"), system);
	}

	@Test
	public void parseUsage_readsTheReportedTokenCounts() {
		String response = "{\"choices\":[],\"usage\":{\"prompt_tokens\":7670,\"completion_tokens\":12,\"total_tokens\":7682}}";

		assertEquals(new LlmFeatureSuggestionClient.Usage(7670, 12, 25), LlmFeatureSuggestionClient.parseUsage(response, 25));
	}

	@Test
	public void parseUsage_withoutUsageOrJson_isMinusOne() {
		assertEquals(new LlmFeatureSuggestionClient.Usage(-1, -1, 3), LlmFeatureSuggestionClient.parseUsage("{\"choices\":[]}", 3));
		assertEquals(new LlmFeatureSuggestionClient.Usage(-1, -1, 3), LlmFeatureSuggestionClient.parseUsage("not json", 3));
	}

	@Test
	public void parseResponse_commaSeparatedConfiguration_returnedAsIs() {
		String response = chatCompletionsResponse("{\"configuration\": \"setup, notes\"}");

		assertEquals("setup, notes", LlmFeatureSuggestionClient.parseResponse(response));
	}

	@Test
	public void parseResponse_configurationIsObjectNotString_yieldsNull() {
		// observed from a real local model even under response_format: json_object
		String response = chatCompletionsResponse("{\"configuration\": {\"featureName\": \"setup\"}}");

		assertNull(LlmFeatureSuggestionClient.parseResponse(response));
	}

	@Test
	public void parseResponse_wrappedInMarkdownFencesAndProse_stillExtractsTheObject() {
		String response = chatCompletionsResponse("""
				Sure, here's the classification:
				```json
				{"configuration": "setup"}
				```
				Let me know if you'd like anything else!""");

		assertEquals("setup", LlmFeatureSuggestionClient.parseResponse(response));
	}

	@Test
	public void parseResponse_echoesInputRecordInstead_yieldsNull() {
		// the exact degenerate reply observed from a real local model: it reformatted the input
		// commit's own fields back as JSON instead of producing a "configuration"
		String response = chatCompletionsResponse("{\"commitId\": \"aaa1111\", \"message\": \"fix\", \"diff\": \"...\"}");

		assertNull(LlmFeatureSuggestionClient.parseResponse(response));
	}

	@Test
	public void parseResponse_notJsonAtAll_yieldsNullNotAnException() {
		String response = chatCompletionsResponse("I'm not sure how to help with that.");

		assertNull(LlmFeatureSuggestionClient.parseResponse(response));
	}

	@Test
	public void parseResponse_malformedOuterJson_yieldsNullNotAnException() {
		assertNull(LlmFeatureSuggestionClient.parseResponse("{ not even valid json"));
	}

	@Test
	public void parseResponse_emptyChoicesArray_yieldsNull() {
		assertNull(LlmFeatureSuggestionClient.parseResponse("{\"choices\": []}"));
	}

	@Test
	public void resolveModelsUri_appendsV1ModelsToSchemeAndAuthority() {
		assertEquals("http://localhost:11434/v1/models",
				LlmFeatureSuggestionClient.resolveModelsUri("http://localhost:11434/v1/chat/completions").toString());
	}

	@Test
	public void resolveModelsUri_ignoresEndpointPath() {
		assertEquals("https://api.example.com:8443/v1/models",
				LlmFeatureSuggestionClient.resolveModelsUri("https://api.example.com:8443/some/custom/path").toString());
	}

	@Test
	public void resolveChatCompletionsUri_appendsV1ChatCompletionsToSchemeAndAuthority() {
		assertEquals("http://localhost:11434/v1/chat/completions",
				LlmFeatureSuggestionClient.resolveChatCompletionsUri("http://localhost:11434").toString());
	}

	@Test
	public void resolveChatCompletionsUri_ignoresEndpointPath() {
		// the exact real-world regression this guards against: the endpoint field held just the
		// server's bare base URL (as saved via the "Refresh" models picker), and suggestions broke
		// with HTTP 405 because the POST went straight to that root path instead of /v1/chat/completions
		assertEquals("http://10.78.115.5:11434/v1/chat/completions",
				LlmFeatureSuggestionClient.resolveChatCompletionsUri("http://10.78.115.5:11434/").toString());
	}

	@Test
	public void parseModelsResponse_returnsIdsSortedCaseInsensitively() throws Exception {
		String response = "{\"object\": \"list\", \"data\": [{\"id\": \"llama3.1\"}, {\"id\": \"Gemma2\"}, {\"id\": \"mistral\"}]}";

		assertEquals(List.of("Gemma2", "llama3.1", "mistral"), LlmFeatureSuggestionClient.parseModelsResponse(response));
	}

	@Test
	public void parseModelsResponse_ignoresEntriesWithoutId() throws Exception {
		String response = "{\"data\": [{\"id\": \"llama3.1\"}, {\"object\": \"model\"}]}";

		assertEquals(List.of("llama3.1"), LlmFeatureSuggestionClient.parseModelsResponse(response));
	}

	@Test
	public void parseModelsResponse_noDataArray_returnsEmptyList() throws Exception {
		assertEquals(List.of(), LlmFeatureSuggestionClient.parseModelsResponse("{}"));
	}

	/** Wraps {@code content} as the "message.content" of a single choice, matching a real OpenAI-compatible chat-completions response shape. */
	private static String chatCompletionsResponse(String content) {
		try {
			var root = MAPPER.createObjectNode();
			var choices = root.putArray("choices");
			var choice = choices.addObject();
			var message = choice.putObject("message");
			message.put("role", "assistant");
			message.put("content", content);
			return MAPPER.writeValueAsString(root);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
