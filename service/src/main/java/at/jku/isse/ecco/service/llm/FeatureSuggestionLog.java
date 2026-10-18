package at.jku.isse.ecco.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records each commit of a Git import with its LLM feature suggestion, if one was asked for, and
 * what became of it: the configuration actually committed (as reviewed, or unattended), or that
 * the commit was skipped, and the positions it changed. The commits imported this way are also
 * where {@link ImportedFeatureTraces} finds where each feature's commits changed things.
 * One JSON object per line, appended to {@code ~/.ecco/llm-suggestions.jsonl} (system property
 * {@value #LOCATION_PROPERTY} to put it elsewhere). Suggestion and outcome side by side are what
 * any change to the prompt or the model can be scored against; the usage says where the time
 * went (for scoring, only entries with a model asked count). Writing it never fails an import: a write error is logged and the entry dropped.
 */
public final class FeatureSuggestionLog {

	public static final String LOCATION_PROPERTY = "ecco.llmSuggestionLog";

	private static final Logger LOGGER = Logger.getLogger(FeatureSuggestionLog.class.getName());

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** How a suggested commit ended. */
	public enum Outcome {
		/** imported after review, with the configuration the reviewer left in the field */
		REVIEWED,
		/** imported without review, with the suggested configuration */
		AUTO,
		/** left out on the review screen */
		SKIPPED,
		/**
		 * not imported, in an import of tagged commits only: a commit between two of them, whose
		 * features go into the next one imported - {@code committed} is the configuration it would
		 * have had, and its changes count for its features as an imported commit's do
		 */
		FOLDED
	}

	/**
	 * One imported or skipped commit, its suggestion and its outcome. {@code model}, {@code
	 * suggestion} and {@code usage} are null when no suggestion was asked for. {@code before} is the configuration of the previous imported
	 * commit, {@code suggestion} the LLM's raw reply (features added, "-name" for removed ones),
	 * {@code proposed} the configuration the reply made of {@code before}, {@code committed} the one
	 * actually committed ({@code null} when skipped). {@code failure} is why there was no
	 * suggestion, if there was none. {@code confidence} is how far the suggestion agreed with where
	 * the commit changed things (null if not known). {@code changes} are the files the commit changed with the
	 * positions in them, the files it added and those it renamed (see
	 * {@link at.jku.isse.ecco.service.git.GitHistoryReader#getChangedPositions}), null if not known.
	 * {@code parents} are the parents the reply named for the new features it suggested - the known
	 * feature each refines, or BASE (see {@link LlmFeatureSuggestionClient#parentsOf}).
	 */
	public record Entry(String eccoRepository, String gitRepository, String commitId, String message, int diffLines, String model,
						String before, String suggestion, String proposed, String failure,
						LlmFeatureSuggestionClient.Usage usage, SuggestionConfidence confidence, Outcome outcome, String committed, ImportedFeatureTraces.Changes changes,
						Map<String, String> parents) {

		public Entry {
			parents = parents == null ? Map.of() : Map.copyOf(parents);
		}

		public Entry(String eccoRepository, String gitRepository, String commitId, String message, int diffLines, String model,
					 String before, String suggestion, String proposed, String failure,
					 LlmFeatureSuggestionClient.Usage usage, SuggestionConfidence confidence, Outcome outcome, String committed, ImportedFeatureTraces.Changes changes) {
			this(eccoRepository, gitRepository, commitId, message, diffLines, model, before, suggestion, proposed, failure, usage, confidence, outcome, committed, changes, Map.of());
		}

		/** This entry with {@code committed} as the configuration it was committed with. */
		public Entry committedAs(String committed) {
			return new Entry(this.eccoRepository, this.gitRepository, this.commitId, this.message, this.diffLines, this.model, this.before,
					this.suggestion, this.proposed, this.failure, this.usage, this.confidence, this.outcome, committed, this.changes, this.parents);
		}

		/** This entry with what its commit changed. */
		public Entry withChanges(ImportedFeatureTraces.Changes changes) {
			return new Entry(this.eccoRepository, this.gitRepository, this.commitId, this.message, this.diffLines, this.model, this.before,
					this.suggestion, this.proposed, this.failure, this.usage, this.confidence, this.outcome, this.committed, changes, this.parents);
		}

		/** This entry for a commit not imported, whose features go into the next imported one (see {@link Outcome#FOLDED}). */
		public Entry folded() {
			return new Entry(this.eccoRepository, this.gitRepository, this.commitId, this.message, this.diffLines, this.model, this.before,
					this.suggestion, this.proposed, this.failure, this.usage, this.confidence, Outcome.FOLDED, this.committed, this.changes, this.parents);
		}

		/** This entry for a skipped commit. */
		public Entry skipped() {
			return new Entry(this.eccoRepository, this.gitRepository, this.commitId, this.message, this.diffLines, this.model, this.before,
					this.suggestion, this.proposed, this.failure, this.usage, this.confidence, Outcome.SKIPPED, null, this.changes, this.parents);
		}
	}

	private FeatureSuggestionLog() {
	}

	public static Path location() {
		String location = System.getProperty(LOCATION_PROPERTY);
		return location != null && !location.isBlank()
				? Path.of(location)
				: Path.of(System.getProperty("user.home"), ".ecco", "llm-suggestions.jsonl");
	}

	public static synchronized void append(Entry entry) {
		Path file = location();
		try {
			if (file.getParent() != null)
				Files.createDirectories(file.getParent());
			Files.writeString(file, toJson(entry) + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException e) {
			LOGGER.log(Level.WARNING, "Could not record the feature suggestion for " + entry.commitId() + " in " + file, e);
		}
	}

	/** One line of the log. Package-visible for testing. */
	static String toJson(Entry entry) {
		ObjectNode line = MAPPER.createObjectNode();
		line.put("time", Instant.now().toString());
		line.put("eccoRepository", entry.eccoRepository());
		line.put("gitRepository", entry.gitRepository());
		line.put("commit", entry.commitId());
		line.put("message", entry.message());
		line.put("diffLines", entry.diffLines());
		line.put("model", entry.model());
		line.put("before", entry.before());
		line.put("suggestion", entry.suggestion());
		line.put("proposed", entry.proposed());
		line.put("failure", entry.failure());
		if (entry.usage() != null) {
			line.put("promptTokens", entry.usage().promptTokens());
			line.put("completionTokens", entry.usage().completionTokens());
			line.put("millis", entry.usage().millis());
		}
		if (entry.confidence() != null) {
			line.put("confidence", entry.confidence().value());
			line.put("confidenceReason", entry.confidence().reason());
		}
		line.put("outcome", entry.outcome().name().toLowerCase());
		line.put("committed", entry.committed());
		if (!entry.parents().isEmpty()) {
			// sorted, so a line reads the same however the reply ordered them
			ObjectNode parents = line.putObject("parents");
			new java.util.TreeMap<>(entry.parents()).forEach(parents::put);
		}
		if (entry.changes() != null) {
			ObjectNode positions = line.putObject("positions");
			entry.changes().positions().forEach((path, names) -> {
				com.fasterxml.jackson.databind.node.ArrayNode array = positions.putArray(path);
				names.forEach(array::add);
			});
			com.fasterxml.jackson.databind.node.ArrayNode added = line.putArray("added");
			entry.changes().added().forEach(added::add);
			ObjectNode renamed = line.putObject("renamed");
			entry.changes().renamed().forEach(renamed::put);
		}
		return line.toString();
	}
}
