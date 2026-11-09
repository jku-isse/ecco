package at.jku.isse.ecco.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which LLM servers get code without asking (NFR-S6d, gap #90). */
public class LlmPreferencesTest {

	private static final String CONFIRMED_KEY = "llmConfirmedEndpoint";
	private String saved;

	@BeforeEach
	public void saveConfirmation() {
		this.saved = java.util.prefs.Preferences.userNodeForPackage(LlmPreferences.class).get(CONFIRMED_KEY, null);
		LlmPreferences.clearConfirmedEndpoint();
	}

	@AfterEach
	public void restoreConfirmation() {
		if (this.saved == null)
			LlmPreferences.clearConfirmedEndpoint();
		else
			java.util.prefs.Preferences.userNodeForPackage(LlmPreferences.class).put(CONFIRMED_KEY, this.saved);
	}

	@Test
	public void serversOnThisMachine() {
		for (String url : new String[]{"http://localhost:11434", "http://LOCALHOST:1/v1", "http://ollama.localhost:11434",
				"http://127.0.0.1:11434", "http://127.1.2.3:8080", "http://[::1]:11434", "http://0.0.0.0:11434"})
			assertTrue(LlmPreferences.isOnThisMachine(url), url);
	}

	@Test
	public void serversElsewhere() {
		for (String url : new String[]{"https://api.openai.com", "http://llm.example.invalid:11434", "http://192.0.2.7:11434",
				"http://[2001:db8::1]:11434", "localhost:11434", "not a url", ""})
			assertFalse(LlmPreferences.isOnThisMachine(url), url);
	}

	@Test
	public void aConfirmedServerUntilAnotherIsSet() {
		String server = "http://llm.example.invalid:11434";
		assertFalse(LlmPreferences.mayReceiveCode(server));
		LlmPreferences.confirmEndpoint(server + "/v1/chat/completions");
		assertTrue(LlmPreferences.mayReceiveCode(server), "the path does not matter");
		assertTrue(LlmPreferences.mayReceiveCode("HTTP://LLM.example.invalid:11434"));
		assertFalse(LlmPreferences.mayReceiveCode("http://llm.example.invalid:8080"), "another port");
		assertFalse(LlmPreferences.mayReceiveCode("https://llm.example.invalid:11434"), "another scheme");
		LlmPreferences.confirmEndpoint("http://other.example.invalid");
		assertFalse(LlmPreferences.mayReceiveCode(server), "one confirmed server at a time");
		assertTrue(LlmPreferences.mayReceiveCode("http://localhost:11434"));
	}
}
