package at.jku.isse.ecco.adapter.python.parse.py4j.cst.writer;

import at.jku.isse.ecco.adapter.python.PythonPlugin;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class WriterEntryPoint {
    private Node root;
    private Path path;

    public static final Logger LOGGER = Logger.getLogger(PythonPlugin.class.getName());

    // render mode (python_cst_writer.py --render): the script reports the code and where each node's text is
    private final Map<Node, Integer> ids = new IdentityHashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private String renderedCode;
    private String renderedSpans;

    public void reset(Path path, Node root) {
        this.root = root;
        this.path = path;
        this.ids.clear();
        this.nodes.clear();
        this.renderedCode = null;
        this.renderedSpans = null;
    }

    /** The id the script uses for {@code node} - an index into {@link #getNode}. */
    int idOf(Node node) {
        return this.ids.computeIfAbsent(node, n -> {
            this.nodes.add(n);
            return this.nodes.size() - 1;
        });
    }

    public Node getNode(int id) {
        return this.nodes.get(id);
    }

    public void setRenderedCode(String code) {
        this.renderedCode = code;
    }

    /** "id,startLine,startColumn,endLine,endColumn" per node, separated by ";" - lines from 1, columns from 0. */
    public void setRenderedSpans(String spans) {
        this.renderedSpans = spans;
    }

    public String getRenderedCode() {
        return this.renderedCode;
    }

    public String getRenderedSpans() {
        return this.renderedSpans;
    }

    public WriterNode getRoot() {
        return new WriterNode(root, this);
    }

    public Logger getLogger() {return LOGGER;} // since direct python-logging via py4j is not working
}