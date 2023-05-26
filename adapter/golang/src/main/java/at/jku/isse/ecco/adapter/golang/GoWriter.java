package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.golang.data.ContextArtifactData;
import at.jku.isse.ecco.adapter.golang.data.TokenArtifactData;
import at.jku.isse.ecco.adapter.golang.io.SourceWriter;
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
            ArtifactData data = node.getArtifact().getData();

            if (!(data instanceof PluginArtifactData)) {
                continue;
            }

            PluginArtifactData pluginArtifactData = (PluginArtifactData) data;
            Path outputPath = base.resolve(pluginArtifactData.getPath());
            List<? extends Node> childNodes = node.getChildren();
            List<TokenArtifactData> tokenList = new LinkedList<>();

            flattenNodeTree(childNodes, tokenList);


            sortTokenList(tokenList);

            for (int rowNumber = 1; rowNumber < tokenList.get(tokenList.size() - 1).getRow(); rowNumber++) {
                final int rowNr = rowNumber;
                List<TokenArtifactData> row = tokenList.stream().filter(token -> token.getRow() == rowNr).collect(Collectors.toList());
                List<TokenArtifactData> duplicates = new LinkedList<>();
                List<TokenArtifactData> conflicts = new LinkedList<>();

                for (int i = 0; i < row.size(); i++) {
                    TokenArtifactData a = row.get(i);

                    for (int j = i + 1; j < row.size(); j++) {
                        TokenArtifactData b = row.get(j);

                        if (a.getColumn() == b.getColumn()) {
                            duplicates.add(b);
                            conflicts.add(a);
                            conflicts.add(b);
                            row.remove(b);
                            tokenList.remove(b);
                        }
                    }
                }

                if (!duplicates.isEmpty()) {
                    row.stream().filter(token -> !conflicts.contains(token))
                            .forEach(token -> duplicates.add(new TokenArtifactData(token.getToken(), token.getRow(), token.getColumn())));

                    tokenList = tokenList.stream()
                            .map(token -> {
                                if (token.getRow() > rowNr) {
                                    return new TokenArtifactData(token.getToken(), token.getRow() + 2, token.getColumn());
                                }
                                return token;
                            })
                            .collect(Collectors.toList());

                    for (TokenArtifactData token : duplicates) {
                        tokenList.add(new TokenArtifactData(token.getToken(), token.getRow() + 1, token.getColumn()));
                    }

                    sortTokenList(tokenList);
                }
            }

            String reconstructedFile = tokenList.stream()
                    .map(TokenArtifactData::getToken)
                    .collect(Collectors.joining());

            try {
                sourceWriter.writeString(outputPath, reconstructedFile, StandardOpenOption.CREATE_NEW);
            } catch (IOException e) {
                throw new EccoException(e);
            }

            writtenFiles.add(outputPath);
            listeners.forEach(listener -> listener.fileWriteEvent(outputPath, this));
        }

        return writtenFiles.toArray(new Path[0]);
    }

    private void sortTokenList(List<TokenArtifactData> tokenList) {
        tokenList.sort((a, b) -> {
            if (a.getRow() != b.getRow()) {
                // First sort by line number
                return a.getRow() - b.getRow();
            }
            // Then by column
            return a.getColumn() - b.getColumn();
        });
    }

    private void flattenNodeTree(List<? extends Node> childNodes, List<TokenArtifactData> tokenList) {
        for (Node childNode : childNodes) {
            ArtifactData childNodeData = childNode.getArtifact().getData();

            if (childNodeData instanceof ContextArtifactData) {
                flattenNodeTree(childNode.getChildren(), tokenList);
            } else if (childNodeData instanceof TokenArtifactData) {
                tokenList.add((TokenArtifactData) childNodeData);
                // A TokenArtifactInstance is only created for terminal nodes,
                // which are not expected to have any children
            }
        }
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
        listeners.add(listener);
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
        listeners.remove(listener);
    }
}
