package at.jku.isse.ecco.plugin.artifact.uml;

import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.Set;

public class UmlWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return null;
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		return new Path[0];
	}

	@Override
	public Path[] write(Set<Node> input) {
		return new Path[0];
	}

	@Override
	public void addListener(WriteListener listener) {

	}

	@Override
	public void removeListener(WriteListener listener) {

	}
}
