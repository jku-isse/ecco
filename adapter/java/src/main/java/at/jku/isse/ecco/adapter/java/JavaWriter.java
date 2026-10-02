package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
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

	/**
	 * This adapter only reads: it has no writer for the trees it builds. Writing nothing, as it
	 * did, made a checkout look successful while the .java files were missing.
	 */
	@Override
	public Path[] write(Path base, Set<Node> input) {
		if (input.isEmpty())
			return new Path[0];
		throw new EccoException("The Java (lines) adapter (" + JavaPlugin.class.getName() + ") can only read files, not write them: "
				+ "map *.java to the Java (AST) adapter (at.jku.cdl.ecco.adapter.java.JavaASTPlugin) in .adapters to check out Java files.");
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
