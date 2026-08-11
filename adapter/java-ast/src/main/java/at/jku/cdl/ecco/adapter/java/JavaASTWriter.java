package at.jku.cdl.ecco.adapter.java;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

public class JavaASTWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return JavaASTPlugin.class.getName();
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<>(input.size());
		for (Node root : input) {
			PluginArtifactData pluginArtifact = (PluginArtifactData) root.getArtifact().getData();
			Path outputPath = base.resolve(pluginArtifact.getPath()); // TODO: this resolve might not be necessary as
																		// the artifact stores the relative path anyway.
			output.add(outputPath);
			if (!root.getChildren().isEmpty()) {
				JavaASTWriteHandler.writeJavaFile(root, outputPath);
			}
		}
		return output.toArray(new Path[output.size()]);
	}

	

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public void addListener(WriteListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeListener(WriteListener listener) {
		// TODO Auto-generated method stub

	}

}
