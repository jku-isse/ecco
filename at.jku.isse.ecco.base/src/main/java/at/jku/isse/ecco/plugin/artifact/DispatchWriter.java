package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DispatchWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return ArtifactPlugin.class.getName();
	}

	/**
	 * The collection of readers to which should be dispatched.
	 */
	private Collection<ArtifactWriter<Set<Node>, Path>> writers;

	@Inject
	public DispatchWriter(Set<ArtifactWriter<Set<Node>, Path>> writers) {
		this.writers = writers;
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

	private void fireWriteEvent(Path path, ArtifactWriter writer) {
		for (WriteListener listener : this.listeners) {
			listener.fileWriteEvent(path, writer);
		}
	}


	private ArtifactWriter<Set<Node>, Path> getWriterForArtifact(PluginArtifactData artifact) {
		for (ArtifactWriter<Set<Node>, Path> writer : this.writers) {
			if (writer.getPluginId().equals(artifact.getPluginId()))
				return writer;
		}
		return null;
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<Path>();

//		// first write the directories
//		for (Node node : input) {
//			this.writeDirectories(base, node, output);
//		}

		for (Node node : input) {
			this.writeRec(base, node, output);
		}

//		for (Node node : input) {
//			PluginArtifact pluginArtifact = (PluginArtifact) node.getArtifact();
//
//			ArtifactWriter<Set<Node>, Path> writer = this.getWriterForArtifact(pluginArtifact);
//
//			output.addAll(Arrays.asList(writer.write(new HashSet<Node>(node.getAllChildren())))); // TODO: make list to set or set to list??? revisit the whole thing in accordance with the algorithms!
//		}

		return output.toArray(new Path[output.size()]);
	}

	private void writeDirectories(Path base, Node node, List<Path> output) {
		Artifact artifact = node.getArtifact();
		if (artifact.getData() instanceof DirectoryArtifactData) {
			DirectoryArtifactData directoryArtifactData = (DirectoryArtifactData) artifact.getData();
			Path path = base.resolve(directoryArtifactData.getPath());
			try {
				Files.createDirectory(path);
				output.add(path);
				for (Node child : node.getAllChildren()) {
					this.writeDirectories(base, child, output);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void writeRec(Path base, Node node, List<Path> output) {
		Artifact artifact = node.getArtifact();
		if (artifact.getData() instanceof DirectoryArtifactData) {
			DirectoryArtifactData directoryArtifactData = (DirectoryArtifactData) artifact.getData();
			Path path = base.resolve(directoryArtifactData.getPath());
			try {
				if (!path.equals(base))
					Files.createDirectory(path);
				output.add(path);
				this.fireWriteEvent(path, this);
				for (Node child : node.getAllChildren()) {
					this.writeRec(base, child, output);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (artifact.getData() instanceof PluginArtifactData) {
			PluginArtifactData pluginArtifactData = (PluginArtifactData) node.getArtifact().getData();

			ArtifactWriter<Set<Node>, Path> writer = this.getWriterForArtifact(pluginArtifactData);

			Set<Node> pluginInput = new HashSet<Node>();
			pluginInput.add(node);
			output.addAll(Arrays.asList(writer.write(base, pluginInput))); // TODO: make list to set or set to list??? revisit the whole thing in accordance with the algorithms!

			this.fireWriteEvent(pluginArtifactData.getPath(), writer);
		}
	}

}
