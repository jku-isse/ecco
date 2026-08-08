package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
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
import java.util.*;

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
			if (!(artifactData instanceof PluginArtifactData pluginArtifactData))
				throw new EccoException("Expected plugin artifact data.");
			Path outputPath = base.resolve(pluginArtifactData.getPath());

			try (BufferedWriter bw = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
				List<Node> tokenNodes = new ArrayList<>();
				collectTokenNodes(fileNode, tokenNodes);

				if (!tokenNodes.isEmpty()) {
					Iterator<Node> it = tokenNodes.iterator();
					DefaultTokenArtifactData d = (DefaultTokenArtifactData) it.next().getArtifact().getData();
					while (it.hasNext()) {
						DefaultTokenArtifactData n = (DefaultTokenArtifactData) it.next().getArtifact().getData();
						bw.write(d.getText());
						if (LilypondFormatter.appendSpace(d, n)) {
							bw.write(" ");
						}
						d = n;
					}
					bw.write(d.getText());
				}

			} catch (IOException e) {
				throw new EccoException("Could not write file: " + outputPath, e);
			}

			output.add(outputPath);
		}

		return output.toArray(new Path[0]);
	}

	/**
	 * Collects every {@link DefaultTokenArtifactData} node reachable from {@code n}, in document
	 * (pre-order) order, into {@code out}. Recurses into every node's children unconditionally -
	 * including a token node's own children, if it has any - matching how the tree is actually
	 * built (a node's data type doesn't affect whether it can have children). Package-visible: also
	 * used by {@link LilypondStringWriter}, which joins the same token sequence into a String
	 * instead of writing it to a file.
	 */
	static void collectTokenNodes(Node n, List<Node> out) {
		if (n.getArtifact().getData() instanceof DefaultTokenArtifactData) {
			out.add(n);
		}
		for (Node child : n.getChildren()) {
			collectTokenNodes(child, out);
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
