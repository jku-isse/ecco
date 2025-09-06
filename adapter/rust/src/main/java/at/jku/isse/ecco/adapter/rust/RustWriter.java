package at.jku.isse.ecco.adapter.rust;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.rust.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private void writeRustFile(Path filePath, Node orderedNode){
        try (BufferedWriter bw = Files.newBufferedWriter(filePath)) {
            List<? extends Node> fileNodeChildren = orderedNode.getChildren();
            for (Node childNode : fileNodeChildren) {
                visitingNode(bw, childNode);
            }
        } catch (IOException e) {
            throw new EccoException("Could not write Rust file.", e);
        }
    }

    // TODO change the way this is implemented
    public void visitingNode(BufferedWriter bw, Node childNode) throws IOException {
        Object data = childNode.getArtifact().getData();
        if (!childNode.getChildren().isEmpty()) {
            for (Node node : childNode.getChildren()) {
                visitingNode(bw, node);
            }
        } else if ( data instanceof StructArtifactData) {
            StructArtifactData structArtifactData = (StructArtifactData) childNode.getArtifact().getData();
            bw.write(structArtifactData.getStruct());
        } else if (data instanceof ImplementationArtifactData) {
            ImplementationArtifactData implementationArtifactData = (ImplementationArtifactData) childNode.getArtifact().getData();
            bw.write(implementationArtifactData.getSignature());
        } else if ( data instanceof TraitArtifactData) {
            TraitArtifactData traitArtifactData = (TraitArtifactData) childNode.getArtifact().getData();
            bw.write(traitArtifactData.getTrait());
        } else if ( data instanceof FunctionArtifactData) {
            FunctionArtifactData functionArtifactData = (FunctionArtifactData) childNode.getArtifact().getData();
            bw.write(functionArtifactData.getSignature());
        } else if ( data instanceof LineArtifactData) {
            LineArtifactData lineArtifactData = (LineArtifactData) childNode.getArtifact().getData();
            bw.write(lineArtifactData.getLine());
        } else if ( data instanceof AttributeArtifactData) {
            AttributeArtifactData attributeArtifactData = (AttributeArtifactData) childNode.getArtifact().getData();
            bw.write(attributeArtifactData.getAttribute());
        } else {
            throw new EccoException("Expected known artifact data. But got: " + data.getClass());
        }
        bw.newLine();
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
