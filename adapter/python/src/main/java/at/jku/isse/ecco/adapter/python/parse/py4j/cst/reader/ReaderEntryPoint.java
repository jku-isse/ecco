package at.jku.isse.ecco.adapter.python.parse.py4j.cst.reader;

import at.jku.isse.ecco.adapter.python.PythonPlugin;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class ReaderEntryPoint {

    public static final Logger LOGGER = Logger.getLogger(PythonPlugin.class.getName());

    protected Node.Op root;
    protected int nNodes = 0;

    public void reset(Path path, EntityFactory entityFactory) {
        root = null;
        nNodes = 0;
        ReaderNode.reset(entityFactory, this);
    }

    public ReaderNode getStartingNode() {
        return new ReaderNode(null);
    }

    public Node.Op getRoot() {
        return root;
    }

    public int getNodesCount() {
        return nNodes;
    }

    public Logger getLogger() {return LOGGER;} // since direct python-logging via py4j is not working
}