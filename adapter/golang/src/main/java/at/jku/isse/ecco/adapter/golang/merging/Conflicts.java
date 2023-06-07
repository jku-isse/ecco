package at.jku.isse.ecco.adapter.golang.merging;

import at.jku.isse.ecco.adapter.golang.data.TokenArtifactData;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Conflicts {
    private final List<TokenArtifactData> conflicts = new LinkedList<>();
    private final List<TokenArtifactData> duplicates = new LinkedList<>();

    public void add(TokenArtifactData original, TokenArtifactData conflict) {
        this.conflicts.add(original);
        this.conflicts.add(conflict);
        this.duplicates.add(conflict);
    }

    public List<TokenArtifactData> getConflicts() {
        return new ArrayList<>(this.conflicts);
    }

    public List<TokenArtifactData> getDuplicates() {
        return new ArrayList<>(this.duplicates);
    }

    public boolean hasConflicts() {
        return !this.duplicates.isEmpty();
    }
}
