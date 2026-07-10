package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LlmFeatureSuggestionClient#buildRequestBody} and
 * {@link LlmFeatureSuggestionClient#parseResponse} are pure, network-free functions specifically
 * so they can be exercised like this - no real LLM endpoint (local or otherwise) needs to be
 * running for these tests to mean something.
 */
public class LlmFeatureSuggestionClientTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final List<LlmFeatureSuggestionClient.CommitForSuggestion> TWO_COMMITS = List.of(
			new LlmFeatureSuggestionClient.CommitForSuggestion("aaa1111", "add setup", "diff --git a/setup.txt ..."),
			new LlmFeatureSuggestionClient.CommitForSuggestion("bbb2222", "add notes", "diff --git a/notes.txt ..."));

	@Test
	public void buildRequestBody_includesModelCommitsAndKnownFeatures() throws Exception {
		String body = LlmFeatureSuggestionClient.buildRequestBody(TWO_COMMITS, List.of("setup"), "llama3.1");

		JsonNode root = MAPPER.readTree(body);
		assertEquals("llama3.1", root.path("model").asText());
		assertEquals(false, root.path("stream").asBoolean());

		JsonNode messages = root.path("messages");
		assertEquals(2, messages.size());
		assertEquals("system", messages.get(0).path("role").asText());
		assertEquals("user", messages.get(1).path("role").asText());

		String userContent = messages.get(1).path("content").asText();
		assertTrue(userContent.contains("setup"), "known feature name should appear in the prompt");
		assertTrue(userContent.contains("aaa1111"), "first commit id should appear in the prompt");
		assertTrue(userContent.contains("bbb2222"), "second commit id should appear in the prompt");
		assertTrue(userContent.contains("add setup"), "first commit message should appear in the prompt");
	}

	@Test
	public void buildRequestBody_withNoKnownFeatures_saysSo() {
		String body = LlmFeatureSuggestionClient.buildRequestBody(TWO_COMMITS, List.of(), "llama3.1");
		assertTrue(body.contains("none yet"));
	}

	@Test
	public void parseResponse_cleanJsonArray_matchesEachCommitInOrder() {
		String response = chatCompletionsResponse("""
				[
				  {"commitId": "aaa1111", "configuration": "setup"},
				  {"commitId": "bbb2222", "configuration": "setup, notes"}
				]""");

		List<String> suggestions = LlmFeatureSuggestionClient.parseResponse(response, TWO_COMMITS);

		assertEquals(List.of("setup", "setup, notes"), suggestions);
	}

	@Test
	public void parseResponse_wrappedInMarkdownFencesAndProse_stillExtractsTheArray() {
		String response = chatCompletionsResponse("""
				Sure, here's the classification:
				```json
				[
				  {"commitId": "aaa1111", "configuration": "setup"},
				  {"commitId": "bbb2222", "configuration": "setup, notes"}
				]
				```
				Let me know if you'd like anything else!""");

		List<String> suggestions = LlmFeatureSuggestionClient.parseResponse(response, TWO_COMMITS);

		assertEquals(List.of("setup", "setup, notes"), suggestions);
	}

	@Test
	public void parseResponse_reorderedEntries_stillMatchByCommitIdNotPosition() {
		String response = chatCompletionsResponse("""
				[
				  {"commitId": "bbb2222", "configuration": "setup, notes"},
				  {"commitId": "aaa1111", "configuration": "setup"}
				]""");

		List<String> suggestions = LlmFeatureSuggestionClient.parseResponse(response, TWO_COMMITS);

		// TWO_COMMITS order is [aaa1111, bbb2222] - output must follow that, not the model's order
		assertEquals(List.of("setup", "setup, notes"), suggestions);
	}

	@Test
	public void parseResponse_missingOneCommitsEntry_leavesJustThatOneBlank() {
		String response = chatCompletionsResponse("""
				[
				  {"commitId": "aaa1111", "configuration": "setup"}
				]""");

		List<String> suggestions = LlmFeatureSuggestionClient.parseResponse(response, TWO_COMMITS);

		assertEquals(List.of("setup", ""), suggestions);
	}

	@Test
	public void parseResponse_notJsonAtAll_yieldsAllBlankNotAnException() {
		String response = chatCompletionsResponse("I'm not sure how to help with that.");

		List<String> suggestions = LlmFeatureSuggestionClient.parseResponse(response, TWO_COMMITS);

		assertEquals(List.of("", ""), suggestions);
	}

	@Test
	public void parseResponse_malformedOuterJson_yieldsAllBlankNotAnException() {
		List<String> suggestions = LlmFeatureSuggestionClient.parseResponse("{ not even valid json", TWO_COMMITS);

		assertEquals(List.of("", ""), suggestions);
	}

	@Test
	public void parseResponse_emptyChoicesArray_yieldsAllBlank() {
		String response = "{\"choices\": []}";

		List<String> suggestions = LlmFeatureSuggestionClient.parseResponse(response, TWO_COMMITS);

		assertEquals(List.of("", ""), suggestions);
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