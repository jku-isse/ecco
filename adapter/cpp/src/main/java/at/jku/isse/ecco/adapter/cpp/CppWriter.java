package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.cpp.data.ScopeArtifactData;
import at.jku.isse.ecco.adapter.cpp.data.SourceLineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.dispatch.TextFileFormat;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Writes C++ files as CppReader reads them: their lines in order, whatever scopes group them, in the
 * charset/line separator/final newline recorded at commit time (TextFileFormat). Files of the retired
 * first format go to LegacyCppWriter.
 */
public class CppWriter implements ArtifactWriter<Set<Node>, Path> {

    private final Collection<WriteListener> listeners = new ArrayList<>();

    @Override
    public String getPluginId() {
        return CppPlugin.class.getName();
    }

    @Override
    public Path[] write(Set<Node> input) {
        return this.write(Paths.get("."), input);
    }

    @Override
    public Path[] write(Path base, Set<Node> input) {
        List<Path> output = new ArrayList<>();
        for (Node fileNode : input) {
            if (!(fileNode.getArtifact().getData() instanceof PluginArtifactData pluginArtifactData))
                throw new EccoException("Expected plugin artifact data.");
            Path outputPath = base.resolve(pluginArtifactData.getPath());
            try {
                if (isRetiredFormat(fileNode)) {
                    Path written = new LegacyCppWriter().processNode(fileNode, base);
                    if (written != null)
                        output.add(written);
                    continue;
                }
                try (TextFileFormat.LineWriter writer = TextFileFormat.newLineWriter(outputPath, pluginArtifactData)) {
                    this.writeLines(fileNode, writer);
                }
            } catch (IOException e) {
                throw new EccoException("Could not write file: " + outputPath, e);
            }
            output.add(outputPath);
        }
        return output.toArray(new Path[0]);
    }

    private void writeLines(Node node, TextFileFormat.LineWriter writer) throws IOException {
        for (Node child : node.getChildren()) {
            ArtifactData data = child.getArtifact().getData();
            if (data instanceof SourceLineArtifactData line)
                writer.writeLine(line.getLine());
            else if (data instanceof ScopeArtifactData)
                this.writeLines(child, writer);
            else
                throw new EccoException("Unexpected artifact in a C++ file: " + data);
        }
    }

    private static boolean isRetiredFormat(Node fileNode) {
        for (Node child : fileNode.getChildren())
            if (child.getArtifact().getData().retiredFormat() != null)
                return true;
        return false;
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
