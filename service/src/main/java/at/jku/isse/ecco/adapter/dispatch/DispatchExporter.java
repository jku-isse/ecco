package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactExporter;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.service.listener.ExportListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class DispatchExporter implements ArtifactExporter<Set<? extends Node>, Path> {

	protected static final Logger LOGGER = Logger.getLogger(DispatchExporter.class.getName());


	@Override
	public String getPluginId() {
		return ArtifactPlugin.class.getName();
	}

	/**
	 * The collection of readers to which should be dispatched.
	 */
	private Collection<ArtifactExporter<Set<Node>, Path>> exporters;

	private Path repositoryDir;

	@Inject
	public DispatchExporter(Set<ArtifactExporter<Set<Node>, Path>> exporters, @Named("repositoryDir") Path repositoryDir) {
		this.exporters = exporters;
		this.repositoryDir = repositoryDir;
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

	private void fireExportEvent(Path path, ArtifactExporter exporter) {
		for (ExportListener listener : this.listeners) {
			listener.fileExportEvent(path, exporter);
		}
	}


	private ArtifactExporter<Set<Node>, Path> getExporterForArtifact(PluginArtifactData artifact) {
		for (ArtifactExporter<Set<Node>, Path> exporter : this.exporters) {
			if (exporter.getPluginId().equals(artifact.getPluginId()))
				return exporter;
		}
		return null;
	}

	@Override
	public Path[] export(Set<? extends Node> input) {
		return this.export(Paths.get("."), input);
	}

	@Override
	public Path[] export(Path base, Set<? extends Node> input) {
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

		for (Node node : input) {
			this.exportRec(base, node, output);
		}

		return output.toArray(new Path[0]);
	}

	private void exportRec(Path base, Node node, List<Path> output) {
		Artifact artifact = node.getArtifact();
		if (artifact.getData() instanceof DirectoryArtifactData) {
			DirectoryArtifactData directoryArtifactData = (DirectoryArtifactData) artifact.getData();
			Path path = base.resolve(directoryArtifactData.getPath());
			try {
				if (!path.equals(base))
					Files.createDirectory(path);
				output.add(path);
				this.fireExportEvent(path, this);
				for (Node child : node.getChildren()) {
					this.exportRec(base, child, output);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (artifact.getData() instanceof PluginArtifactData) {
			PluginArtifactData pluginArtifactData = (PluginArtifactData) node.getArtifact().getData();

			ArtifactExporter<Set<Node>, Path> exporter = this.getExporterForArtifact(pluginArtifactData);

			Set<Node> pluginInput = new HashSet<>();
			pluginInput.add(node);

			Path[] outputPaths = exporter.export(base, pluginInput);

			output.addAll(Arrays.asList(outputPaths));

			this.fireExportEvent(pluginArtifactData.getPath(), exporter);
		}
	}

}
