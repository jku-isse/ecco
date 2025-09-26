package at.jku.isse.ecco.adapter.rust;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.rust.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RustWriter implements ArtifactWriter<Set<Node>, Path> {

    private Collection<WriteListener> listeners = new ArrayList<>();

    /**
     * @return name of the plugin this writer belongs to
     */
    @Override
    public String getPluginId() { return RustPlugin.class.getName(); }

    /**
     * @param base base path where to write the artifacts
     * @param input artifacts to write
     * @return paths to the written artifacts
     */
    @Override
    public Path[] write(Path base, Set<Node> input) {
        List<Path> output = new ArrayList<>();

        for (Node fileNode : input) {
            Artifact<?> fileArtifact = fileNode.getArtifact();
            ArtifactData artifactData = fileArtifact.getData();
            if (!(artifactData instanceof PluginArtifactData)) {
                throw new EccoException("Expected plugin artifact data.");
            }
            PluginArtifactData pluginArtifactData = (PluginArtifactData) artifactData;
            Path outputPath = base.resolve(pluginArtifactData.getPath());
            output.add(outputPath);

            this.writeRustFile(outputPath, fileNode);
        }

        return output.toArray(new Path[0]);
    }

    /**
     * Writes a Rust file based on the provided ordered node.
     *
     * @param filePath    Path where the Rust file will be written.
     * @param orderedNode Node representing the structure of the Rust file.
     */
    private void writeRustFile(Path filePath, Node orderedNode){
        try (BufferedWriter bw = Files.newBufferedWriter(filePath)) {
            List<? extends Node> fileNodeChildren = orderedNode.getChildren();
            if (fileNodeChildren == null || fileNodeChildren.isEmpty()) {
                throw new EccoException("File node has no children.");
            }
            for (Node childNode : fileNodeChildren) {
                visitingNode(bw, childNode);
            }
        } catch (IOException e) {
            throw new EccoException("Could not write Rust file.", e);
        }
    }

    /**
     * Recursively visits nodes and writes their data if they implement RustWritable.
     *
     * @param bw        BufferedWriter to write to.
     * @param childNode Current node being visited.
     * @throws IOException If an I/O error occurs from the BufferedWriter.
     */
    public void visitingNode(BufferedWriter bw, Node childNode) throws IOException {
        ArtifactData childArtifactData = childNode.getArtifact().getData();
        if (childArtifactData instanceof VisibilityArtifactData) {
            // Special handling for VisibilityArtifactData
            bw.write("pub ");
        }

        // childArtifactData has something to write
        if (childArtifactData instanceof RustWritable) {
            ((RustWritable) childArtifactData).write(bw);
        }

        if (!childNode.getChildren().isEmpty()) {
            for (Node node : childNode.getChildren()) {
                visitingNode(bw, node);
            }
        }
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
