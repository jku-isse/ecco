package at.jku.isse.ecco.service.llm;

import at.jku.isse.ecco.feature.ConfigurationText;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The files each feature's commits of a Git import changed - what the LLM feature suggestion is
 * shown with each known feature, to tell a commit continuing a feature from one starting a new
 * one (with bare names it filed 11 of 13 new features of a Vite replay under a broad known one).
 * <p>
 * Not taken from the repository: an import commits cumulative configurations (every feature seen
 * so far), so ECCO can attribute content only to the features new in a commit - in a 40-commit
 * Vite import the first feature owned the whole first snapshot (546 files) and features changed
 * later under an unchanged configuration owned nothing of theirs. Instead, an imported commit's
 * changed files go to its labels: the features it added, and the suggested ones that were kept.
 * Earlier import runs are read back from the {@link FeatureSuggestionLog}.
 */
public final class ImportedFeatureFiles {

	private static final Logger LOGGER = Logger.getLogger(ImportedFeatureFiles.class.getName());

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final Map<String, Set<String>> files = new TreeMap<>();

	/**
	 * What an imported commit was about: the features {@code committed} has that {@code before}
	 * did not, and the features {@code suggestion} named ("-name" removals aside) that
	 * {@code committed} kept. Empty for a commit that added nothing and had no suggestion kept.
	 */
	public static Set<String> labels(String before, String suggestion, String committed) {
		Set<String> committedFeatures = ConfigurationText.tokens(committed);
		Set<String> labels = new LinkedHashSet<>(committedFeatures);
		labels.removeAll(ConfigurationText.tokens(before));
		for (String token : ConfigurationText.tokens(suggestion))
			if (!token.startsWith("-") && committedFeatures.contains(token))
				labels.add(token);
		return labels;
	}

	public synchronized void add(Collection<String> labels, Collection<String> changedFiles) {
		for (String label : labels)
			this.files.computeIfAbsent(label, l -> new TreeSet<>()).addAll(changedFiles);
	}

	/** Feature name to the files its commits changed, each sorted - a copy. */
	public synchronized Map<String, Set<String>> files() {
		Map<String, Set<String>> copy = new TreeMap<>();
		this.files.forEach((feature, paths) -> copy.put(feature, new TreeSet<>(paths)));
		return copy;
	}

	/**
	 * The commits of {@code gitRepository} imported into {@code eccoRepository} in earlier runs, as
	 * the log recorded them, with their changed files read from the clone again. Entries of other
	 * repositories, skipped commits, and commits the clone no longer has are passed over.
	 */
	public static ImportedFeatureFiles fromLog(Path log, String eccoRepository, Path gitRepository, GitHistoryReader reader) {
		ImportedFeatureFiles imported = new ImportedFeatureFiles();
		if (!Files.isRegularFile(log))
			return imported;
		List<String> lines;
		try {
			lines = Files.readAllLines(log, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not read " + log, e);
			return imported;
		}
		for (String line : lines) {
			try {
				JsonNode entry = MAPPER.readTree(line);
				if (!eccoRepository.equals(entry.path("eccoRepository").asText(null))
						|| !gitRepository.toString().equals(entry.path("gitRepository").asText(null))
						|| "skipped".equals(entry.path("outcome").asText()))
					continue;
				Set<String> labels = labels(entry.path("before").asText(null), entry.path("suggestion").asText(null), entry.path("committed").asText(null));
				if (!labels.isEmpty())
					imported.add(labels, reader.getChangedFiles(gitRepository, entry.path("commit").asText()));
			} catch (IOException | RuntimeException e) {
				LOGGER.log(Level.FINE, "Passed over a suggestion log line: " + line, e);
			}
		}
		return imported;
	}
}
