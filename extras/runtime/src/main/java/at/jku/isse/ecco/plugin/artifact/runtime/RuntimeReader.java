package at.jku.isse.ecco.plugin.artifact.runtime;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.DispatchReader;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.artifact.java.JDTArtifactData;
import at.jku.isse.ecco.plugin.artifact.java.JavaReader;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/*
	Reader opens CONFIGFILE (.settings.onfig) and reads IP and port to connect to, example: "conn=127.0.0.1:8001"

	The program to be observed needs to be started manually using the parameters
	"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8001,suspend=y -Djava.compiler=NONE". 
	The parameter "address" determines the IP address and the corresponding port.

 */

public class RuntimeReader implements ArtifactReader<Path, Set<Node>> {

	private final EntityFactory entityFactory;
//	private final String CONFIGFILE = ".settings.config";

	private JavaReader javaReader;
	private DispatchReader dispatchReader;

	@Inject
	public RuntimeReader(EntityFactory entityFactory, JavaReader javaReader) {
		com.google.common.base.Preconditions.checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
		this.javaReader = javaReader;

		Set<ArtifactReader<Path, Set<Node>>> readers = new HashSet<>();
		readers.add(javaReader);

		this.dispatchReader = new DispatchReader(entityFactory, readers);
	}

	@Override
	public String getPluginId() {
		return RuntimePlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	@Override
	public boolean canRead(Path path) {
		return (!Files.isDirectory(path) && path.getFileName().toString().endsWith("settings.config"));
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {
		if (input == null || input.length == 0) {
			return null;
		}
		String connectionString = getConnectionString(base, input[0]);
		if (connectionString == null) {
			return null;
		}

		Path sourceDir = getSourceFolder(base, input[0]);
		ArrayList files = new ArrayList();
		listJavaFiles(sourceDir, sourceDir, files);


		Set<Node> nodes = new HashSet<>();

		Set<Node> dispatchNodes = dispatchReader.read(sourceDir, new Path[]{Paths.get("")});
		nodes.addAll(dispatchNodes);

//		Artifact sourceDirectoryArtifact = entityFactory.createArtifact(new DirectoryArtifactData(Paths.get("runtime")));
//		Node directoryNode = entityFactory.createNode(sourceDirectoryArtifact);
//		directoryNode.getChildren().addAll(dispatchNodes);
//		nodes.add(directoryNode);


//		Path[] javafiles = (Path[]) files.toArray(new Path[0]);
//		Set<Node> javanodes = javaReader.read(sourceDir, javafiles);
		Set<Node> javanodes = new HashSet<>();
		for (Node node : nodes) {
			this.getAllJavaPluginNodesInTree(node, javanodes);
		}

		List<RuntimeArtifactData> listRad = new ArrayList<RuntimeArtifactData>();
		VMObservation deb = new VMObservation();
		deb.connect(connectionString);
		deb.initializeObservation();
		System.out.println("Observation started ...");
		for (int i = 0; ; i++) {
			List<RuntimeArtifactData> tempListRad = deb.manageNextEvent();
			if (tempListRad == null || tempListRad.size() == 0) {
				break;
			}

			listRad.addAll(tempListRad);
		}

		ArrayList list = new ArrayList<Node>(javanodes);
		setExecuted(new HashSet<RuntimeArtifactData>(listRad), list);
		trimTree(list);

		// add nodes for .settings.config
		Artifact<PluginArtifactData> configArtifact = entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), Paths.get(input[0].toString())));
		Node configNode = entityFactory.createOrderedNode(configArtifact);
		//list.add(configNode);
		nodes.add(configNode);

		Artifact<ConfigArtifactData> connArtifact = entityFactory.createArtifact(new ConfigArtifactData("conn", connectionString));
		Node connNode = entityFactory.createNode(connArtifact);
		configNode.addChild(connNode);

		Artifact<ConfigArtifactData> sourceArtifact = entityFactory.createArtifact(new ConfigArtifactData("source", sourceDir.toString()));
		Node sourceNode = entityFactory.createNode(sourceArtifact);
		configNode.addChild(sourceNode);

		System.out.println("Observation ended.");
		//return new HashSet<Node>(list);
		return nodes;
	}

	private void getAllJavaPluginNodesInTree(Node node, Set<Node> javaNodes) {
		if (node != null && node.getArtifact() != null && node.getArtifact().getData() != null && node.getArtifact().getData() instanceof PluginArtifactData && ((PluginArtifactData) node.getArtifact().getData()).getPluginId().equals(this.javaReader.getPluginId())) {
			javaNodes.add(node);
		}
		for (Node child : node.getChildren()) {
			this.getAllJavaPluginNodesInTree(child, javaNodes);
		}
	}

	private void setUses(Node node) {
		if (node == null) return;
		ArtifactData ad = node.getArtifact().getData();
		if (ad instanceof JDTArtifactData) {
			((JDTArtifactData) ad).setExecuted();
		}

		List<ArtifactReference> listArt = node.getArtifact().getUses();
		for (ArtifactReference tempArt : listArt) {
			ad = tempArt.getTarget().getData();
			if (ad instanceof JDTArtifactData) {
				((JDTArtifactData) ad).setExecuted();
			}
			Node nextNode = tempArt.getTarget().getContainingNode();
			while (nextNode != null) {
				ad = nextNode.getArtifact().getData();
				if (ad instanceof JDTArtifactData) {
					((JDTArtifactData) ad).setExecuted();
				}
				nextNode = nextNode.getParent();
			}
		}
	}

	private void setUsesAtomic(Node node) {
		if (node == null) return;
		setUses(node);
		Node tempNode = node.getParent();
		setUsesAtomic(tempNode);
	}

	private void setExecuted(Set<RuntimeArtifactData> listRad, List<Node> javanodes) {
		if (listRad == null || javanodes == null) return;

		for (Node tempJavaNode : javanodes) {
			JDTArtifactData jdtart = null;
			for (RuntimeArtifactData rad : listRad) {
				try {
					if (tempJavaNode.getArtifact().getData() instanceof JDTArtifactData) {
						jdtart = (JDTArtifactData) tempJavaNode.getArtifact().getData();
						Optional<Integer> opt = tempJavaNode.getArtifact().getProperty("line");

						if (jdtart.getFile().equals(rad.getFile()) &&
								opt.isPresent() && opt.get().intValue() == rad.getLineNumber()) {
							jdtart.setExecuted();

							Node nextNode = tempJavaNode.getParent();
							while (nextNode != null) {
								ArtifactData ad = nextNode.getArtifact().getData();
								if (ad instanceof JDTArtifactData) {
									((JDTArtifactData) ad).setExecuted();
								}
								nextNode = nextNode.getParent();
							}
							setUsesAtomic(tempJavaNode);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			try {
				setExecuted(listRad, tempJavaNode.getChildren());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void trimTree(List<Node> javanodes) {
		if (javanodes == null) return;

		Iterator i = javanodes.iterator();
		while (i.hasNext()) {
			Node tempJavaNode = (Node) i.next();
			JDTArtifactData jdtart = null, jdtartParent = null;
			try {
				if (tempJavaNode.getArtifact().getData() instanceof JDTArtifactData &&
						tempJavaNode.getParent().getArtifact().getData() instanceof JDTArtifactData) {
					jdtart = (JDTArtifactData) tempJavaNode.getArtifact().getData();
					jdtartParent = (JDTArtifactData) tempJavaNode.getParent().getArtifact().getData();
					if (!jdtart.isExecuted() && jdtartParent.getType().equals("org.eclipse.jdt.core.dom.ChildListPropertyDescriptor")) {
						List<ArtifactReference> usedBy = tempJavaNode.getArtifact().getUsedBy();
						CopyOnWriteArrayList<ArtifactReference> usedByConcur = new CopyOnWriteArrayList(usedBy);
						ArtifactReference ar = null;
						for (int j = 0; j < usedByConcur.size(); j++) {
							ar = usedByConcur.get(j);
							ar.getSource().getUses().remove(ar);
							ar.getSource().getUsedBy().remove(ar);
							ar.getTarget().getUses().remove(ar);
							ar.getTarget().getUsedBy().remove(ar);
						}
						i.remove();
						tempJavaNode.setParent(null);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				trimTree(tempJavaNode.getChildren());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private String getConnectionString(Path base, Path input) {
		Path resolvedPath = base.resolve(input);
		try (Stream<String> lines = Files.lines(resolvedPath)) {
			Iterator<String> it = lines.iterator();
			if (it.hasNext()) {
				String line = it.next();
				line = line.trim();

				if (line.toLowerCase().startsWith("conn=")) {
					return line.substring(5).trim();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private Path getSourceFolder(Path base, Path input) {
		Path resolvedPath = base.resolve(input);
		try (Stream<String> lines = Files.lines(resolvedPath)) {
			Iterator<String> it = lines.iterator();
			while (it.hasNext()) {
				String line = it.next();
				line = line.trim();
				if (line.toLowerCase().startsWith("source=")) {
					return Paths.get(line.substring(7).trim());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void listJavaFiles(Path base, Path directoryName, ArrayList<Path> files) {
		File directory = directoryName.toFile();

		// get all the files from a directory
		File[] fList = directory.listFiles();
		for (File file : fList) {
			if (file.isFile()) {
				if (file.getName().endsWith(".java")) {
					if (directoryName.getNameCount() > base.getNameCount()) {
						files.add(directoryName.subpath(base.getNameCount(), directoryName.getNameCount()).resolve(Paths.get(file.getName())));
					} else {
						files.add(Paths.get(file.getName()));
					}
				}

			} else if (file.isDirectory()) {
				listJavaFiles(base, Paths.get(file.getAbsolutePath()), files);
			}
		}
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

}
