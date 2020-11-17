package at.jku.isse.ecco.adapter.file;

import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class FileWriter implements ArtifactWriter<Set<Node>, Path> {

	public FileWriter() {

	}

	@Override
	public String getPluginId() {
		return FilePlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<Path>();

		for (Node node : input) {
			PluginArtifactData pluginArtifact = (PluginArtifactData) node.getArtifact().getData();
			Path outputPath = base.resolve(pluginArtifact.getPath()); // TODO: this resolve might not be necessary as the artifact stores the relative path anyway.
			output.add(outputPath);

			if (node.getChildren().size() != 1) {
				// TODO: ERROR? OR: write empty file.
				try {
					Files.write(outputPath, new byte[]{});
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				for (Node childNode : node.getChildren()) {
					FileArtifactData fileArtifact = (FileArtifactData) childNode.getArtifact().getData(); // TODO: node type must have Type parameter for artifact type it contains?
					try {
						// Path path = Files.write(artifact.getPath(), artifact.getData());
						Files.write(outputPath, fileArtifact.getData());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
//				FileArtifactData fileArtifact = (FileArtifactData) node.getChildren().get(0).getArtifact().getData(); // TODO: node type must have Type parameter for artifact type it contains?
//
//				try {
//					// Path path = Files.write(artifact.getPath(), artifact.getData());
//					Files.write(outputPath, fileArtifact.getData());
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
			}
		}

		return output.toArray(new Path[output.size()]);
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
