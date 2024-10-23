package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.c.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class CWriter implements ArtifactWriter<Set<Node>, Path> {

    private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

    @Override
    public String getPluginId() {
        return CPlugin.class.getName();
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        List<Path> output = new ArrayList<>();

        for (Node fileNode : input) {
            Artifact<?> fileArtifact = fileNode.getArtifact();
            ArtifactData artifactData = fileArtifact.getData();
            if (!(artifactData instanceof PluginArtifactData pluginArtifactData))
                throw new EccoException("Expected plugin artifact data.");
            Path outputPath = base.resolve(pluginArtifactData.getPath());
            output.add(outputPath);

            this.writeCFile(outputPath, fileNode);
        }

        return output.toArray(new Path[0]);
    }

    private void writeCFile(Path filePath, Node orderedNode){
        try (BufferedWriter bw = Files.newBufferedWriter(filePath)) {
            List<Node> fileNodeChildren = (List<Node>) orderedNode.getChildren();
            for (Node node : fileNodeChildren){
                Artifact<?> artifact = node.getArtifact();
                ArtifactData artifactData = artifact.getData();
                if (artifactData instanceof FunctionArtifactData){
                    this.writeFunctionNode(bw, node);
                } else if (artifactData instanceof LineArtifactData lineArtifactData){
                    bw.write(lineArtifactData.getLine());
                    bw.newLine();
                } else {
                    throw new EccoException("Expected FunctionArtifactData or LineArtifactData.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeFunctionNode(BufferedWriter bw, Node functionNode) throws IOException {
        List<Node> lineNodeChildren = (List<Node>) functionNode.getChildren();
        for (Node lineNode : lineNodeChildren){
            LineArtifactData lineArtifactData = (LineArtifactData) lineNode.getArtifact().getData();
            bw.write(lineArtifactData.getLine());
            bw.newLine();
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
