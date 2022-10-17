package at.jku.isse.ecco.adapter.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

public interface PythonParser<T> {

    void init() throws IOException;
//
//    PythonNode<T> parse(Path path);
//
//    PythonNode<T> parse(Path path, HashMap<String, Integer> tokenMetric);

    void shutdown();

}
