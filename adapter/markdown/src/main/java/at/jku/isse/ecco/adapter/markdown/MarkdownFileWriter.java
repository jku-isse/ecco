package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Writes ECCO's tree back out to a Markdown file - a plain pre-order walk that prints every {@code
 * LineArtifactData} leaf's raw text, in tree order, and nothing else. No block-type-specific logic at
 * all: since every leaf everywhere in this tree already holds a verbatim original source line (see
 * {@code MarkdownTreeBuilder}), reconstructing the file is just "print every line, in order" -
 * container types (sections, lists, tables, ...) only exist to give those lines structure for
 * composition, not to be interpreted here.
 */
public class MarkdownFileWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return MarkdownPlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<>();

		for (Node fileNode : input) {
			Artifact<?> fileArtifact = fileNode.getArtifact();
			ArtifactData artifactData = fileArtifact.getData();
			if (!(artifactData instanceof PluginArtifactData))
				throw new EccoException("Expected plugin artifact data.");
			PluginArtifactData pluginArtifactData = (PluginArtifactData) artifactData;
			Path outputPath = base.resolve(pluginArtifactData.getPath());
			output.add(outputPath);

			try (BufferedWriter bw = Files.newBufferedWriter(outputPath)) {
				this.writeLines(fileNode, bw);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return output.toArray(new Path[0]);
	}

	private void writeLines(Node node, BufferedWriter bw) throws IOException {
		for (Node child : node.getChildren()) {
			ArtifactData data = child.getArtifact().getData();
			if (data instanceof LineArtifactData lineArtifactData) {
				bw.write(lineArtifactData.getLine());
				bw.newLine();
			} else {
				this.writeLines(child, bw);
			}
		}
	}

	private final Collection<WriteListener> listeners = new ArrayList<>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
