package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class GoReader implements ArtifactReader<Path, Set<Node.Op>> {
    @Override
    public String getPluginId() {
        return null;
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return null;
    }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        return null;
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return null;
    }

    @Override
    public void addListener(ReadListener listener) {

    }

    @Override
    public void removeListener(ReadListener listener) {

    }
}
