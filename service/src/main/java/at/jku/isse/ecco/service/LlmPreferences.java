package at.jku.isse.ecco.service;

import java.util.prefs.Preferences;

/**
 * Persists the local LLM endpoint the "Import from Git" feature calls to suggest a feature
 * configuration per imported commit (see {@code at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient}
 * in the same module). Deliberately no API key field: the endpoint is expected to be a local,
 * unauthenticated OpenAI-compatible chat-completions server (e.g. Ollama's default
 * {@code /v1/chat/completions}), matching the same small, per-user runtime setting pattern
 * {@link AdapterPreferences} already established (backed by {@link Preferences}, e.g. the
 * platform registry/plist) rather than the bundled {@code ecco.properties} classpath resource.
 */
public final class LlmPreferences {

	private static final String ENDPOINT_URL_KEY = "llmEndpointUrl";
	private static final String MODEL_NAME_KEY = "llmModelName";

	private static final String DEFAULT_ENDPOINT_URL = "http://localhost:11434/v1/chat/completions";

	private LlmPreferences() {
	}

	public static String getEndpointUrl() {
		return prefs().get(ENDPOINT_URL_KEY, DEFAULT_ENDPOINT_URL);
	}

	public static void setEndpointUrl(String endpointUrl) {
		prefs().put(ENDPOINT_URL_KEY, endpointUrl);
	}

	/** Blank by default, deliberately - forces the user to name a model they've actually pulled rather than guessing one for them. */
	public static String getModelName() {
		return prefs().get(MODEL_NAME_KEY, "");
	}

	public static void setModelName(String modelName) {
		prefs().put(MODEL_NAME_KEY, modelName);
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(LlmPreferences.class);
	}

}
