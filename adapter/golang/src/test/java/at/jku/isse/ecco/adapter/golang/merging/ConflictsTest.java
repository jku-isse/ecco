package at.jku.isse.ecco.adapter.golang.merging;

import at.jku.isse.ecco.adapter.golang.data.TokenArtifactData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConflictsTest {
    @Test
    public void addsConflictOriginalOnlyToConflictList() {
        TokenArtifactData original = new TokenArtifactData("original", 1, 1);
        TokenArtifactData conflict = new TokenArtifactData("conflict", 1, 1);
        Conflicts conflicts = new Conflicts();

        conflicts.add(original, conflict);

        assertEquals(2, conflicts.getConflicts().size());
        assertTrue(conflicts.getConflicts().contains(original));
        assertTrue(conflicts.getConflicts().contains(conflict));
        assertEquals(1, conflicts.getDuplicates().size());
        assertTrue(conflicts.getDuplicates().contains(conflict));
    }

    @Test
    public void hasConflictsIsSetCorrectly() {
        TokenArtifactData original = new TokenArtifactData("original", 1, 1);
        TokenArtifactData conflict = new TokenArtifactData("conflict", 1, 1);
        Conflicts conflicts = new Conflicts();

        assertFalse(conflicts.hasConflicts());

        conflicts.add(original, conflict);

        assertTrue(conflicts.hasConflicts());
    }
}
