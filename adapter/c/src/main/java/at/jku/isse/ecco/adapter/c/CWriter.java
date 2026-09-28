package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.c.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.dispatch.TextFileFormat;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

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
            if (!(artifactData instanceof PluginArtifactData)){
                throw new EccoException("Expected plugin artifact data.");
            }
            PluginArtifactData pluginArtifactData = (PluginArtifactData) artifactData;
            Path outputPath = base.resolve(pluginArtifactData.getPath());
            output.add(outputPath);

            this.writeCFile(outputPath, fileNode, pluginArtifactData);
        }

        return output.toArray(new Path[0]);
    }

    private void writeCFile(Path filePath, Node orderedNode, PluginArtifactData pluginArtifactData){
        // reproduces the charset/line separator/final newline recorded at commit time (TextFileFormat)
        try (TextFileFormat.LineWriter bw = TextFileFormat.newLineWriter(filePath, pluginArtifactData)) {
            List<Node> fileNodeChildren = (List<Node>) orderedNode.getChildren();
            for (Node node : fileNodeChildren){
                Artifact<?> artifact = node.getArtifact();
                ArtifactData artifactData = artifact.getData();
                if (artifactData instanceof FunctionArtifactData){
                    this.writeFunctionNode(bw, node);
                } else if (artifactData instanceof LineArtifactData){
                    LineArtifactData lineArtifactData = (LineArtifactData) artifactData;
                    bw.writeLine(lineArtifactData.getLine());
                } else {
                    throw new EccoException("Expected FunctionArtifactData or LineArtifactData.");
                }
            }
        } catch (IOException e) {
            throw new EccoException("Could not write file: " + filePath, e);
        }
    }

    private void writeFunctionNode(TextFileFormat.LineWriter bw, Node functionNode) throws IOException {
        List<Node> lineNodeChildren = (List<Node>) functionNode.getChildren();
        for (Node lineNode : lineNodeChildren){
            LineArtifactData lineArtifactData = (LineArtifactData) lineNode.getArtifact().getData();
            bw.writeLine(lineArtifactData.getLine());
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
