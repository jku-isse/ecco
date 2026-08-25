package at.jku.isse.ecco.adapter.python;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PythonWriter implements ArtifactWriter<Set<Node>, Path> {

    @Override
    public String getPluginId() {
        return PythonPlugin.class.getName();
    }

    private static final Logger LOGGER = Logger.getLogger(PythonPlugin.class.getName());

    @Override
    public Path[] write(Set<Node> input) {
        return this.write(Paths.get("."), input);
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        PythonParser.Writer parser = PythonParserFactory.getWriteParser();

        try {
            if (parser == null) {
                throw new IOException("no parser found");
            }
            parser.init();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "could not initialize parser", e);
            throw new RuntimeException("could not initialize parser", e);
        }

        List<Path> output = new ArrayList<>();

        for (Node root : input) {
            PluginArtifactData pluginArtifact = (PluginArtifactData) root.getArtifact().getData();
            Path outputPath = base.resolve(pluginArtifact.getPath());
            // HINT: this resolve might not be necessary as the artifact stores the relative path anyway.
            output.add(outputPath);
            if (root.getChildren().size() == 1) {
                Node moduleNode = root.getChildren().get(0);
                parser.parse(outputPath, moduleNode);
            } else {
                LOGGER.log(Level.SEVERE, "attempting to create file, wrong number of module nodes (found {0} expecting 1)", root.getChildren().size());
            }
        }
        parser.shutdown();
        return output.toArray(new Path[0]);
    }

    private final Collection<WriteListener> listeners = new ArrayList<>();

    @Override
    public void addListener(WriteListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        this.listeners.remove(listener);
    }
}
