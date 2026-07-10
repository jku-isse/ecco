package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LlmFeatureSuggestionClient#buildRequestBody}, {@link LlmFeatureSuggestionClient#parseResponse}
 * and {@link LlmFeatureSuggestionClient#stripDiffNoise} are pure, network-free functions
 * specifically so they can be exercised like this - no real LLM endpoint (local or otherwise)
 * needs to be running for these tests to mean something.
 */
public class LlmFeatureSuggestionClientTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final LlmFeatureSuggestionClient.CommitForSuggestion COMMIT =
			new LlmFeatureSuggestionClient.CommitForSuggestion("aaa1111", "add setup", "diff --git a/setup.txt ...");

	@Test
	public void buildRequestBody_includesModelCommitAndKnownFeatures() throws Exception {
		String body = LlmFeatureSuggestionClient.buildRequestBody(COMMIT, List.of("setup"), "llama3.1");

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
