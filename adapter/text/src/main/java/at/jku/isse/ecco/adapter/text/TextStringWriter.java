package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class TextStringWriter implements ArtifactWriter<Set<Node>, String> {

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}

	@Override
	public String[] write(String base, Set<Node> input) {
		return this.write(input);
	}

	@Override
	public String[] write(Set<Node> input) {
		List<String> output = new ArrayList<>();

		// Every node in the input is a text file. The children of files are lines. The children of lines are characters.
		for (Node fileNode : input) {
			StringBuilder sb = new StringBuilder();
			for (Node lineNode : fileNode.getChildren()) {
				LineArtifactData lineArtifactData = (LineArtifactData) lineNode.getArtifact().getData();

				sb.append(lineArtifactData.getLine());
				sb.append("\n");
			}
			output.add(sb.toString());
		}

		return output.toArray(new String[0]);
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
