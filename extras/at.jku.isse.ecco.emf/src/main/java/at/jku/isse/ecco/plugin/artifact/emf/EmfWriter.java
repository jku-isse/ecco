package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.listener.WriteListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.Set;

/**
 * Created by hhoyos on 19/05/2017.
 */
public class EmfWriter implements ArtifactWriter<Set<Node>, Path> {

    @Override
    public String getPluginId() {
        return EmfWriter.class.getName();
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        return new Path[0];
    }

    @Override
    public Path[] write(Set<Node> input) {
        return new Path[0];
    }

    @Override
    public void addListener(WriteListener listener) {

    }

    @Override
    public void removeListener(WriteListener listener) {

    }
}
