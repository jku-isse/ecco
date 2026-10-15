package at.jku.isse.ecco.service.llm;

import at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Row;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The order suggestion for an ORDER warning: what is asked, how a reply is read, and that the order
 * graph's fixed precedences always win over the model's order.
 */
public class LlmOrderSuggestionClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String reply(String content) {
        return MAPPER.createObjectNode().set("choices", MAPPER.createArrayNode().add(
                MAPPER.createObjectNode().set("message", MAPPER.createObjectNode().put("content", content)))).toString();
    }

    @Test
    public void theRequestNumbersTheLinesAndMarksTheMovableOnes() throws Exception {
        String body = LlmOrderSuggestionClient.buildRequestBody("src/a.c", List.of(new Row("int main() {", false), new Row("init();", true)), "qwen");
        JsonNode root = MAPPER.readTree(body);
        assertEquals("qwen", root.path("model").asText());
        assertEquals("json_object", root.path("response_format").path("type").asText());
        assertEquals(LlmOrderSuggestionClient.maxTokens(2), root.path("max_tokens").asInt(), "the answer is capped: a thinking model wrote pages first");
        String user = root.path("messages").get(1).path("content").asText();
        assertEquals("File: src/a.c\n1   int main() {\n2 * init();\n", user);
    }

    @Test
    public void aReplyIsReadAsAPermutation() {
        assertEquals(List.of(0, 2, 1, 3), LlmOrderSuggestionClient.parseOrder(reply("{\"order\": [1, 3, 2, 4]}"), 4));
        assertEquals(List.of(1, 0), LlmOrderSuggestionClient.parseOrder(reply("Here you go:\n```json\n{\"order\": [2, 1]}\n```"), 2), "inside prose and fences");
        assertNull(LlmOrderSuggestionClient.parseOrder(reply("{\"order\": [1, 1, 2]}"), 3), "a number twice");
        assertNull(LlmOrderSuggestionClient.parseOrder(reply("{\"order\": [1, 2]}"), 3), "one missing");
        assertNull(LlmOrderSuggestionClient.parseOrder(reply("{\"order\": [0, 1, 2]}"), 3), "out of range");
        assertNull(LlmOrderSuggestionClient.parseOrder(reply("no idea"), 3));
        assertNull(LlmOrderSuggestionClient.parseOrder("not json", 3));
    }

    /** The model's order where the graph leaves a choice; the graph's fixed precedences always. */
    @Test
    public void fixedPrecedencesWinOverTheModel() {
        // rows 0 and 3 are fixed: 0 before 3; the model wants 3 first
        boolean[][] mustPrecede = new boolean[4][4];
        mustPrecede[0][3] = true;
        assertEquals(List.of(2, 1, 0, 3), LlmOrderSuggestionClient.respecting(List.of(3, 2, 1, 0), mustPrecede));
        // no precedences: the model's order as it is
        assertEquals(List.of(3, 2, 1, 0), LlmOrderSuggestionClient.respecting(List.of(3, 2, 1, 0), new boolean[4][4]));
        // a chain 0 < 1 < 2: only that order
        boolean[][] chain = new boolean[3][3];
        chain[0][1] = chain[1][2] = chain[0][2] = true;
        assertEquals(List.of(0, 1, 2), LlmOrderSuggestionClient.respecting(List.of(2, 1, 0), chain));
    }

    @Test
    @Timeout(30)
    public void anEndpointIsAskedAndItsOrderReturned() throws Exception {
        List<String> received = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = reply("{\"order\": [2, 1]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            LlmOrderSuggestionClient client = new LlmOrderSuggestionClient("http://localhost:" + server.getAddress().getPort(), "m");
            LlmOrderSuggestionClient.Suggestion suggestion = client.suggestOrder("a.txt", List.of(new Row("a", true), new Row("b", true)));
            assertTrue(suggestion.succeeded(), suggestion.failureReason());
            assertEquals(List.of(1, 0), suggestion.order());
            assertEquals(1, received.size());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Timeout(30)
    public void failuresAreReportedNotThrown() throws Exception {
        LlmOrderSuggestionClient.Suggestion unreachable = new LlmOrderSuggestionClient("http://localhost:1", "m")
                .suggestOrder("a.txt", List.of(new Row("a", true), new Row("b", true)));
        assertFalse(unreachable.succeeded());
        assertNotNull(unreachable.failureReason());

        List<Row> many = new ArrayList<>();
        for (int i = 0; i <= LlmOrderSuggestionClient.MAX_ROWS; i++)
            many.add(new Row("line " + i, true));
        LlmOrderSuggestionClient.Suggestion tooMany = new LlmOrderSuggestionClient("http://localhost:1", "m").suggestOrder("a.txt", many);
        assertFalse(tooMany.succeeded());
        assertTrue(tooMany.failureReason().contains("too many"), tooMany.failureReason());
    }
}
