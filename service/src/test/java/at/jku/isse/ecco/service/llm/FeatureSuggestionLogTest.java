package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FeatureSuggestionLogTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final FeatureSuggestionLog.Entry ENTRY = new FeatureSuggestionLog.Entry(
			"/ecco/repo/.ecco", "/git/repo", "abc123", "Add reset link", 812, "qwen2.5-coder:32b",
			"login", "password-reset", "login, password-reset", null,
			new LlmFeatureSuggestionClient.Usage(7670, 12, 6100), new SuggestionConfidence(0.9, "login changed these positions before"),
			FeatureSuggestionLog.Outcome.REVIEWED, null, null);

	// the build points the log into the build directory for all tests
	private String location;

	@BeforeEach
	public void saveLocation() {
		this.location = System.getProperty(FeatureSuggestionLog.LOCATION_PROPERTY);
	}

	@AfterEach
	public void restoreLocation() {
		if (this.location == null)
			System.clearProperty(FeatureSuggestionLog.LOCATION_PROPERTY);
		else
			System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, this.location);
	}

	/** The parents the reply named for its new features are recorded, sorted; an entry without any has none. */
	@Test
	public void theParentsOfNewFeaturesAreRecorded() throws Exception {
		FeatureSuggestionLog.Entry entry = new FeatureSuggestionLog.Entry(
				"/ecco/repo/.ecco", "/git/repo", "abc123", "Add reset link", 812, "m", "BASE, login", "password-reset, email", "BASE, login, password-reset, email",
				null, null, null, FeatureSuggestionLog.Outcome.AUTO, null, null, java.util.Map.of("password-reset", "login", "email", "BASE"));

		JsonNode line = MAPPER.readTree(FeatureSuggestionLog.toJson(entry.committedAs("BASE, login, password-reset, email")));
		assertEquals("{\"email\":\"BASE\",\"password-reset\":\"login\"}", line.path("parents").toString());
		assertEquals(entry.parents(), entry.skipped().parents(), "kept through the outcome");
		assertTrue(MAPPER.readTree(FeatureSuggestionLog.toJson(ENTRY)).path("parents").isMissingNode());
	}

	@Test
	public void entriesAreAppendedOneJsonObjectPerLine(@TempDir Path tmp) throws Exception {
		Path log = tmp.resolve("sub").resolve("suggestions.jsonl");
		System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());

		FeatureSuggestionLog.append(ENTRY.committedAs("login, password-reset, email")
				.withChanges(new ImportedFeatureTraces.Changes(java.util.Map.of("src/Login.java", java.util.List.of("Login.reset")),
						java.util.Set.of("src/Login.java"), java.util.Map.of("src/Auth.java", "src/OldAuth.java"))));
		FeatureSuggestionLog.append(ENTRY.skipped());

		List<String> lines = Files.readAllLines(log);
		assertEquals(2, lines.size());
		JsonNode first = MAPPER.readTree(lines.get(0));
		assertEquals("/ecco/repo/.ecco", first.path("eccoRepository").asText());
		assertEquals("abc123", first.path("commit").asText());
		assertEquals(812, first.path("diffLines").asInt());
		assertEquals("login", first.path("before").asText());
		assertEquals("password-reset", first.path("suggestion").asText());
		assertEquals("login, password-reset", first.path("proposed").asText());
		assertEquals("login, password-reset, email", first.path("committed").asText());
		assertEquals("reviewed", first.path("outcome").asText());
		assertEquals(7670, first.path("promptTokens").asInt());
		assertEquals(6100, first.path("millis").asLong());
		assertEquals(0.9, first.path("confidence").asDouble());
		assertEquals("login changed these positions before", first.path("confidenceReason").asText());
		assertTrue(first.path("failure").isNull());
		assertEquals("Login.reset", first.path("positions").path("src/Login.java").get(0).asText());
		assertEquals("src/Login.java", first.path("added").get(0).asText());
		assertEquals("src/OldAuth.java", first.path("renamed").path("src/Auth.java").asText());
		JsonNode second = MAPPER.readTree(lines.get(1));
		assertEquals("skipped", second.path("outcome").asText());
		assertTrue(second.path("committed").isNull());
		assertTrue(second.path("positions").isMissingNode(), "no positions known");
	}

	@Test
	public void aLogThatCannotBeWrittenDoesNotThrow(@TempDir Path tmp) throws Exception {
		Path file = Files.writeString(tmp.resolve("file"), "x");
		System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, file.resolve("suggestions.jsonl").toString());

		FeatureSuggestionLog.append(ENTRY);
	}

	@Test
	public void theDefaultLocationIsInTheUsersEccoDirectory() {
		System.clearProperty(FeatureSuggestionLog.LOCATION_PROPERTY);
		assertEquals(Path.of(System.getProperty("user.home"), ".ecco", "llm-suggestions.jsonl"), FeatureSuggestionLog.location());
	}
}
