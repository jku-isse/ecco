package at.jku.isse.ecco.service.llm;

import at.jku.isse.ecco.service.git.GitHistoryReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ImportedFeatureTracesTest {

	@Test
	public void labelsAreTheAddedFeaturesAndTheKeptSuggestions() {
		// a new feature, suggested and kept
		assertEquals(Set.of("B"), ImportedFeatureTraces.labels("A", "B", "A, B"));
		// a known feature changed again: the configuration does not change, the kept suggestion says which
		assertEquals(Set.of("A"), ImportedFeatureTraces.labels("A, B", "A", "A, B"));
		// a suggestion the reviewer replaced
		assertEquals(Set.of("C"), ImportedFeatureTraces.labels("A", "B", "A, C"));
		// a removal is no label; nothing added and no suggestion: no label
		assertEquals(Set.of(), ImportedFeatureTraces.labels("A, B", "-B", "A"));
		assertEquals(Set.of(), ImportedFeatureTraces.labels("A, B", null, "A, B"));
		// the first commit
		assertEquals(Set.of("A", "B"), ImportedFeatureTraces.labels("", null, "A, B"));
	}

	@Test
	public void filesAndPositionsAccumulatePerLabel() {
		ImportedFeatureTraces traces = new ImportedFeatureTraces();
		traces.add(List.of("A"), Map.of("a.c", List.of("add"), "README.md", List.of("Doc > Usage")));
		traces.add(List.of("A", "B"), Map.of("b.c", List.of(), "a.c", List.of("sub")));

		assertEquals(Map.of("A", Set.of("a.c", "b.c", "README.md"), "B", Set.of("a.c", "b.c")), traces.files());
		assertEquals(Map.of("A", Set.of("a.c#add", "a.c#sub", "README.md#Doc > Usage"), "B", Set.of("a.c#sub")), traces.positions());
	}

	/** Same position beats same enclosing position beats same file; a feature without any overlap is left out. */
	@Test
	public void overlapAtThreeLevels() {
		ImportedFeatureTraces traces = new ImportedFeatureTraces();
		traces.add(List.of("encryption"), Map.of("Zip.java", List.of("Zip.encrypt"), "README.md", List.of("Zip4j > Encryption")));
		traces.add(List.of("extract"), Map.of("Zip.java", List.of("Zip.extract"), "Unzip.java", List.of("Unzip.run")));
		traces.add(List.of("progress"), Map.of("Zip.java", List.of()));
		traces.add(List.of("unrelated"), Map.of("Other.java", List.of("Other.x")));

		List<ImportedFeatureTraces.Overlap> overlap = traces.overlap(Map.of(
				"Zip.java", List.of("Zip.encrypt", "Zip.encryptHeader"),
				"README.md", List.of("Zip4j > Encryption > AES")));

		assertEquals(List.of(
				new ImportedFeatureTraces.Overlap("encryption", List.of("Zip.encrypt"), List.of("Zip", "Zip4j > Encryption"), 2),
				new ImportedFeatureTraces.Overlap("extract", List.of(), List.of("Zip"), 1),
				new ImportedFeatureTraces.Overlap("progress", List.of(), List.of(), 1)),
				overlap.stream().map(o -> new ImportedFeatureTraces.Overlap(o.feature(), o.samePositions(),
						o.sameEnclosing().stream().sorted().toList(), o.sameFiles())).toList());
	}

	@Test
	public void sharedEnclosingPositions() {
		assertEquals("Zip", ImportedFeatureTraces.sharedEnclosing("Zip.add", "Zip.extract"));
		assertEquals("Zip", ImportedFeatureTraces.sharedEnclosing("Zip.add", "Zip"));
		assertEquals("Zip", ImportedFeatureTraces.sharedEnclosing("Zip", "Zip.add"));
		assertEquals("geo::Point", ImportedFeatureTraces.sharedEnclosing("geo::Point::norm", "geo::Point::x"));
		assertEquals("Doc > Install", ImportedFeatureTraces.sharedEnclosing("Doc > Install > Docker", "Doc > Install > Windows"));
		assertNull(ImportedFeatureTraces.sharedEnclosing("add", "extract"), "top-level siblings share no position");
		assertNull(ImportedFeatureTraces.sharedEnclosing("Zip.add", "Unzip.add"));
	}

	/** Positions the log has are taken as they are; for older entries without, the changed files. */
	@Test
	public void fromLogUsesTheLoggedPositions(@TempDir Path tmp) throws Exception {
		Path log = tmp.resolve("log.jsonl");
		Files.writeString(log, String.join("\n",
				"{\"eccoRepository\":\"/e\",\"gitRepository\":\"/g\",\"commit\":\"c1\",\"before\":\"\",\"suggestion\":\"A\",\"committed\":\"A\",\"outcome\":\"reviewed\",\"positions\":{\"a.c\":[\"add\"]}}",
				"{\"eccoRepository\":\"/e\",\"gitRepository\":\"/g\",\"commit\":\"c2\",\"before\":\"A\",\"suggestion\":\"B\",\"committed\":\"A, B\",\"outcome\":\"skipped\",\"positions\":{\"b.c\":[]}}",
				"{\"eccoRepository\":\"/other\",\"gitRepository\":\"/g\",\"commit\":\"c3\",\"before\":\"\",\"suggestion\":\"C\",\"committed\":\"C\",\"outcome\":\"auto\",\"positions\":{\"c.c\":[]}}",
				"not json") + "\n");

		ImportedFeatureTraces traces = ImportedFeatureTraces.fromLog(log, "/e", Path.of("/g"), new GitHistoryReader(), null);

		assertEquals(Map.of("A", Set.of("a.c#add")), traces.positions());
	}

	private static ImportedFeatureTraces.Changes changed(String path, List<String> positions, boolean added) {
		return new ImportedFeatureTraces.Changes(Map.of(path, positions), added ? Set.of(path) : Set.of(), Map.of());
	}

	/**
	 * A feature that keeps changing a class another feature created is suggested to refine it; one
	 * whose changes lie where everybody's do is not; the base, which created the code everything
	 * else changes, gets no parent.
	 */
	@Test
	public void aFeatureChangingMostlyCodeAnotherCreatedRefinesIt() {
		ImportedFeatureTraces traces = new ImportedFeatureTraces();
		traces.record("g", List.of("BASE"), changed("Zip.java", List.of("Zip.add", "Zip.extract"), true));
		traces.record("g", List.of("EXTRACT"), changed("Extract.java", List.of("Extract.run", "Extract.check"), true));
		for (int i = 0; i < 12; i++)
			traces.record("g", List.of("SLIP"), changed("Extract.java", List.of("Extract.check"), false));
		for (int i = 0; i < 60; i++)
			traces.record("g", List.of("SPREAD"), changed("Zip.java", List.of("Zip.add"), false));

		List<ImportedFeatureTraces.RelationSuggestion> suggestions = traces.relationSuggestions();

		assertEquals(1, suggestions.size(), suggestions.toString());
		assertEquals("SLIP", suggestions.get(0).feature());
		assertEquals("EXTRACT", suggestions.get(0).other());
		assertEquals(ImportedFeatureTraces.Relation.SUBFEATURE, suggestions.get(0).relation(), "SLIP has no code of its own");
		assertEquals(1.0, suggestions.get(0).share());
		assertEquals(6.0, suggestions.get(0).lift(), 1e-9, "12 of 72 votes go to EXTRACT; all of SLIP's");
	}

	/** A restructuring commit that moves a file creates nothing: the file keeps its owner. */
	@Test
	public void aRenamedFileKeepsItsOwner() {
		ImportedFeatureTraces traces = new ImportedFeatureTraces();
		traces.record("g", List.of("BASE"), changed("Zip.java", List.of("Zip.add"), true));
		traces.record("g", List.of("EXTRACT"), changed("Extract.java", List.of("Extract.run"), true));
		traces.record("g", List.of("MOVE"), new ImportedFeatureTraces.Changes(Map.of("tasks/Extract.java", List.of("Extract.run")), Set.of(), Map.of("tasks/Extract.java", "Extract.java")));
		for (int i = 0; i < 12; i++)
			traces.record("g", List.of("SLIP"), changed("tasks/Extract.java", List.of("Extract.run"), false));
		for (int i = 0; i < 60; i++)
			traces.record("g", List.of("SPREAD"), changed("Zip.java", List.of("Zip.add"), false));

		assertEquals(List.of("EXTRACT"), traces.relationSuggestions().stream().filter(p -> p.feature().equals("SLIP")).map(ImportedFeatureTraces.RelationSuggestion::other).toList());
	}

	/** The view reads only what the log recorded: positions, files added and renamed. */
	@Test
	public void fromLogOfAnEccoRepositoryReadsTheRecordedChanges(@TempDir Path tmp) throws Exception {
		StringBuilder log = new StringBuilder();
		log.append("{\"eccoRepository\":\"/e\",\"gitRepository\":\"/g\",\"commit\":\"c0\",\"before\":\"\",\"committed\":\"BASE\",\"outcome\":\"auto\",\"positions\":{\"Zip.java\":[\"Zip.add\"]},\"added\":[\"Zip.java\"],\"renamed\":{}}\n");
		log.append("{\"eccoRepository\":\"/e\",\"gitRepository\":\"/g\",\"commit\":\"c1\",\"before\":\"BASE\",\"committed\":\"BASE, EXTRACT\",\"outcome\":\"auto\",\"positions\":{\"Extract.java\":[\"Extract.run\"]},\"added\":[\"Extract.java\"],\"renamed\":{}}\n");
		for (int i = 0; i < 12; i++)
			log.append("{\"eccoRepository\":\"/e\",\"gitRepository\":\"/g\",\"commit\":\"s").append(i).append("\",\"before\":\"BASE, EXTRACT\",\"suggestion\":\"SLIP\",\"committed\":\"BASE, EXTRACT, SLIP\",\"outcome\":\"reviewed\",\"positions\":{\"Extract.java\":[\"Extract.check\"]}}\n");
		for (int i = 0; i < 60; i++)
			log.append("{\"eccoRepository\":\"/e\",\"gitRepository\":\"/g\",\"commit\":\"z").append(i).append("\",\"before\":\"BASE, EXTRACT, SLIP\",\"suggestion\":\"SPREAD\",\"committed\":\"BASE, EXTRACT, SLIP, SPREAD\",\"outcome\":\"reviewed\",\"positions\":{\"Zip.java\":[\"Zip.add\"]}}\n");
		log.append("{\"eccoRepository\":\"/other\",\"gitRepository\":\"/g\",\"commit\":\"o\",\"before\":\"\",\"committed\":\"X\",\"outcome\":\"auto\",\"positions\":{\"x\":[]},\"added\":[\"x\"]}\n");
		Path file = Files.writeString(tmp.resolve("log.jsonl"), log);

		assertEquals(List.of(new ImportedFeatureTraces.RelationSuggestion("SLIP", "EXTRACT", ImportedFeatureTraces.Relation.SUBFEATURE, 1.0, 6.0, 0)),
				ImportedFeatureTraces.fromLog(file, "/e").relationSuggestions());
	}

	/**
	 * A feature with source code of its own elsewhere that also changes another feature's code
	 * requires it (cross-tree); one with no code of its own is a subfeature (tree). Source files it
	 * created in the other's directories are the other's.
	 */
	@Test
	public void ownSourceCodeElsewhereMakesItARequires() {
		ImportedFeatureTraces traces = new ImportedFeatureTraces();
		traces.record("g", List.of("BASE"), changed("core/Zip.java", List.of("Zip.add", "Zip.extract"), true));
		traces.record("g", List.of("STORE"), changed("store/Store.java", List.of("Store.write", "Store.read"), true));
		// AES has a package of its own and changes STORE's code
		traces.record("g", List.of("AES"), changed("aes/Aes.java", List.of("Aes.encrypt"), true));
		// STORED adds a class in STORE's package: still inside STORE
		traces.record("g", List.of("STORED"), changed("store/Stored.java", List.of("Stored.copy"), true));
		for (int i = 0; i < 12; i++) {
			traces.record("g", List.of("AES"), changed("store/Store.java", List.of("Store.write"), false));
			traces.record("g", List.of("STORED"), changed("store/Store.java", List.of("Store.read"), false));
		}
		for (int i = 0; i < 120; i++)
			traces.record("g", List.of("SPREAD"), changed("core/Zip.java", List.of("Zip.add"), false));

		java.util.Map<String, ImportedFeatureTraces.RelationSuggestion> byFeature = new java.util.HashMap<>();
		traces.relationSuggestions().forEach(r -> byFeature.put(r.feature(), r));

		assertEquals(ImportedFeatureTraces.Relation.REQUIRES, byFeature.get("AES").relation());
		assertEquals("STORE", byFeature.get("AES").other());
		assertEquals(1, byFeature.get("AES").ownSources());
		assertEquals(ImportedFeatureTraces.Relation.SUBFEATURE, byFeature.get("STORED").relation());
		assertEquals(0, byFeature.get("STORED").ownSources());
	}

	/** A configuration with the feature but without the other contradicts both relations. */
	@Test
	public void configurationsWithTheFeatureButNotTheOtherRuleItOut() {
		List<ImportedFeatureTraces.RelationSuggestion> suggestions = List.of(
				new ImportedFeatureTraces.RelationSuggestion("SLIP", "EXTRACT", ImportedFeatureTraces.Relation.SUBFEATURE, 0.5, 20, 0),
				new ImportedFeatureTraces.RelationSuggestion("AES", "STORE", ImportedFeatureTraces.Relation.REQUIRES, 0.3, 6, 1));

		assertEquals(List.of(suggestions.get(0)), ImportedFeatureTraces.consistentWith(suggestions,
				List.of(Set.of("BASE", "EXTRACT", "SLIP"), Set.of("BASE", "AES"), Set.of("BASE", "STORE", "AES"))));
	}
}
