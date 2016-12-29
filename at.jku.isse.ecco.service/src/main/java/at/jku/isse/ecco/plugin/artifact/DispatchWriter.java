package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DispatchWriter implements ArtifactWriter<Set<? extends Node>, Path> {

	@Override
	public String getPluginId() {
		return ArtifactPlugin.class.getName();
	}

	/**
	 * The collection of readers to which should be dispatched.
	 */
	private Collection<ArtifactWriter<Set<Node>, Path>> writers;

	private Path repositoryDir;

	@Inject
	public DispatchWriter(Set<ArtifactWriter<Set<Node>, Path>> writers, @Named("repositoryDir") Path repositoryDir) {
		this.writers = writers;
		this.repositoryDir = repositoryDir;
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
	public Path[] write(Set<? extends Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<? extends Node> input) {
		if (!Files.exists(base)) {
			throw new EccoException("Base directory does not exist.");
		} else if (Files.isDirectory(base)) {
			try {
				if (Files.list(base).filter(path -> {
					return !path.equals(this.repositoryDir);
				}).findAny().isPresent()) {
					throw new EccoException("Current base directory must be empty for checkout operation.");
				}
			} catch (IOException e) {
				throw new EccoException(e.getMessage());
			}
		} else {
			throw new EccoException("Current base directory is not a directory but a file.");
		}


		List<Path> output = new ArrayList<>();

		for (Node node : input) {
			this.writeRec(base, node, output);
		}

		return output.toArray(new Path[output.size()]);
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
				for (Node child : node.getChildren()) {
					this.writeRec(base, child, output);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (artifact.getData() instanceof PluginArtifactData) {
			PluginArtifactData pluginArtifactData = (PluginArtifactData) node.getArtifact().getData();

			ArtifactWriter<Set<Node>, Path> writer = this.getWriterForArtifact(pluginArtifactData);

			Set<Node> pluginInput = new HashSet<>();
			pluginInput.add(node);
			output.addAll(Arrays.asList(writer.write(base, pluginInput)));

			this.fireWriteEvent(pluginArtifactData.getPath(), writer);
		}
	}

}