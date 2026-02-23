package at.jku.isse.ecco.adapter.lilypond;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

public interface LilypondParser<T> {
    void init() throws IOException;

    LilypondNode<T> parse(Path path);

    LilypondNode<T> parse(Path path, HashMap<String, Integer> tokenMetric);

    void shutdown();
}
