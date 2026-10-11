package at.jku.isse.ecco.adapter.lilypond;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

public interface LilypondParser<T> {
    void init() throws IOException;

    LilypondNode<T> parse(Path path);

    LilypondNode<T> parse(Path path, HashMap<String, Integer> tokenMetric);

    void shutdown();

    /** Whether the parser makes musical tokens (see LilyEccoTransformer#transform); off unless set. */
    default void setMusicalTokens(boolean musicalTokens) {}

    /** Each top-level assignment a context named after its variable; parsers without them ignore it. */
    default void setVariableContexts(boolean variableContexts) {}

    /** Read with lymodel's tree-sitter parse instead of parce; before {@link #init()}. */
    default void setLybar(boolean lybar) {}
}
