package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.cpp.data.*;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CppWriter implements ArtifactWriter<Set<Node>, Path> {

    @Override
    public String getPluginId() {
        return CppPlugin.class.getName();
    }

    //public String dir = "";

    @Override
    public Path[] write(Set<Node> input) {
        return this.write(Paths.get("."), input);
    }

    String[] code = {""};
    String[] includes = {""};
    String[] fields = {""};
    String[] defines = {""};

    @Override
    public Path[] write(Path base, Set<Node> input) {
        Path[] toreturn = input.parallelStream().map(node -> {
            try {
                includes[0] = "";
                //dir = "C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\Method_comparison\\results\\"+ base.getFileName().toString()+".txt";
                return processNode(node, base);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }).filter(Objects::nonNull).toArray(Path[]::new);
        if (toreturn.length != input.size())
            throw new IllegalStateException("Not all files could be written!");
        return toreturn;
    }

    /**
     * @param baseNode The base node which should be processed
     * @param basePath The base path (need to parse package hierarchy
     * @return The path were the file got placed
     */
    private Path processNode(Node baseNode, Path basePath) throws IOException {
        if (!(baseNode.getArtifact().getData() instanceof PluginArtifactData)) return null;
        PluginArtifactData rootData = (PluginArtifactData) baseNode.getArtifact().getData();
        final List<? extends Node> children = baseNode.getChildren();
        if (children.size() < 1)
            return null;

        Path returnPath = basePath.resolve(rootData.getPath());

        if (baseNode.getChildren().size() > 0) {
            for (Node node : baseNode.getChildren()) {
                visitingNodes(node);
            }
        }
        code[0] = includes[0] + "\n" + defines[0] + "\n" + fields[0] + "\n" + code[0];
        try (BufferedWriter writer = Files.newBufferedWriter(returnPath, StandardCharsets.UTF_8)) {
            writer.write(code[0]);
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }

        code = new String[]{""};
        includes = new String[]{""};
        fields = new String[]{""};
        defines = new String[]{""};

        return returnPath;
    }


    public void visitingNodes(Node childNode) {
        if (childNode.getArtifact().toString().equals("INCLUDES") || childNode.getArtifact().toString().equals("FUNCTIONS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
        } else if (childNode.getArtifact().toString().equals("FIELDS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
					fields[0] += node.getArtifact().getData() + "\n";
					if (node.getChildren().size() > 0) {
						for (Node childfield : node.getChildren()) {
							fields[0] += childfield.getArtifact().getData() + "\n";
						}
					}
                }
            }
        } else if (childNode.getArtifact().toString().equals("DEFINES")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    defines[0] += node.getArtifact().getData() + "\n";
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof IncludeArtifactData)) {
            final IncludeArtifactData artifactData = (IncludeArtifactData) childNode.getArtifact().getData();
            includes[0] += artifactData.toString() + "\n";
        } else if ((childNode.getArtifact().getData() instanceof LineArtifactData)) {
            code[0] += ((LineArtifactData) childNode.getArtifact().getData()).getLine() + "\n";
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof FunctionArtifactData)) {
            code[0] += "\n" + ((FunctionArtifactData) childNode.getArtifact().getData()).getSignature() + "\n";//artifactData.toString() + "{\n";
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof LineArtifactData)) {
            code[0] += "\n" + childNode.getArtifact().getData() + "\n";
        }
    }

    private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

    @Override
    public void addListener(WriteListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        this.listeners.remove(listener);
    }

}
