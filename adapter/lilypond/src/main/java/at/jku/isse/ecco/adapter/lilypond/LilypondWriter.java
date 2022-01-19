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
			output.add(outputPath);

			try (BufferedWriter bw = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
				ArtifactIterator it = new ArtifactIterator(fileNode);
				if (it.hasNext()) {
					Node cur = it.next();
					DefaultTokenArtifactData d = (DefaultTokenArtifactData)cur.getArtifact().getData();
					while (it.hasNext()) {
						Node next = it.next();
						bw.write(d.getText());
						DefaultTokenArtifactData n = (DefaultTokenArtifactData)next.getArtifact().getData();
						if (LilypondFormatter.appendSpace(d, n)) {
							bw.write(" ");
						}
						d = n;
					}
					bw.write(d.getText());
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return output.toArray(new Path[0]);
	}

	static class ArtifactIterator implements Iterator<Node> {
		private List<? extends  Node> children;
		private Node nextNode = null;
		Stack<Integer> indexes = new Stack<>();

		public ArtifactIterator(Node n) {
			assert n != null;

			do {
				calcNext(n);
			} while (nextNode != null && !(nextNode.getArtifact().getData() instanceof DefaultTokenArtifactData));
		}

		@Override
		public boolean hasNext() {
			return nextNode != null;
		}

		@Override
		public Node next() {
			Node current = nextNode;
			do {
				calcNext(nextNode);
			} while (nextNode != null && !(nextNode.getArtifact().getData() instanceof DefaultTokenArtifactData));
			return current;
		}

		private void calcNext(Node n) {
			List<? extends Node> cs = n.getChildren();
			if (cs.size() > 0) {
				children = cs;
				indexes.push(0);
				nextNode = children.get(0);

			} else {
				while (true) {
					int i = indexes.pop();
					i++;
					if (i < children.size()) {
						indexes.push(i);
						nextNode = children.get(i);
						return;

					} else if (indexes.size() == 0 && i == children.size()) {
						nextNode = null;
						return;

					} else {
						n = n.getParent();
						children = n.getParent().getChildren();
					}
				}
			}
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
