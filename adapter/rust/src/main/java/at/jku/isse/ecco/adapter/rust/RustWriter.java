package at.jku.isse.ecco.adapter.rust;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.rust.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.rust.data.LineArtifactData;
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
     * @return 
     */
    @Override
    public String getPluginId() { return RustPlugin.class.getName(); }

    /**
     * @param base 
     * @param input
     * @return
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
            List<Node> fileNodeChildren = (List<Node>) orderedNode.getChildren();
            for (Node node : fileNodeChildren){
                Artifact<?> artifact = node.getArtifact();
                ArtifactData artifactData = artifact.getData();
                if (artifactData instanceof FunctionArtifactData){
                    this.writeFunctionNode(bw, node);
                } else if (artifactData instanceof LineArtifactData){
                    LineArtifactData lineArtifactData = (LineArtifactData) artifactData;
                    bw.write(lineArtifactData.getLine());
                    bw.newLine();
                } else {
                    throw new EccoException("Expected FunctionArtifactData or LineArtifactData.");
                }
            }
        } catch (IOException e) {
            throw new EccoException("Could not write Rust file.", e);
        }
    }


    /**
     * @param input 
     * @return
     */
    @Override
    public Path[] write(Set<Node> input) {
        return new Path[0];
    }

    private void writeFunctionNode(BufferedWriter bw, Node functionNode) throws IOException {
        List<Node> lineNodeChildren =  (List<Node>) functionNode.getChildren();
        for (Node lineNode : lineNodeChildren){
            ArtifactData artifactData = lineNode.getArtifact().getData();
            if (artifactData instanceof LineArtifactData) {
                LineArtifactData lineArtifactData = (LineArtifactData) lineNode.getArtifact().getData();
                bw.write(lineArtifactData.getLine());
                bw.newLine();
            } else {
                throw new EccoException("Expected LineArtifactData.");
            }
        }
    }

    /**
     * @param listener 
     */
    @Override
    public void addListener(WriteListener listener) {
        this.listeners.add(listener);

    }

    /**
     * @param listener 
     */
    @Override
    public void removeListener(WriteListener listener) {
        this.listeners.remove(listener);
    }

    public static void main(String[] args) {
        RustReader reader = new RustReader(new MemEntityFactory());
        Path[] input = {Paths.get("/home/zaber/Documents/bachelor/ecco/adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/simple.rs")};
        Set<Node.Op> nodes = reader.read(input);
        Node first = (Node) nodes.toArray()[0];
        RustWriter writer = new RustWriter();
        writer.writeRustFile(Paths.get("/home/zaber/Documents/bachelor/ecco/adapter/rust/src/main/java/at/jku/isse/ecco/adapter/rust/simple_out.rs"), first);
    }
}
