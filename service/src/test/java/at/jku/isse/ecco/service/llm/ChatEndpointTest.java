package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A local Ollama loads a model with a 4,096-token context unless told otherwise, and through the
 * OpenAI-compatible endpoint it cannot be told: a longer prompt silently lost its beginning. Ollama
 * now gets its own /api/chat with the context the prompt needs - never less than the model is
 * loaded with or was asked for before (each change reloads the model), never more than its maximum.
 */
public class ChatEndpointTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private HttpServer server;
	private final List<JsonNode> chats = new ArrayList<>();
	private final List<String> paths = new ArrayList<>();
	private int loadedContext;

	@BeforeEach
	public void reset() {
		ChatEndpoint.reset();
	}

	@AfterEach
	public void stop() {
		if (this.server != null)
			this.server.stop(0);
		ChatEndpoint.reset();
	}

	private String startServer(boolean ollama) throws IOException {
		return this.startServer(ollama, 0);
	}

	private String startServer(boolean ollama, int port) throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		this.server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			this.paths.add(path);
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String reply;
			int status = 200;
			if (!ollama && path.startsWith("/api/")) {
				status = 404;
				reply = "404 page not found";
			} else if (path.equals("/api/version")) {
				reply = "{\"version\":\"0.35.0\"}";
			} else if (path.equals("/api/ps")) {
				reply = this.loadedContext == 0 ? "{\"models\":[]}"
						: "{\"models\":[{\"name\":\"m:14b\",\"model\":\"m:14b\",\"context_length\":" + this.loadedContext + "}]}";
			} else if (path.equals("/api/show")) {
				reply = "{\"model_info\":{\"qwen2.block_count\":48,\"qwen2.context_length\":32768}}";
			} else if (path.equals("/api/chat")) {
				JsonNode request = MAPPER.readTree(body);
				this.chats.add(request);
				this.loadedContext = request.path("options").path("num_ctx").asInt();
				reply = "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"configuration\\\": \\\"a\\\"}\"},\"prompt_eval_count\":9000,\"eval_count\":7}";
			} else {
				reply = "{\"choices\":[{\"message\":{\"content\":\"openai\"}}]}";
			}
			byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		this.server.start();
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	private static ObjectNode request(int promptCharacters) {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("model", "m:14b");
		body.put("stream", false);
		body.put("temperature", 0);
		body.putObject("response_format").put("type", "json_object");
		body.putArray("messages").addObject().put("role", "user").put("content", "x".repeat(promptCharacters));
		return body;
	}

	@Test
	@Timeout(30)
	public void ollamaGetsTheContextThePromptNeeds() throws Exception {
		String endpoint = this.startServer(true);
		this.loadedContext = 4096; // the default a local Ollama loads with

		ChatEndpoint.Reply reply = new ChatEndpoint(endpoint + "/v1").send(request(27_000), Duration.ofSeconds(10));

		JsonNode chat = this.chats.get(0);
		assertEquals(16384, chat.path("options").path("num_ctx").asInt(), "27,000 characters, about 9,000 tokens, and the answer");
		assertEquals("json", chat.path("format").asText());
		assertEquals(0, chat.path("options").path("temperature").asInt());
		assertEquals(200, reply.status());
		JsonNode converted = MAPPER.readTree(reply.body());
		assertEquals("{\"configuration\": \"a\"}", converted.path("choices").get(0).path("message").path("content").asText());
		assertEquals(9000, converted.path("usage").path("prompt_tokens").asInt());
		assertFalse(this.paths.contains("/v1/chat/completions"));

		// a shorter prompt keeps the context: a smaller one would reload the model
		new ChatEndpoint(endpoint).send(request(3_000), Duration.ofSeconds(10));
		assertEquals(16384, this.chats.get(1).path("options").path("num_ctx").asInt());
	}

	/** A shared server loaded with a larger context keeps it: a smaller one would reload the model for everyone. */
	@Test
	@Timeout(30)
	public void aLargerLoadedContextIsKept() throws Exception {
		String endpoint = this.startServer(true);
		this.loadedContext = 32768;

		new ChatEndpoint(endpoint).send(request(3_000), Duration.ofSeconds(10));

		assertEquals(32768, this.chats.get(0).path("options").path("num_ctx").asInt());
	}

	/**
	 * Ollama not up yet at the first request: once it is, it is asked as Ollama - the failed check
	 * was remembered for good, and every later prompt went to the OpenAI endpoint, cut to 4,096 tokens.
	 */
	@Test
	@Timeout(30)
	public void anOllamaNotUpYetIsTakenForOllamaOnceItIs() throws Exception {
		String endpoint = this.startServer(true);
		int port = this.server.getAddress().getPort();
		this.server.stop(0);

		assertThrows(IOException.class, () -> new ChatEndpoint(endpoint).send(request(3_000), Duration.ofSeconds(5)));

		this.startServer(true, port);
		new ChatEndpoint(endpoint).send(request(3_000), Duration.ofSeconds(10));
		assertTrue(this.paths.contains("/api/chat"), this.paths.toString());
		assertFalse(this.paths.contains("/v1/chat/completions"), this.paths.toString());
	}

	@Test
	@Timeout(30)
	public void otherServersGetTheOpenAiEndpoint() throws Exception {
		String endpoint = this.startServer(false);

		ChatEndpoint.Reply reply = new ChatEndpoint(endpoint).send(request(27_000), Duration.ofSeconds(10));

		assertEquals(List.of("/api/version", "/v1/chat/completions"), this.paths);
		assertEquals("openai", MAPPER.readTree(reply.body()).path("choices").get(0).path("message").path("content").asText());
	}

	@Test
	public void contextSizes() {
		assertEquals(8192, ChatEndpoint.context(3000, 0, 0, 0), "at least 8,192");
		assertEquals(16384, ChatEndpoint.context(9000, 4096, 0, 32768));
		assertEquals(32768, ChatEndpoint.context(9000, 32768, 0, 32768), "never less than loaded");
		assertEquals(16384, ChatEndpoint.context(3000, 0, 16384, 32768), "never less than asked before");
		assertEquals(32768, ChatEndpoint.context(50000, 0, 0, 32768), "at most the model's maximum");
	}

	@Test
	public void maxTokensBecomesNumPredict() {
		ObjectNode body = request(10);
		body.put("max_tokens", 300);
		ObjectNode ollama = ChatEndpoint.ollamaRequest(body, 8192);
		assertEquals(300, ollama.path("options").path("num_predict").asInt());
		assertEquals(8192, ollama.path("options").path("num_ctx").asInt());
		assertFalse(ollama.has("max_tokens"));
	}
}
