package at.jku.isse.ecco.adapter.golang.merging;

import at.jku.isse.ecco.adapter.golang.data.TokenArtifactData;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TokenMergerTest {
    @Test
    public void findsAllConflicts() {
        TokenArtifactData a = new TokenArtifactData("a", 1, 1);
        TokenArtifactData b = new TokenArtifactData("b", 1, 1);
        TokenArtifactData c = new TokenArtifactData("c", 1, 2);
        TokenArtifactData d = new TokenArtifactData("d", 1, 3);
        List<TokenArtifactData> list = new LinkedList<>();

        list.add(a);
        list.add(b);
        list.add(c);
        list.add(d);

        Conflicts conflicts = new TokenMerger().findConflicts(list);

        assertEquals(2, conflicts.getConflicts().size());
        assertEquals(1, conflicts.getDuplicates().size());
        assertEquals(a, conflicts.getConflicts().get(0));
        assertEquals(b, conflicts.getConflicts().get(1));
        assertEquals(b, conflicts.getDuplicates().get(0));
    }

    @Test
    public void sortsByRowAndColumn() {
        TokenArtifactData a = new TokenArtifactData("a", 2, 1);
        TokenArtifactData b = new TokenArtifactData("b", 3, 2);
        TokenArtifactData c = new TokenArtifactData("c", 3, 1);
        TokenArtifactData d = new TokenArtifactData("d", 1, 1);
        List<TokenArtifactData> list = new LinkedList<>();

        list.add(a);
        list.add(b);
        list.add(c);
        list.add(d);

        List<TokenArtifactData> result = new TokenMerger().sort(list);

        assertEquals(4, result.size());
        assertEquals("d", result.get(0).getToken());
        assertEquals("a", result.get(1).getToken());
        assertEquals("c", result.get(2).getToken());
        assertEquals("b", result.get(3).getToken());
    }

    @Test
    public void mergesByDuplicatingConflictingLines() {
        TokenArtifactData a = new TokenArtifactData("a", 1, 1);
        TokenArtifactData b = new TokenArtifactData("b", 1, 1);
        TokenArtifactData c = new TokenArtifactData("c", 1, 2);
        TokenArtifactData d = new TokenArtifactData("d", 2, 3);
        List<TokenArtifactData> list = new LinkedList<>();

        list.add(a);
        list.add(b);
        list.add(c);
        list.add(d);

        List<TokenArtifactData> resultList = new TokenMerger().merge(list);

        assertEquals(new TokenArtifactData("a", 1, 1), resultList.get(0));
        assertEquals(new TokenArtifactData("c", 1, 2), resultList.get(1));
        assertEquals(new TokenArtifactData("b", 2, 1), resultList.get(2));
        assertEquals(new TokenArtifactData("c", 2, 2), resultList.get(3));
        assertEquals(new TokenArtifactData("d", 3, 3), resultList.get(4));
    }
}
