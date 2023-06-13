package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.designspace.exception.MultipleWorkspaceException;
import at.jku.isse.ecco.adapter.designspace.exception.NoWorkspaceException;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;

import java.util.Map;
import java.util.Set;

public class DesignSpaceReader implements ArtifactReader<Workspace, Set<Node.Op>> {

    @Override
    public String getPluginId() {
        return new DesignSpacePlugin().getPluginId();
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        // Since this reader does not interact with files, there are no patterns to look for
        return Map.of();
    }

    @Override
    public Set<Node.Op> read(Workspace base, Workspace[] input) {
        if (input != null && input.length > 0) {
            throw new MultipleWorkspaceException();
        }

        if (base == null) {
            throw new NoWorkspaceException();
        }

        return null;
    }

    @Override
    public Set<Node.Op> read(Workspace[] input) {
        if (input != null && input.length > 1) {
            throw new MultipleWorkspaceException();
        }

        if (input == null || input.length == 0) {
            throw new NoWorkspaceException();
        }

        return read(input[0], null);
    }

    @Override
    public void addListener(ReadListener listener) {

    }

    @Override
    public void removeListener(ReadListener listener) {

    }
}
