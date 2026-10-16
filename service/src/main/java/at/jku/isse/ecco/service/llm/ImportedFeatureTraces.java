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
import java.util.HashMap;
import java.util.HashSet;
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
 * <p>
 * The traces also suggest where a feature sits in the feature model ({@link #relationSuggestions}):
 * a place - a file, or a class, method or section in it - is owned by the features of the commit
 * that created the file (inside a new file every place is first changed by its creator), and
 * later by those that first changed it; each later change votes for the owners of the nearest
 * place around it. A feature whose changes lie far more often than others' in the places one
 * feature created likely depends on that feature (experiments/llm-feature-suggestion/nesting.py):
 * as a subfeature if it has no code of its own - no source file it created outside the other
 * feature's directories - and as a cross-tree REQUIRES if it has.
 */
public final class ImportedFeatureTraces {

	private static final Logger LOGGER = Logger.getLogger(ImportedFeatureTraces.class.getName());

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Separators of nested position names: "Zip.Entry.read", "geo::Point::norm", "Install > Docker". */
	private static final String[] NESTING = {" > ", "::", "."};

	/** Per feature, per file, the positions changed (empty: the file without known positions). */
	private final Map<String, Map<String, Set<String>>> traces = new TreeMap<>();

	/** A suggested parent is shown only if the feature's changes lie this many times more often in its places than all features' do... */
	static final double MIN_LIFT = 5;
	/** ...and at least this share of them. */
	static final double MIN_SHARE = 0.2;
	/** Fewer changes in others' places than this say nothing. */
	static final int MIN_VOTES = 10;

	/** Owners of places, key "source\0path\0position" (position "" for the file itself), in the order they were first changed. */
	private final Map<String, Set<String>> owners = new HashMap<>();
	/** Files created within the recorded history, "source\0path". */
	private final Set<String> created = new HashSet<>();
	/** Per feature, per owner, how many of its changes lie in that owner's places. */
	private final Map<String, Map<String, Integer>> votes = new TreeMap<>();
	/** The features of the first commit that created files: everything nests in them. */
	private Set<String> base;
	/** Owners of directories, "source\0directory": the features of the commit that added the first file to it. */
	private final Map<String, Set<String>> directoryOwners = new HashMap<>();
	/** Per feature, the source files its commits created, "source\0path". */
	private final Map<String, Set<String>> createdSources = new HashMap<>();

	/**
	 * What a commit changed: per file, the positions it changed in it; the files it added; and
	 * {@code renamed}, new path to old path.
	 */
	public record Changes(Map<String, ? extends Collection<String>> positions, Set<String> added, Map<String, String> renamed) {

		/** From {@link GitHistoryReader#getChangedPositions}. */
		public static Changes of(Map<String, GitHistoryReader.ChangedFile> files) {
			Map<String, Set<String>> positions = new LinkedHashMap<>();
			Set<String> added = new LinkedHashSet<>();
			Map<String, String> renamed = new LinkedHashMap<>();
			for (GitHistoryReader.ChangedFile file : files.values()) {
				positions.put(file.path(), file.positions());
				if (file.kind() == GitHistoryReader.ChangeKind.ADDED)
					added.add(file.path());
				if (file.kind() == GitHistoryReader.ChangeKind.RENAMED && file.oldPath() != null)
					renamed.put(file.path(), file.oldPath());
			}
			return new Changes(positions, added, renamed);
		}
	}

	/**
	 * Records an imported commit of {@code source} (a Git clone): its changes go to the traces of
	 * its labels, and to the ownership and votes {@link #parentSuggestions} is computed from.
	 * Commits must come in their order.
	 */
	public synchronized void record(String source, Collection<String> labels, Changes changes) {
		if (labels.isEmpty())
			return;
		this.add(labels, changes.positions());
		Set<String> labelSet = new LinkedHashSet<>(labels);
		if (!changes.added().isEmpty() && this.base == null)
			this.base = labelSet;
		for (String path : changes.added()) {
			this.created.add(source + "\0" + path);
			this.directoryOwners.putIfAbsent(source + "\0" + directory(path), labelSet);
			if (ChangeEvidence.kind(path).equals("source"))
				for (String label : labelSet)
					this.createdSources.computeIfAbsent(label, l -> new LinkedHashSet<>()).add(source + "\0" + path);
		}
		// a renamed file keeps its owners: a restructuring commit does not create what it moves
		changes.renamed().forEach((to, from) -> {
			if (this.created.contains(source + "\0" + from)) {
				this.created.add(source + "\0" + to);
				String prefix = source + "\0" + from + "\0";
				for (Map.Entry<String, Set<String>> owner : new ArrayList<>(this.owners.entrySet()))
					if (owner.getKey().startsWith(prefix))
						this.owners.putIfAbsent(source + "\0" + to + "\0" + owner.getKey().substring(prefix.length()), owner.getValue());
			}
		});
		changes.positions().forEach((path, positions) -> {
			String file = source + "\0" + path + "\0";
			if (!this.created.contains(source + "\0" + path))
				return;
			// this commit's changes vote for the owners of the places around them, as they were before it
			if (!changes.added().contains(path)) {
				for (String position : positions) {
					Set<String> owner = null;
					for (String area = parent(position); area != null && owner == null; area = parent(area))
						owner = this.owners.get(file + area);
					if (owner == null)
						owner = this.owners.get(file);
					if (owner != null)
						for (String label : labelSet)
							for (String o : owner)
								this.votes.computeIfAbsent(label, l -> new TreeMap<>()).merge(o, 1, Integer::sum);
				}
			}
			// then it owns what it changed first
			this.owners.putIfAbsent(file, labelSet);
			for (String position : positions)
				for (String area = position; area != null; area = parent(area))
					this.owners.putIfAbsent(file + area, labelSet);
		});
	}

	/** How a feature relates to another in the feature model. */
	public enum Relation {
		/** a child of the other in the tree: its code lies inside the other's */
		SUBFEATURE,
		/** a cross-tree REQUIRES: it has code of its own, and changes the other's */
		REQUIRES
	}

	/**
	 * A suggested relation in the feature model: {@code share} of the feature's changes in places
	 * other features own lie in places {@code other} owns, {@code lift} times as often as all
	 * features' changes do; {@code ownSources} source files the feature created outside the other's
	 * directories - none makes it a subfeature, any a REQUIRES.
	 */
	public record RelationSuggestion(String feature, String other, Relation relation, double share, double lift, int ownSources) {
	}

	/**
	 * Relations suggested by where features' changes lie (see the class comment), only strong ones:
	 * at least {@value #MIN_LIFT} times as often as all features', and {@value #MIN_SHARE} of the
	 * feature's changes - of those, the one with the largest share. None for the base features,
	 * which everything nests in.
	 */
	public synchronized List<RelationSuggestion> relationSuggestions() {
		Map<String, Integer> all = new HashMap<>();
		int grand = 0;
		for (Map.Entry<String, Map<String, Integer>> feature : this.votes.entrySet())
			for (Map.Entry<String, Integer> owner : feature.getValue().entrySet())
				if (!owner.getKey().equals(feature.getKey())) {
					all.merge(owner.getKey(), owner.getValue(), Integer::sum);
					grand += owner.getValue();
				}
		List<RelationSuggestion> suggestions = new ArrayList<>();
		for (Map.Entry<String, Map<String, Integer>> feature : this.votes.entrySet()) {
			if (this.base != null && this.base.contains(feature.getKey()))
				continue;
			int total = feature.getValue().entrySet().stream().filter(e -> !e.getKey().equals(feature.getKey())).mapToInt(Map.Entry::getValue).sum();
			if (total < MIN_VOTES)
				continue;
			// of the strong candidates, the one with most of the feature's changes: a high lift alone
			// can come from a few changes in a catch-all feature's code
			RelationSuggestion best = null;
			for (Map.Entry<String, Integer> owner : feature.getValue().entrySet()) {
				if (owner.getKey().equals(feature.getKey()) || owner.getValue() < 3)
					continue;
				double share = owner.getValue() / (double) total;
				double lift = share / (all.get(owner.getKey()) / (double) grand);
				if (lift >= MIN_LIFT && share >= MIN_SHARE && (best == null || share > best.share())) {
					int ownSources = this.ownSources(feature.getKey(), owner.getKey());
					best = new RelationSuggestion(feature.getKey(), owner.getKey(), ownSources == 0 ? Relation.SUBFEATURE : Relation.REQUIRES, share, lift, ownSources);
				}
			}
			if (best != null)
				suggestions.add(best);
		}
		return suggestions;
	}

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

	/** The source files {@code feature}'s commits created outside the directories {@code other}'s commits created. */
	private int ownSources(String feature, String other) {
		int own = 0;
		for (String file : this.createdSources.getOrDefault(feature, Set.of())) {
			int cut = file.indexOf('\0');
			Set<String> owner = this.directoryOwners.get(file.substring(0, cut + 1) + directory(file.substring(cut + 1)));
			if (owner == null || !owner.contains(other))
				own++;
		}
		return own;
	}

	private static String directory(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	/**
	 * The suggestions the configurations agree with: a subfeature and a REQUIRES both mean a
	 * configuration with the feature also has the other - one without it contradicts them.
	 */
	public static List<RelationSuggestion> consistentWith(List<RelationSuggestion> suggestions, Collection<? extends Collection<String>> configurations) {
		List<RelationSuggestion> consistent = new ArrayList<>();
		for (RelationSuggestion suggestion : suggestions)
			if (configurations.stream().noneMatch(c -> c.contains(suggestion.feature()) && !c.contains(suggestion.other())))
				consistent.add(suggestion);
		return consistent;
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
					imported.record(gitRepository.toString(), labels, changes(entry, gitRepository, reader, dispatchReader));
			} catch (IOException | RuntimeException e) {
				LOGGER.log(Level.FINE, "Passed over a suggestion log line: " + line, e);
			}
		}
		return imported;
	}

	/**
	 * The commits imported into {@code eccoRepository} from any Git clone, from what the log
	 * recorded alone - without the clones: entries without recorded changes are passed over. Cheap
	 * enough for a view to call on every refresh.
	 */
	public static ImportedFeatureTraces fromLog(Path log, String eccoRepository) {
		ImportedFeatureTraces imported = new ImportedFeatureTraces();
		if (!Files.isRegularFile(log))
			return imported;
		try {
			for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
				try {
					JsonNode entry = MAPPER.readTree(line);
					if (!eccoRepository.equals(entry.path("eccoRepository").asText(null)) || "skipped".equals(entry.path("outcome").asText())
							|| !entry.path("positions").isObject())
						continue;
					Set<String> labels = labels(entry.path("before").asText(null), entry.path("suggestion").asText(null), entry.path("committed").asText(null));
					if (!labels.isEmpty())
						imported.record(entry.path("gitRepository").asText(""), labels, logged(entry));
				} catch (IOException | RuntimeException e) {
					LOGGER.log(Level.FINE, "Passed over a suggestion log line: " + line, e);
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not read " + log, e);
		}
		return imported;
	}

	private static Changes changes(JsonNode entry, Path gitRepository, GitHistoryReader reader, DispatchReader dispatchReader) {
		if (entry.path("positions").isObject())
			return logged(entry);
		String commit = entry.path("commit").asText();
		if (dispatchReader != null)
			return Changes.of(reader.getChangedPositions(gitRepository, commit, dispatchReader));
		Map<String, List<String>> changed = new LinkedHashMap<>();
		for (String path : reader.getChangedFiles(gitRepository, commit))
			changed.put(path, List.of());
		return new Changes(changed, Set.of(), Map.of());
	}

	/** The changes an entry recorded: positions, and - in entries written since they are recorded - the files added and renamed. */
	private static Changes logged(JsonNode entry) {
		Map<String, List<String>> positions = new LinkedHashMap<>();
		for (Iterator<Map.Entry<String, JsonNode>> files = entry.path("positions").fields(); files.hasNext(); ) {
			Map.Entry<String, JsonNode> file = files.next();
			List<String> names = new ArrayList<>();
			file.getValue().forEach(position -> names.add(position.asText()));
			positions.put(file.getKey(), names);
		}
		Set<String> added = new LinkedHashSet<>();
		entry.path("added").forEach(path -> added.add(path.asText()));
		Map<String, String> renamed = new LinkedHashMap<>();
		for (Iterator<Map.Entry<String, JsonNode>> files = entry.path("renamed").fields(); files.hasNext(); ) {
			Map.Entry<String, JsonNode> file = files.next();
			renamed.put(file.getKey(), file.getValue().asText());
		}
		return new Changes(positions, added, renamed);
	}
}
