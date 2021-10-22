package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.context.BaseContextArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class LilypondWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<>();

		// Every node in the input is a text file. The children of files are tokens. The children of tokens are characters.
		for (Node fileNode : input) {
			Artifact<?> fileArtifact = fileNode.getArtifact();
			ArtifactData artifactData = fileArtifact.getData();
			if (!(artifactData instanceof PluginArtifactData))
				throw new EccoException("Expected plugin artifact data.");
			PluginArtifactData pluginArtifactData = (PluginArtifactData) artifactData;
			Path outputPath = base.resolve(pluginArtifactData.getPath());
			output.add(outputPath);

			try (BufferedWriter bw = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                for (Node n : fileNode.getChildren()) {
                    writeNodeRec(n, bw);
                }

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return output.toArray(new Path[0]);
	}

    private void writeNodeRec(Node n, BufferedWriter bw) throws IOException {
        ArtifactData d = n.getArtifact().getData();
        if (d instanceof BaseContextArtifactData) {
            for (Node cn : n.getChildren()) {
                writeNodeRec(cn, bw);
            }

        } else if (d instanceof DefaultTokenArtifactData) {
            DefaultTokenArtifactData dad = (DefaultTokenArtifactData)d;
            bw.write(dad.getText());
            bw.write(dad.getPostWhitespace());
        }
    }

	private Collection<WriteListener> listeners = new ArrayList<>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
