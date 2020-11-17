package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.ArtifactExporter;
import at.jku.isse.ecco.service.listener.ExportListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class TextExporter implements ArtifactExporter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}


	@Override
	public Path[] export(Path base, Set<Node> input) {
		return new Path[0]; // TODO
	}

	@Override
	public Path[] export(Set<Node> input) {
		return new Path[0]; // TODO
	}


	private Collection<ExportListener> listeners = new ArrayList<>();

	@Override
	public void addListener(ExportListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ExportListener listener) {
		this.listeners.remove(listener);
	}

}
