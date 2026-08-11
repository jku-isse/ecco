package at.jku.cdl.ecco.adapter.java;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

public class JavaASTStringWriter implements ArtifactWriter<Set<Node>, String> {

	@Override
	public String getPluginId() {
		return JavaASTPlugin.class.getName();
	}

	@Override
	public String[] write(String base, Set<Node> input) {
		List<String> output = new ArrayList<>(input.size());
		for (Node root : input) {
			String outputString = "";
			output.add(outputString);
			if (!root.getChildren().isEmpty()) {
				JavaASTWriteHandler.writeJavaString(root, outputString);
			}
		}
		return output.toArray(new String[output.size()]);
	}

	@Override
	public String[] write(Set<Node> input) {
		return write("", input);
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
