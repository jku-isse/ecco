package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.golang.data.ContextArtifactData;
import at.jku.isse.ecco.adapter.golang.data.TokenArtifactData;
import at.jku.isse.ecco.adapter.golang.io.SourceWriter;
import at.jku.isse.ecco.adapter.golang.merging.TokenMerger;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GoWriter implements ArtifactWriter<Set<Node>, Path> {
    private final SourceWriter sourceWriter;
    private final List<WriteListener> listeners = new LinkedList<>();

    @Inject
    public GoWriter(SourceWriter sourceWriter) {
        this.sourceWriter = sourceWriter;
    }

    @Override
    public String getPluginId() {
        return new GoPlugin().getPluginId();
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        List<Path> writtenFiles = new LinkedList<>();

        for (Node node : input) {
            Path outputPath = getPathFromPluginArtifact(base, node);
            if (outputPath == null) {
                // If root node is not a plugin node the tree is faulty, so ignore it
                continue;
            }

            List<TokenArtifactData> tokenList = flattenAndMerge(node);

            writeTokensToFile(outputPath, tokenList);
            writtenFiles.add(outputPath);
        }

        return writtenFiles.toArray(new Path[0]);
    }

    private Path getPathFromPluginArtifact(Path base, Node node) {
        ArtifactData data = node.getArtifact().getData();

        if (!(data instanceof PluginArtifactData)) {
            return null;
        }

        PluginArtifactData pluginArtifactData = (PluginArtifactData) data;
        return base.resolve(pluginArtifactData.getPath());
    }

    private List<TokenArtifactData> flattenAndMerge(Node node) {
        List<? extends Node> childNodes = node.getChildren();
        List<TokenArtifactData> tokenList = flattenNodeTree(childNodes);

        tokenList = new TokenMerger().merge(tokenList);
        return tokenList;
    }

    private void writeTokensToFile(Path outputPath, List<TokenArtifactData> tokenList) {
        String reconstructedFile = tokenList.stream()
                .map(TokenArtifactData::getToken)
                .collect(Collectors.joining());

        try {
            sourceWriter.writeString(outputPath, reconstructedFile, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new EccoException(e);
        }

        listeners.forEach(listener -> listener.fileWriteEvent(outputPath, this));
    }

    private List<TokenArtifactData> flattenNodeTree(List<? extends Node> childNodes) {
        List<TokenArtifactData> tokenList = new LinkedList<>();

        for (Node childNode : childNodes) {
            ArtifactData childNodeData = childNode.getArtifact().getData();

            if (childNodeData instanceof ContextArtifactData) {
                tokenList.addAll(flattenNodeTree(childNode.getChildren()));
            } else if (childNodeData instanceof TokenArtifactData) {
                tokenList.add((TokenArtifactData) childNodeData);
                // A TokenArtifactInstance is only created for terminal nodes,
                // which are not expected to have any children
            }
        }

        return tokenList;
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
     * Adds a {@link WriteListener} that
     * is notified everytime a file has been written.
     *
     * @param listener Instance of WriteListener to be notified
     * @see WriteListener
     */
    @Override
    public void addListener(WriteListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a {@link WriteListener} that
     * is notified everytime a file has been written.
     *
     * @param listener Instance of WriteListener to remove
     * @see #addListener(WriteListener)
     * @see WriteListener
     */
    @Override
    public void removeListener(WriteListener listener) {
        listeners.remove(listener);
    }
}
