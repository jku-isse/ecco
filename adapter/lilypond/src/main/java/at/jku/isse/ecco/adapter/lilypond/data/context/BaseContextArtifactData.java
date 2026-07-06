package at.jku.isse.ecco.adapter.lilypond.data.context;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/**
 * Represents a base class for a Lilypond context (lexicon in python <a href="https://parce.info">parce</a> parser).
 */
public abstract class BaseContextArtifactData implements ArtifactData {
    private final String context;

    public BaseContextArtifactData(String context)
    {
        this.context = context;
    }

    /**
     * Returns the name of the context (Lilypond parce lexicon path).
     * @return Text of context
     */
    public String getContext() {
        return context;
    }

    @Override
    public String toString() {
        return "Context '" + context + "'";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseContextArtifactData that = (BaseContextArtifactData) o;
        return context.equals(that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context);
    }
}
