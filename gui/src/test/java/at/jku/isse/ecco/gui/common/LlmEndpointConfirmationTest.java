package at.jku.isse.ecco.gui.common;

import at.jku.isse.ecco.service.LlmPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Asked before code first goes to an LLM server on another machine (gap #90). */
public class LlmEndpointConfirmationTest {

	private static final String CONFIRMED_KEY = "llmConfirmedEndpoint";
	private static final String REMOTE = "http://llm.example.invalid:11434";
	private final Predicate<String> originalAsker = LlmEndpointConfirmation.asker;
	private final List<String> asked = new ArrayList<>();
	private String saved;

	@BeforeEach
	public void forgetConfirmation() {
		this.saved = Preferences.userNodeForPackage(LlmPreferences.class).get(CONFIRMED_KEY, null);
		LlmPreferences.clearConfirmedEndpoint();
	}

	@AfterEach
	public void restore() {
		LlmEndpointConfirmation.asker = this.originalAsker;
		if (this.saved == null)
			LlmPreferences.clearConfirmedEndpoint();
		else
			Preferences.userNodeForPackage(LlmPreferences.class).put(CONFIRMED_KEY, this.saved);
	}

	private void answer(boolean agree) {
		LlmEndpointConfirmation.asker = warning -> {
			this.asked.add(warning);
			return agree;
		};
	}

	@Test
	public void aServerOnThisMachineIsNotAskedAbout() {
		this.answer(false);
		assertTrue(LlmEndpointConfirmation.confirm("http://localhost:11434"));
		assertEquals(List.of(), this.asked);
	}

	@Test
	public void declinedIsAskedAgain() {
		this.answer(false);
		assertFalse(LlmEndpointConfirmation.confirm(REMOTE));
		assertFalse(LlmEndpointConfirmation.confirm(REMOTE));
		assertEquals(2, this.asked.size());
		assertTrue(this.asked.get(0).contains("llm.example.invalid"), this.asked.get(0));
	}

	@Test
	public void agreedIsNotAskedAgain() {
		this.answer(true);
		assertTrue(LlmEndpointConfirmation.confirm(REMOTE));
		this.answer(false);
		assertTrue(LlmEndpointConfirmation.confirm(REMOTE));
		assertEquals(1, this.asked.size());
		assertFalse(LlmEndpointConfirmation.confirm("http://other.example.invalid:11434"), "another server is asked about");
	}
}
