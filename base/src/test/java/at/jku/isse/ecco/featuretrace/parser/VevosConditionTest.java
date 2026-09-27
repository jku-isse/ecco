package at.jku.isse.ecco.featuretrace.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VevosCondition's constructor reads up through lineParts[6] (endLine) but used to only validate
 * lineParts.length < 6 -- a line with exactly 6 fields (missing endLine, or missing the "Line Type"
 * field read at lineParts[4], which the class's own inline format comment never used to mention)
 * passed the guard and then threw an unchecked ArrayIndexOutOfBoundsException instead of the
 * intended, diagnostic IllegalArgumentException. Present since the class's first commit, with no
 * prior test coverage anywhere in the repo.
 */
public class VevosConditionTest {

	@Test
	public void parsesAllSevenFields() {
		VevosCondition condition = new VevosCondition("src/Main.c;True;True;A&&B;code;5;10");

		assertEquals(Path.of("src/Main.c"), condition.getFilePath());
		assertEquals(5, condition.getStartLine());
		assertEquals(10, condition.getEndLine());
		assertEquals("A&B", condition.getConditionString());
	}

	@Test
	public void aLineMissingTheEndLineFieldThrowsAnInformativeExceptionInsteadOfArrayIndexOutOfBounds() {
		String sixFieldLine = "src/Main.c;True;True;A&&B;code;5"; // missing endLine
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new VevosCondition(sixFieldLine));
		assertEquals("VEVOS file entry has less than 7 comma-separated parts: " + sixFieldLine, exception.getMessage());
	}

	/**
	 * True/False were replaced as plain substrings, so a feature name containing them was mangled
	 * (IsTrueColor -> Is$trueColor); only the constants themselves may be replaced.
	 */
	@Test
	public void onlyTheBooleanConstantsAreReplacedNotPartsOfFeatureNames() {
		VevosCondition condition = new VevosCondition("src/Main.c;True;True;IsTrueColor && !FalseStart || True;code;1;2");
		assertEquals("IsTrueColor & ~FalseStart | $true", condition.getConditionString());
	}

	/** An empty VEVOS file failed on removing its (missing) header line. */
	@Test
	public void anEmptyVevosFileHasNoConditions() throws Exception {
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("vevos-empty");
		java.nio.file.Files.writeString(dir.resolve("pcs.variant.csv"), "");
		VevosConditionHandler handler = new VevosConditionHandler(dir);
		assertEquals(0, handler.getFileConditionsMap().size());
	}

	/** A file without conditions yielded a container that NPE'd on the first lookup. */
	@Test
	public void aFileWithoutConditionsMatchesNothing() {
		assertEquals(0, new VevosFileConditionContainer(null).getMatchingPresenceConditions(1, 10).size());
	}
}
