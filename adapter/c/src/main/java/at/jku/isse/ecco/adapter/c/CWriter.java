package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class CWriter implements ArtifactWriter<Set<Node>, Path> {

    private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

    @Override
    public String getPluginId() {
        return CPlugin.class.getName();
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        // TODO
        return new Path[0];
    }

    @Override
    public Path[] write(Set<Node> input) {
        return new Path[0];
    }

    @Override
    public void addListener(WriteListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        this.listeners.remove(listener);
    }
}
