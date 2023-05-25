package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.Set;

public class GoWriter implements ArtifactWriter<Set<Node>, Path> {
    @Override
    public String getPluginId() {
        return new GoPlugin().getPluginId();
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        return new Path[0];
    }

    /**
     * @param input Set of input nodes to write
     * @return Files that were created
     * @see #write(Path, Set)
     */
    @Override
    public Path[] write(Set<Node> input) {
        return this.write(Path.of("."), input);
    }

    /**
     * Adds a <a href="#{@link}">{@link WriteListener}</a> that
     * is notified everytime a file has been written.
     *
     * @param listener Instance of WriteListener to be notified
     * @see WriteListener
     */
    @Override
    public void addListener(WriteListener listener) {

    }

    /**
     * Removes a <a href="#{@link}">{@link WriteListener}</a> that
     * is notified everytime a file has been written.
     *
     * @param listener Instance of WriteListener to remove
     * @see #addListener(WriteListener)
     * @see WriteListener
     */
    @Override
    public void removeListener(WriteListener listener) {

    }
}
