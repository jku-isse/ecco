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
}
