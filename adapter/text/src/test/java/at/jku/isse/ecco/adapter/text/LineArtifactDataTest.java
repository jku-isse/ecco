package at.jku.isse.ecco.adapter.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Lines are artifacts: two lines are the same artifact exactly when their text is the same, whitespace included. */
public class LineArtifactDataTest {

    private static void assertDistinct(String a, String b) {
        assertNotEquals(new LineArtifactData(a), new LineArtifactData(b), "'" + a + "' vs '" + b + "'");
    }

    @Test
    public void sameTextIsEqualWithTheSameHash() {
        LineArtifactData one = new LineArtifactData("int x = 1;");
        LineArtifactData other = new LineArtifactData("int x = 1;");
        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
        assertEquals(one, one);
    }

    @Test
    public void whitespaceDifferencesAreDifferentLines() {
        assertDistinct("a", "a ");
        assertDistinct("a", " a");
        assertDistinct("\ta", "    a");
        assertDistinct("a\t", "a ");
        assertDistinct("", " ");
    }

    @Test
    public void lineEndCharactersInsideALineCount() {
        // a reader never leaves them in a line, but a stray CR must not be dropped by equality
        assertDistinct("a", "a\r");
        assertDistinct("a\r", "a\n");
    }

    @Test
    public void caseAndUnicodeNormalizationCount() {
        assertDistinct("A", "a");
        assertDistinct("é", "é");
        assertDistinct("a", "﻿a");
    }

    @Test
    public void nullLines() {
        assertEquals(new LineArtifactData(null), new LineArtifactData(null));
        assertEquals(new LineArtifactData(null).hashCode(), new LineArtifactData(null).hashCode());
        assertNotEquals(new LineArtifactData(null), new LineArtifactData(""));
        assertNotEquals(new LineArtifactData(""), new LineArtifactData(null));
    }

    @Test
    public void nullAndOtherClassesAreNotEqual() {
        assertFalse(new LineArtifactData("a").equals(null));
        assertNotEquals("a", new LineArtifactData("a"));
        assertNotEquals(new LineArtifactData("a") {
        }, new LineArtifactData("a"), "a subclass is another class");
    }

    @Test
    public void toStringIsTheLine() {
        assertEquals("  x\t", new LineArtifactData("  x\t").toString());
        assertEquals("  x\t", new LineArtifactData("  x\t").getLine());
    }
}
