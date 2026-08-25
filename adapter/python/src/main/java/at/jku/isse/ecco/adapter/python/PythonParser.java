package at.jku.isse.ecco.adapter.python;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.Path;

public interface PythonParser {

    void init() throws IOException;
    void shutdown();

    interface Reader extends PythonParser {
        Node.Op parse(Path path, EntityFactory entityFactory);
    }

    interface Writer extends PythonParser {
        void parse(Path path, Node root);
    }
 }

