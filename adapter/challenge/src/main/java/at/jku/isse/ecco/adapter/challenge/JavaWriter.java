package at.jku.isse.ecco.adapter.challenge;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class JavaWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return JavaPlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<>();

		// TODO: implement!

		return output.toArray(new Path[0]);
	}


	private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
