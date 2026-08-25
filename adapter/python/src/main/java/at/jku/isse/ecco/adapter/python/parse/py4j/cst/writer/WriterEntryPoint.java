package at.jku.isse.ecco.adapter.python.parse.py4j.cst.writer;

import at.jku.isse.ecco.adapter.python.PythonPlugin;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.logging.Logger;

public class WriterEntryPoint {
    private Node root;
    private Path path;

    public static final Logger LOGGER = Logger.getLogger(PythonPlugin.class.getName());

    public void reset(Path path, Node root) {
        this.root = root;
        this.path = path;
    }

    public WriterNode getRoot() {
        return new WriterNode(root);
    }

    public Logger getLogger() {return LOGGER;} // since direct python-logging via py4j is not working
}