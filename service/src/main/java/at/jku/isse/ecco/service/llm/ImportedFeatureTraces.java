package at.jku.isse.ecco.service.llm;

import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.feature.ConfigurationText;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Where each feature's commits of a Git import changed things: files, and positions in them
 * (classes, methods, sections - see {@link GitHistoryReader#getChangedPositions}). What the LLM
 * feature suggestion is shown, to tell a commit continuing a feature from one starting a new one
 * (with bare names it filed 11 of 13 new features of a Vite replay under a broad known one).
 * Features are cross-cutting: a feature's trace has its sources, tests, documentation and build
 * files alike, so a change to a README section points to the feature that section is about.
 * <p>
 * Not taken from the repository: an import commits cumulative configurations (every feature seen
 * so far), so ECCO can attribute content only to the features new in a commit - in a 40-commit
 * Vite import the first feature owned the whole first snapshot (546 files) and features changed
 * later under an unchanged configuration owned nothing of theirs. Instead, an imported commit's
 * changes go to its labels: the features it added, and the suggested ones that were kept. Earlier
 * import runs are read back from the {@link FeatureSuggestionLog}.
 */
public final class ImportedFeatureTraces {

	private static final Logger LOGGER = Logger.getLogger(ImportedFeatureTraces.class.getName());

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Separators of nested position names: "Zip.Entry.read", "geo::Point::norm", "Install > Docker". */
	private static final String[] NESTING = {" > ", "::", "."};

	/** Per feature, per file, the positions changed (empty: the file without known positions). */
	private final Map<String, Map<String, Set<String>>> traces = new TreeMap<>();

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

	/** Adds a commit's changes, {@code changed} (file to its changed positions), to each of its labels. */
	public synchronized void add(Collection<String> labels, Map<String, ? extends Collection<String>> changed) {
		for (String label : labels) {
			Map<String, Set<String>> trace = this.traces.computeIfAbsent(label, l -> new TreeMap<>());
			changed.forEach((path, positions) -> trace.computeIfAbsent(path, p -> new TreeSet<>()).addAll(positions));
		}
	}

	/** Feature name to the files its commits changed, each sorted - a copy. */
	public synchronized Map<String, Set<String>> files() {
		Map<String, Set<String>> copy = new TreeMap<>();
		this.traces.forEach((feature, trace) -> copy.put(feature, new TreeSet<>(trace.keySet())));
		return copy;
	}

	/** Feature name to the positions its commits changed, "file#position", each sorted - a copy. */
	public synchronized Map<String, Set<String>> positions() {
		Map<String, Set<String>> copy = new TreeMap<>();
		this.traces.forEach((feature, trace) -> {
			Set<String> positions = new TreeSet<>();
			trace.forEach((path, names) -> names.forEach(name -> positions.add(path + "#" + name)));
			copy.put(feature, positions);
		});
		return copy;
	}

	/**
	 * How a commit's changes overlap a feature's earlier ones, strongest first: the same positions,
	 * other positions in the same enclosing one (another method of the same class, another
	 * subsection of the same section), and the same files.
	 */
	public record Overlap(String feature, List<String> samePositions, List<String> sameEnclosing, int sameFiles) {

		public boolean isEmpty() {
			return this.samePositions.isEmpty() && this.sameEnclosing.isEmpty() && this.sameFiles == 0;
		}
	}

	/** The overlap of {@code changed} (file to its changed positions) with each feature's trace, for the features that have any, strongest first. */
	public synchronized List<Overlap> overlap(Map<String, ? extends Collection<String>> changed) {
		List<Overlap> overlaps = new ArrayList<>();
		for (Map.Entry<String, Map<String, Set<String>>> entry : this.traces.entrySet())
			overlaps.add(overlap(entry.getKey(), entry.getValue(), changed));
		overlaps.removeIf(Overlap::isEmpty);
		overlaps.sort((a, b) -> a.samePositions.size() != b.samePositions.size() ? b.samePositions.size() - a.samePositions.size()
				: a.sameEnclosing.size() != b.sameEnclosing.size() ? b.sameEnclosing.size() - a.sameEnclosing.size()
				: b.sameFiles - a.sameFiles);
		return overlaps;
	}

	static Overlap overlap(String feature, Map<String, Set<String>> trace, Map<String, ? extends Collection<String>> changed) {
		List<String> same = new ArrayList<>();
		Set<String> enclosing = new LinkedHashSet<>();
		int files = 0;
		for (Map.Entry<String, ? extends Collection<String>> file : changed.entrySet()) {
			Set<String> known = trace.get(file.getKey());
			if (known == null)
				continue;
			files++;
			for (String position : file.getValue()) {
				if (known.contains(position)) {
					same.add(position);
					continue;
				}
				for (String other : known) {
					String shared = sharedEnclosing(position, other);
					if (shared != null)
						enclosing.add(shared);
				}
			}
		}
		return new Overlap(feature, same, new ArrayList<>(enclosing), files);
	}

	/** The position enclosing both {@code a} and {@code b} if they are siblings or one encloses the other ("Zip" for "Zip.add" and "Zip.extract"), or null. */
	static String sharedEnclosing(String a, String b) {
		String parentA = parent(a), parentB = parent(b);
		if (parentA != null && (parentA.equals(parentB) || parentA.equals(b)))
			return parentA;
		if (parentB != null && parentB.equals(a))
			return a;
		return null;
	}

	private static String parent(String position) {
		int cut = -1;
		for (String separator : NESTING)
			cut = Math.max(cut, position.lastIndexOf(separator));
		return cut > 0 ? position.substring(0, cut) : null;
	}

	/**
	 * The commits of {@code gitRepository} imported into {@code eccoRepository} in earlier runs, as
	 * the log recorded them: with the positions the log has, or for an entry without them, those
	 * {@code dispatchReader} names now - or the changed files only, without one. Entries of other
	 * repositories, skipped commits, and commits the clone no longer has are passed over.
	 */
	public static ImportedFeatureTraces fromLog(Path log, String eccoRepository, Path gitRepository, GitHistoryReader reader, DispatchReader dispatchReader) {
		ImportedFeatureTraces imported = new ImportedFeatureTraces();
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
					imported.add(labels, changed(entry, gitRepository, reader, dispatchReader));
			} catch (IOException | RuntimeException e) {
				LOGGER.log(Level.FINE, "Passed over a suggestion log line: " + line, e);
			}
		}
		return imported;
	}

	private static Map<String, ? extends Collection<String>> changed(JsonNode entry, Path gitRepository, GitHistoryReader reader, DispatchReader dispatchReader) {
		JsonNode logged = entry.path("positions");
		if (logged.isObject()) {
			Map<String, List<String>> changed = new LinkedHashMap<>();
			for (Iterator<Map.Entry<String, JsonNode>> files = logged.fields(); files.hasNext(); ) {
				Map.Entry<String, JsonNode> file = files.next();
				List<String> positions = new ArrayList<>();
				file.getValue().forEach(position -> positions.add(position.asText()));
				changed.put(file.getKey(), positions);
			}
			return changed;
		}
		String commit = entry.path("commit").asText();
		if (dispatchReader != null) {
			Map<String, List<String>> changed = new LinkedHashMap<>();
			reader.getChangedPositions(gitRepository, commit, dispatchReader).forEach((path, file) -> changed.put(path, new ArrayList<>(file.positions())));
			return changed;
		}
		Map<String, List<String>> changed = new LinkedHashMap<>();
		for (String path : reader.getChangedFiles(gitRepository, commit))
			changed.put(path, List.of());
		return changed;
	}
}
