package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoUtil;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;

public class DispatchWriter implements ArtifactWriter<Set<? extends Node>, Path> {

	protected static final Logger LOGGER = Logger.getLogger(DispatchWriter.class.getName());


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
				if (Files.list(base).anyMatch(path -> !path.equals(this.repositoryDir))) {
					throw new EccoException("Current base directory must be empty for checkout operation.");
				}
			} catch (IOException e) {
				throw new EccoException(e.getMessage());
			}
		} else {
			throw new EccoException("Current base directory is not a directory but a file.");
		}


		List<Path> output = new ArrayList<>();

		Properties hashes = new Properties();
		for (Node node : input) {
			this.writeRec(base, base, node, output, hashes);
		}

		// write hashes file into base directory
		Path hashesFile = base.resolve(EccoService.HASHES_FILE_NAME);
		if (Files.exists(hashesFile)) {
			throw new EccoException("Hashes file already exists in base directory.");
		} else {
			try (Writer writer = Files.newBufferedWriter(hashesFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				hashes.store(writer, null);
			} catch (IOException e) {
				throw new EccoException("Could not create hashes file.", e);
			}
			this.fireWriteEvent(hashesFile, this);
		}

		return output.toArray(new Path[0]);
	}

	private void writeRec(Path base, Path parent, Node node, List<Path> output, Properties hashes) {
		Artifact artifact = node.getArtifact();
		if (artifact.getData() instanceof DirectoryArtifactData) {
			DirectoryArtifactData directoryArtifactData = (DirectoryArtifactData) artifact.getData();
			Path path = parent.resolve(directoryArtifactData.getPath());
			try {
				if (!path.equals(parent))
					Files.createDirectory(path);
				output.add(path);
				this.fireWriteEvent(path, this);
				for (Node child : node.getChildren()) {
					this.writeRec(base, path, child, output, hashes);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (artifact.getData() instanceof PluginArtifactData) {
			PluginArtifactData pluginArtifactData = (PluginArtifactData) node.getArtifact().getData();

			ArtifactWriter<Set<Node>, Path> writer = this.getWriterForArtifact(pluginArtifactData);

			Set<Node> pluginInput = new HashSet<>();
			pluginInput.add(node);

			Path[] outputPaths = writer.write(base, pluginInput);
			for (Path outputPath : outputPaths) {
				hashes.put(outputPath.toString(), EccoUtil.getSHA(base.resolve(outputPath)));
			}

			output.addAll(Arrays.asList(outputPaths));

			this.fireWriteEvent(pluginArtifactData.getPath(), writer);
		}
	}

}
