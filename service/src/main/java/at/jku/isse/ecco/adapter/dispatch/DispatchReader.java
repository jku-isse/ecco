package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import javax.inject.Named;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class DispatchReader implements ArtifactReader<Path, Set<Node.Op>> {

	protected static final Logger LOGGER = Logger.getLogger(DispatchWriter.class.getName());

	public static final Path IGNORES_FILE_NAME = Paths.get(".ignores");
	public static final Path ADAPTERS_FILE_NAME = Paths.get(".adapters");


	@Override
	public String getPluginId() {
		return ArtifactPlugin.class.getName();
	}

	private Map<Integer, String[]> prioritizedPatterns;

	@Override
	public Map<Integer, String[]> getPrioritizedPatterns() {
		return Collections.unmodifiableMap(this.prioritizedPatterns);
	}


	private final EntityFactory entityFactory;

	private Path repositoryDir;

	/**
	 * The collection of readers to which should be dispatched.
	 */
	private Collection<ArtifactReader<Path, Set<Node.Op>>> readers;

	/**
	 * Set of glob patterns (strings) for files to ignore.
	 */
	private Set<String> ignorePatterns;

	/**
	 * Ordered pairs of glob patterns and plugin ids.
	 */
	private List<Mapping> adapterMappings;

	/**
	 * @param entityFactory The entity factory used by this reader for creating nodes and artifacts.
	 * @param readers       The collection of readers to which should be dispatched.
	 * @param repositoryDir The repository directory.
	 */
	@Inject
	public DispatchReader(EntityFactory entityFactory, Set<ArtifactReader<Path, Set<Node.Op>>> readers, @Named("repositoryDir") Path repositoryDir) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
		this.repositoryDir = repositoryDir;

		this.readers = readers;

		this.ignorePatterns = new HashSet<>();

		this.adapterMappings = new ArrayList<>();

		this.prioritizedPatterns = new HashMap<>();
	}

	public void addAdapterMappings(String pattern, ArtifactReader<Path, Set<Node.Op>> reader) {
		this.adapterMappings.add(new Mapping(pattern, reader));
	}

	public class Mapping {
		private String pattern;
		private ArtifactReader<Path, Set<Node.Op>> reader;

		public Mapping(String pattern, ArtifactReader<Path, Set<Node.Op>> reader) {
			this.pattern = pattern;
			this.reader = reader;
		}

		public String getPattern() {
			return this.pattern;
		}

		public ArtifactReader<Path, Set<Node.Op>> getReader() {
			return this.reader;
		}
	}


	public void init() {
		// load ignore patterns from IGNORES_FILE_NAME file in this.repositoryDir folder
		try {
			Path ignoresFile = this.repositoryDir.resolve(IGNORES_FILE_NAME);
			if (!Files.exists(ignoresFile))
				Files.createFile(ignoresFile);
			List<String> ignorePatterns = Files.readAllLines(ignoresFile).stream().filter(line -> !line.trim().isEmpty()).collect(Collectors.toList());
			this.ignorePatterns.addAll(ignorePatterns);
		} catch (IOException e) {
			throw new EccoException("Error creating or reading ignores file.", e);
		}

		// load plugin mappings from ADAPTERS_FILE_NAME file in this.repositoryDir folder
		try {
			Path adaptersFile = this.repositoryDir.resolve(ADAPTERS_FILE_NAME);
			// check if adapters file exists
			if (!Files.exists(adaptersFile)) {
				// if not create adapters mapping from loaded adapter plugins and write mappings to file
				Files.createFile(adaptersFile);
				// get adapter patterns for every adapter plugin
				Map<Integer, Collection<Mapping>> prioritizedMappings = new HashMap<>();
				for (ArtifactReader<Path, Set<Node.Op>> reader : this.readers) {
					for (Map.Entry<Integer, String[]> entry : reader.getPrioritizedPatterns().entrySet()) {
						Collection<Mapping> existingPatterns = prioritizedMappings.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
						for (String pattern : entry.getValue()) {
							existingPatterns.add(new Mapping(pattern, reader));
						}
					}
				}
				// write patterns to file, highest priority first
				List<CharSequence> adapterMappingsStrings = prioritizedMappings.entrySet().stream().sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey())).map(Map.Entry::getValue).flatMap(Collection::stream).map(m -> m.getReader().getPluginId() + ";" + m.getPattern()).collect(Collectors.toList());
				Files.write(adaptersFile, adapterMappingsStrings);
			}
			// load adapter mappings from file
			List<String> adapterPatterns = Files.readAllLines(adaptersFile);
			for (String adapterPattern : adapterPatterns) {
				String[] pair = adapterPattern.split(";");
				String pluginId = pair[0];
				String pattern = pair[1];

				ArtifactReader<Path, Set<Node.Op>> reader = null;
				for (ArtifactReader<Path, Set<Node.Op>> tempReader : this.readers) {
					if (tempReader.getPluginId().equals(pluginId)) {
						reader = tempReader;
						break;
					}
				}
				if (reader != null)
					this.adapterMappings.add(new Mapping(pattern, reader));
			}
		} catch (IOException e) {
			throw new EccoException("Error creating or reading adapters file.", e);
		}
	}


	public Set<String> getIgnorePatterns() {
		return Collections.unmodifiableSet(this.ignorePatterns);
	}

	public void addIgnorePattern(String ignorePattern) {
		this.ignorePatterns.add(ignorePattern);
	}

	public void removeIgnorePattern(String ignorePattern) {
		this.ignorePatterns.remove(ignorePattern);
	}

	private boolean isIgnored(Path path) {
		for (String ignorePattern : this.ignorePatterns) {
			PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:" + ignorePattern);
			if (pm.matches(path))
				return true;
		}
		return false;
	}


	/**
	 * @param file The file to be read.
	 * @return The reader best suited for reading the file.
	 */
	private ArtifactReader<Path, Set<Node.Op>> getReaderForFile(Path base, Path file) {
		// pick the first artifact reader whose glob matches the file
		for (Mapping mapping : this.adapterMappings) {
			PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + mapping.getPattern());
			if (pathMatcher.matches(file)) {
				return mapping.getReader();
			}
		}
		return null;

//		ArtifactReader<Path, Set<Node.Op>> currentReader = null;
//		for (ArtifactReader<Path, Set<Node.Op>> reader : this.readers) {
//			if (reader.canRead(base.resolve(file)) && (currentReader == null || currentReader.getTypeHierarchy().length < reader.getTypeHierarchy().length))
//				currentReader = reader;
//		}
//		return currentReader;
	}


	public Set<Node.Op> readSpecificFiles(Path[] input) {
		return this.readSpecificFiles(Paths.get("."), input);
	}

	public Set<Node.Op> readSpecificFiles(Path base, Path[] input) {
		long startTime = System.currentTimeMillis();

		// for every file in paths add all parent directories and parse the file using the appropriate plugin

		// map of directories
		Map<Path, Node.Op> directoryNodes = new HashMap<>();

		// for every file
		for (Path path : input) {
			if (path.isAbsolute())
				throw new EccoException("Path must be relative to base directory.");

			// recursively check if its parents are already contained in the directory map, if not add them and link them
			Path parent = path.getParent();
			if (parent == null)
				parent = Paths.get("");
			Node.Op parentNode = this.createParents(base, parent, directoryNodes);

			// create node for file itself
			if (Files.isDirectory(base.resolve(path))) {
				//Path relative = base.relativize(path);
				Path relative = path;
				Artifact.Op<?> directoryArtifact = this.entityFactory.createArtifact(new DirectoryArtifactData(relative.getFileName()));
				Node.Op directoryNode = this.entityFactory.createNode(directoryArtifact);
				directoryNodes.put(relative, directoryNode);
				parentNode.addChild(directoryNode);
			} else {
				ArtifactReader<Path, Set<Node.Op>> reader = this.getReaderForFile(base, path);
				if (reader == null)
					throw new EccoException("No reader found for file " + path);
				//long localStartTime = System.currentTimeMillis();
				Set<Node.Op> nodes = reader.read(base, new Path[]{path});
				//LOGGER.info(reader.getClass() + ".read(): " + (System.currentTimeMillis() - localStartTime) + "ms");
				if (!nodes.isEmpty()) {
					for (Node.Op node : nodes) {
						parentNode.addChild(node);
					}
				}
			}
		}

		// return set of nodes containing only the node representing the base directory
		Set<Node.Op> nodes = new HashSet<>();
		nodes.add(directoryNodes.get(Paths.get("")));

		LOGGER.info(this.getClass() + ".readSpecificFiles(): " + (System.currentTimeMillis() - startTime) + "ms");

		return nodes;
	}

	private Node.Op createParents(Path base, Path path, Map<Path, Node.Op> directoryNodes) {
		// make sure that path is a directory
		if (!Files.isDirectory(base.resolve(path)))
			throw new EccoException("Expected a directory: " + path);

		// check if path is already contained in directory nodes
		Path relative = path;
		Node.Op node = directoryNodes.get(relative);
		if (node != null) { // if it is we are done
			return node;
		}

		// if it is not we create and add it
		Artifact.Op<?> directoryArtifact = this.entityFactory.createArtifact(new DirectoryArtifactData(relative.getFileName()));
		Node.Op directoryNode = this.entityFactory.createNode(directoryArtifact);
		directoryNodes.put(relative, directoryNode);

		// if the current path is still below the base directory
		if (!relative.equals(Paths.get(""))) {
			// proceed recursively with its parent and add it as a child to that parent
			Node.Op parent = this.createParents(base, path.getParent(), directoryNodes);
			parent.addChild(directoryNode);
		}

		// finally we return the current directory node
		return directoryNode;
	}


	@Override
	public Set<Node.Op> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		if (!Files.exists(base)) {
			throw new EccoException("Base directory does not exist.");
		} else if (!Files.isDirectory(base)) {
			throw new EccoException("Current base directory is not a directory but a file.");
		}

		long startTime = System.currentTimeMillis();

		Set<Node.Op> nodes = new HashSet<>();

		base = base.normalize();

		for (Path path : input) {

			// read file hashes if they exist
			Properties hashes = new Properties();
			Path hashesFile = base.resolve(EccoService.HASHES_FILE_NAME);
			if (Files.exists(hashesFile)) {
				try (Reader reader = Files.newBufferedReader(hashesFile)) {
					hashes.load(reader);
				} catch (IOException e) {
					throw new EccoException("Error reading hashes file.", e);
				}
			}

			Map<ArtifactReader<Path, Set<Node.Op>>, ArrayList<Path>> readerToFilesMap = new HashMap<>();
			Map<ArtifactReader<Path, Set<Node.Op>>, ArrayList<Path>> readerToUnmodifiedFilesMap = new HashMap<>();

			// this reader itself is responsible for the directory tree structure (unless there is an adapter that deals with a directory)
			Map<Path, Node.Op> directoryNodes = new HashMap<>();
			Node.Op baseDirectoryNode = this.readDirectories(base, base.resolve(path), hashes, readerToFilesMap, readerToUnmodifiedFilesMap, directoryNodes);
			nodes.add(baseDirectoryNode);

			// let readers read the assigned, modified files
			for (ArtifactReader<Path, Set<Node.Op>> reader : this.readers) {
				ArrayList<Path> filesList = readerToFilesMap.get(reader);

				if (filesList != null) {
					Path[] pluginInput = filesList.toArray(new Path[0]);

					long localStartTime = System.currentTimeMillis();
					Set<Node.Op> pluginNodes = reader.read(base, pluginInput);
					LOGGER.info(reader.getClass() + ".read(): " + (System.currentTimeMillis() - localStartTime) + "ms");
					for (Node.Op pluginNode : pluginNodes) {
						if (!(pluginNode.getArtifact().getData() instanceof PluginArtifactData))
							throw new EccoException("Plugin must return valid plugin nodes as root nodes in order for it to be compatible with dispatchers.");

						PluginArtifactData pluginArtifactData = (PluginArtifactData) pluginNode.getArtifact().getData();
						Path parent = pluginArtifactData.getPath().getParent();
						if (parent == null)
							parent = Paths.get(".").normalize();
						Node.Op parentNode = directoryNodes.get(parent);
						if (parentNode != null)
							parentNode.addChild(pluginNode);
						else
							throw new EccoException("Plugin '" + pluginArtifactData.getPluginId() + "' returned an invalid plugin node: " + pluginNode);
					}
				}
			}

			// deal with unmodified files
			for (ArtifactReader<Path, Set<Node.Op>> reader : this.readers) {
				ArrayList<Path> unmodifiedFilesList = readerToUnmodifiedFilesMap.get(reader);

				if (unmodifiedFilesList != null) {
					for (Path unmodifiedFilePath : unmodifiedFilesList) {
						Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), unmodifiedFilePath));
						Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
						pluginArtifact.putProperty(Artifact.PROPERTY_UNMODIFIED, true);

						PluginArtifactData pluginArtifactData = (PluginArtifactData) pluginNode.getArtifact().getData();
						Path parent = pluginArtifactData.getPath().getParent();
						if (parent == null)
							parent = Paths.get(".").normalize();
						Node.Op parentNode = directoryNodes.get(parent);
						if (parentNode != null)
							parentNode.addChild(pluginNode);
						else
							throw new EccoException("Plugin '" + this.getPluginId() + "' returned an invalid plugin node: " + pluginNode);
					}
				}
			}

		}

		LOGGER.info(this.getClass() + ".read(): " + (System.currentTimeMillis() - startTime) + "ms");

		// return produced nodes
		return nodes;
	}

	private Node.Op readDirectories(Path base, Path current, Properties hashes, Map<ArtifactReader<Path, Set<Node.Op>>, ArrayList<Path>> readerToFilesMap, Map<ArtifactReader<Path, Set<Node.Op>>, ArrayList<Path>> readerToUnmodifiedFilesMap, Map<Path, Node.Op> directoryNodes) {
		Path relativeCurrent = base.relativize(current);

		try {
			if (Files.isDirectory(current)) { // deal with directories
				if (!this.isIgnored(relativeCurrent)) { // if directory is not ignored add it to directories
					Artifact.Op<?> directoryArtifact = this.entityFactory.createArtifact(new DirectoryArtifactData(relativeCurrent.getFileName()));
					Node.Op directoryNode = this.entityFactory.createNode(directoryArtifact);
					directoryNodes.put(relativeCurrent, directoryNode);

					this.fireReadEvent(base.relativize(current), this);

					// go into sub directories
					try (Stream<Path> filesStream = Files.list(current)) {
						filesStream.forEach(d -> {
							Node.Op child = this.readDirectories(base, d, hashes, readerToFilesMap, readerToUnmodifiedFilesMap, directoryNodes);
							if (child != null)
								directoryNode.addChild(child);
						});
					}

					return directoryNode;
				}
			} else { // deal with files
				if (!this.isIgnored(relativeCurrent)) { // if file is not ignored add it to readerToFilesMap
					Map<ArtifactReader<Path, Set<Node.Op>>, ArrayList<Path>> filesMap;

					// get reader for file
					ArtifactReader<Path, Set<Node.Op>> reader = this.getReaderForFile(base, relativeCurrent);

//					// check if file was modified
//					String hash = hashes.getProperty(relativeCurrent.toString());
//					if (hash != null && hash.equals(EccoUtil.getSHA(base.resolve(relativeCurrent)))) { // hashes match
//						filesMap = readerToUnmodifiedFilesMap;
//					} else {
//						filesMap = readerToFilesMap;
//					}
					// TODO: for now, always read all the files.
					filesMap = readerToFilesMap;

					// assign file to reader
					if (reader != null) {
						ArrayList<Path> fileList = filesMap.get(reader);
						if (fileList == null)
							fileList = new ArrayList<>();
						fileList.add(relativeCurrent);
						filesMap.put(reader, fileList);

						this.fireReadEvent(relativeCurrent, reader);

//						// add artifact plugin node
//						Artifact<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(reader.getPluginId(), relativeCurrent));
//						Node pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
//						directoryNodes.put(relativeCurrent, pluginNode);
//
//						return pluginNode;
					}

					return null;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}


	private Collection<ReadListener> listeners = new ArrayList<>();

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

}
