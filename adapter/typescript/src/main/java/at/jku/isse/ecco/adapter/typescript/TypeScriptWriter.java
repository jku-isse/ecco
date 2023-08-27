package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.VariableAssignmentData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class TypeScriptWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return TypeScriptPlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return new Path[0];
	}

	@Override
	public Path[] write(Path base,Set<Node> input) {
		List<Path> output = new ArrayList<>();
		// Every node in the input is a text file. The children of files are lines. The children of lines are characters.
		for (Node fileNode : input) {
			StringBuilder sb = new StringBuilder();
			PluginArtifactData rootData = (PluginArtifactData) fileNode.getArtifact().getData();
			for (Node lineNode : fileNode.getChildren()) {
				ArtifactData lineArtifactData = lineNode.getArtifact().getData();
				sb.append(lineArtifactData.toString());
				sb.append("\n");
			}
			try (BufferedWriter writer = Files.newBufferedWriter(base, StandardCharsets.UTF_8)) {
				writer.write(sb.toString());
			} catch (IOException x) {
				System.err.format("IOException: %s%n", x);
			}
			output.add(base.resolve(rootData.getPath()));
		}

		return (Path[]) output.toArray();
	}

	private void writeNode(StringBuilder sb, Node node){

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
