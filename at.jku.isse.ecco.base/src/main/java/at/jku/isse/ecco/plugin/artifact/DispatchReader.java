package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class DispatchReader implements ArtifactReader<Path, Set<Node>> {

	private final EntityFactory entityFactory;

	@Override
	public String getPluginId() {
		return ArtifactPlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	/**
	 * The collection of readers to which should be dispatched.
	 */
	private Collection<ArtifactReader<Path, Set<Node>>> readers;

	/**
	 * @param readers The collection of readers to which should be dispatched.
	 */
	@Inject
	public DispatchReader(EntityFactory entityFactory, Set<ArtifactReader<Path, Set<Node>>> readers) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
		this.readers = readers;
	}

	private Collection<ReadListener> listeners = new ArrayList<ReadListener>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

	private void fireReadEvent(Path path, ArtifactReader reader) {
		for (ReadListener listener : this.listeners) {
			listener.fileReadEvent(path, reader);
		}
	}

	@Override
	public boolean canRead(Path file) {
		for (ArtifactReader<Path, Set<Node>> reader : this.readers) {
			if (reader.canRead(file))
				return true;
		}
		return false;
	}

	/**
	 * @param file The file to be read.
	 * @return The reader best suited for reading the file.
	 */
	private ArtifactReader<Path, Set<Node>> getReaderForFile(Path base, Path file) {
		ArtifactReader<Path, Set<Node>> currentReader = null;
		for (ArtifactReader<Path, Set<Node>> reader : this.readers) {
			if (reader.canRead(base.resolve(file)) && (currentReader == null || currentReader.getTypeHierarchy().length < reader.getTypeHierarchy().length))
				currentReader = reader;
		}
		return currentReader;
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {
		Set<Node> nodes = new HashSet<Node>();

		base = base.normalize();

		Map<ArtifactReader<Path, Set<Node>>, ArrayList<Path>> readerToFilesMap = new HashMap<ArtifactReader<Path, Set<Node>>, ArrayList<Path>>();

		// this reader itself is responsible for the directory tree structure (unless there is an adapter that deals with a directory)
		Set<Path> directories = new HashSet<Path>();
		Set<Path> files = new HashSet<Path>();
		for (Path path : input) {
			path = path.normalize();
			Path resolvedPath = base.resolve(path);
			if (Files.isDirectory(resolvedPath) && this.getReaderForFile(base, base.relativize(resolvedPath)) == null) {
				directories.add(base.relativize(resolvedPath));
				this.fireReadEvent(base.relativize(resolvedPath), this);
			} else {
				files.add(base.relativize(resolvedPath));
			}
		}
		Map<Path, Node> directoryNodes = new HashMap<Path, Node>();
		Node baseDirectoryNode = this.readDirectories(base, base, directories, directoryNodes);
		nodes.add(baseDirectoryNode);

		// assign files to readers
		for (Path file : files) {
			ArtifactReader<Path, Set<Node>> reader = this.getReaderForFile(base, file);

			if (reader != null) {
				ArrayList<Path> fileList = readerToFilesMap.get(reader);
				if (fileList == null)
					fileList = new ArrayList<Path>();
				fileList.add(file);
				readerToFilesMap.put(reader, fileList);
				this.fireReadEvent(file, reader);
			}
		}

		// let readers read the assigned files
		for (ArtifactReader<Path, Set<Node>> reader : this.readers) {
			ArrayList<Path> filesList = readerToFilesMap.get(reader);

			if (filesList != null) {
				Path[] pluginInput = filesList.toArray(new Path[filesList.size()]);

				Set<Node> pluginNodes = reader.read(base, pluginInput);
				for (Node pluginNode : pluginNodes) {
					PluginArtifactData pluginArtifactData = (PluginArtifactData) pluginNode.getArtifact().getData();
					Path parent = pluginArtifactData.getPath().getParent();
					if (parent == null)
						parent = Paths.get(".").normalize();
					directoryNodes.get(parent).addChild(pluginNode);
				}
			}
		}

		// return produced nodes
		return nodes;
	}

	private Node readDirectories(Path base, Path current, Set<Path> directories, Map<Path, Node> directoryNodes) {
		Path relativeCurrent = base.relativize(current);
		Artifact directoryArtifact = entityFactory.createArtifact(new DirectoryArtifactData(relativeCurrent));
		Node directoryNode = entityFactory.createNode(directoryArtifact);
		directoryNodes.put(relativeCurrent, directoryNode);

		try {
			Files.list(current).filter(d -> {
				return directories.contains(base.relativize(d));
			}).forEach(d -> {
				directories.remove(d);
				directoryNode.addChild(this.readDirectories(base, d, directories, directoryNodes));
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

		return directoryNode;
	}

}
