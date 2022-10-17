package at.jku.isse.ecco.adapter.python;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PythonReader implements ArtifactReader<Path, Set<Node.Op>> {

    @Override
    public String getPluginId() {
        return PythonPlugin.class.getName();
    }

    private static final Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(1, new String[]{"**.py"});
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        System.out.println("Reader should start reading");
        return null;
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        System.out.println("Reader should start reading");
        return null;
    }

    @Override
    public void addListener(ReadListener listener) {
        System.out.println("addListener");
    }

    @Override
    public void removeListener(ReadListener listener) {
        System.out.println("removeListener");
    }
}
