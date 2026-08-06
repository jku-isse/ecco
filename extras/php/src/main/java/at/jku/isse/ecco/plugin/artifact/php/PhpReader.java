package at.jku.isse.ecco.plugin.artifact.php;

import at.ac.tuwien.infosys.www.phpparser.ParseNode;
import at.ac.tuwien.infosys.www.phpparser.ParseTree;
import at.ac.tuwien.infosys.www.phpparser.PhpLexer;
import at.ac.tuwien.infosys.www.phpparser.PhpParser;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Timea Kovacs
 */
public class PhpReader implements ArtifactReader<Path, Set<Node>> {

	private final EntityFactory entityFactory;

	private static String[] statements = new String[]{"parameter_list", "class_statement", "top_statement", "statement", "case_list"};
	private static String[] skippedStatements = new String[]{"T_OPEN_CURLY_BRACES", "T_CLOSE_CURLY_BRACES", "epsilon"};
	private static String[] skipLabel = new String[]{"epsilon", "T_OPEN_CURLY_BRACES", "T_CLOSE_CURLY_BRACES", "T_COMMA"};
	private static String[] ifStatements = new String[]{"T_IF", "else_single", "elseif_list"};

	private static String[] needsBraces = new String[]{"T_SWITCH", "T_ELSEIF", "T_ELSE", "T_IF", "T_FOR", "T_FOREACH"};

	public static String[] getSkipLabel() {
		return skipLabel;
	}

	public static void setSkipLabel(String[] skipLabel) {
		PhpReader.skipLabel = skipLabel;
	}

	@Inject
	public PhpReader(EntityFactory entityFactory) {
		com.google.common.base.Preconditions.checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return PhpPlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{"php"};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	@Override
	public boolean canRead(Path path) {
		// TODO: actually check if file is a php file
		String lowerCaseFileName = path.getFileName().toString().toLowerCase();
		if (!Files.isDirectory(path) && Files.isRegularFile(path) && lowerCaseFileName.endsWith(".php"))
			return true;
		else
			return false;
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {
		Set<Node> nodes = new HashSet<Node>();
		for (Path path : input) {
			Path resolvedPath = base.resolve(path);
			Artifact<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
			Node pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);

			try {
				FileReader fr = new FileReader(resolvedPath.toFile());
				PhpLexer pl = new PhpLexer(fr);
				pl.setFileName(resolvedPath.getFileName().toString());
				PhpParser parser = new PhpParser(pl);
				// obtain a parsed tree using external library
				ParseNode rootNode = (ParseNode) parser.parse().value;
				ParseTree parseTree = new ParseTree(rootNode);

				constructMyTree(pluginNode, parseTree);
				nodes.add(pluginNode);
			} catch (FileNotFoundException e) {
				System.err.println("File not found: " + e.getMessage());
				System.exit(1);
			} catch (Exception e) {
				System.err.println("Error parsing " + path.getFileName());
				System.err.println(e.getMessage());
				e.printStackTrace();
				System.exit(1);
			}

		}
		return nodes;
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	private void constructMyTree(Node fileNode, ParseTree parseTree) {
		constructorHelper(fileNode, fileNode, parseTree.getRoot());
	}

	private void constructorHelper(Node baseNode, Node myNode, ParseNode parsedNode) {

		// if we've reached a leaf
		if (parsedNode.isToken() && !(Arrays.asList(getSkipLabel()).contains(parsedNode.getName()))) {
			((PhpArtifactData) myNode.getArtifact().getData()).concatToValue(parsedNode.getLexeme());
		}

		// if new potential node detected 
		// create new and add as children to the current node
		if (checkIfNewNode(parsedNode)
				|| Arrays.asList(getIfStatements()).contains(parsedNode.getName())
				|| parsedNode.getName() == "case_list") {
			PhpArtifactData newArtifactData = createArtifact(parsedNode);
			Node newNode = entityFactory.createNode(entityFactory.createArtifact(newArtifactData));
			if (Arrays.asList(getIfStatements()).contains(parsedNode.getName())
					|| parsedNode.getName() == "case_list") {
				baseNode.addChild(newNode);
			} else {
				myNode.addChild(newNode);
			}
			myNode = newNode;
		}

		// handle successors
		for (ParseNode child : parsedNode.getChildren()) {
			if (child.getName() == "parameter_list") {
				PhpArtifactData newArtifact = createArtifact(child);
				Node paramNode = entityFactory.createNode(entityFactory.createArtifact(newArtifact));
				myNode.addChild(paramNode);
				((PhpArtifactData) paramNode.getArtifact().getData()).concatToValue("-parameters-");
				constructParamList(paramNode, paramNode, child);
				//fix mixed up order due to recursivity
				Collections.reverse(paramNode.getChildren());
			} else if (child.getName() == "switch_case_list") {
				constructorHelper(myNode, myNode, child);
				//fix mixed up order due to recursivity
				Collections.reverse(myNode.getChildren());
			} else if (child.getNumChildren() > 0 && child.getChild(0).getName() == "T_IF") {
				//initial node, parent for all if-related and connected nodes
				PhpArtifactData newArtifactData = createIfBaseArtifact();
				Node ifNode = entityFactory.createNode(entityFactory.createArtifact(newArtifactData));
				((PhpArtifactData) ifNode.getArtifact().getData()).concatToValue("-if-");
				myNode.getParent().removeChild(myNode);
				myNode.getParent().addChild(ifNode);
				ifNode.addChild(myNode);
				constructorHelper(ifNode, myNode, child);
				myNode = ifNode;
				//fix mixed up order due to recursivity
				orderTheIfChildren(ifNode.getChildren());
				//remove initial node so that its children are connected directly to upper level
				skipIfNode(ifNode);
			} else if (checkIfChildPotentialNode(child)) {
				constructorHelper(baseNode, myNode, child);
			}
		}
	}

	private void skipIfNode(Node ifNode) {
		Node parent = ifNode.getParent();
		for (Node child : ifNode.getChildren()) {
			parent.addChild(child);
		}
		parent.removeChild(ifNode);
	}

	private void orderTheIfChildren(List<Node> allChildren) {
		List<Node> correctOrder = new ArrayList<Node>(allChildren);
		allChildren.clear();
		allChildren.add(correctOrder.remove(0));
		Node elseNode = null;
		if (((PhpArtifactData) correctOrder.get(correctOrder.size() - 1).getArtifact().getData()).getValue().startsWith("else")) {
			elseNode = correctOrder.remove(correctOrder.size() - 1);
		}
		for (int i = correctOrder.size() - 1; i >= 0; i--)
			if (((PhpArtifactData) correctOrder.get(i).getArtifact().getData()).getValue() != "") {
				allChildren.add(correctOrder.get(i));
			}
		if (elseNode != null) {
			allChildren.add(elseNode);
		}
	}

	private void constructParamList(Node baseNode, Node myNode, ParseNode parsedNode) {
		if (parsedNode.isToken() && !(Arrays.asList(getSkipLabel()).contains(parsedNode.getName()))) {
			((PhpArtifactData) myNode.getArtifact().getData()).concatToValue(parsedNode.getLexeme());
		}
		// create new node
		if (parsedNode.getName() == "non_empty_parameter_list") {
			PhpArtifactData newArtifactData = new PhpArtifactData();
			Node newNode = entityFactory.createNode(entityFactory.createArtifact(newArtifactData));
			baseNode.addChild(newNode);
			myNode = newNode;
		}

		for (ParseNode child : parsedNode.getChildren()) {
			if (checkIfChildPotentialNode(child)) {
				constructParamList(baseNode, myNode, child);
			}
		}
	}

	private boolean checkIfChildPotentialNode(ParseNode child) {
		switch (child.getName()) {
			case "elseif_list":
			case "else_single":
			case "case_list":
				if (child.getChild(0).getName() == "epsilon")
					return false;
				break;
		}

		return true;
	}

	private boolean checkIfNewNode(ParseNode parsedNode) {
		if (parsedNode.getNumChildren() > 0 && parsedNode.getChild(0).getNumChildren() > 0) {
			return (Arrays.asList(getStatements()).contains(parsedNode.getName())
					&& !Arrays.asList(getSkippedStatements()).contains(parsedNode.getChild(0).getChild(0).getName()));
		} else
			return Arrays.asList(getStatements()).contains(parsedNode.getName());
	}

	// method for creating PHP artifact overwriting the toString method 
	private PhpArtifactData createArtifact(ParseNode parsedNode) {
		PhpArtifactData newArtifact;

		// having every children between brackets
		if (parsedNode.getName() == "elseif_list"
				|| (parsedNode.getName() == "top_statement" && !isFunctionNode(parsedNode))
				|| (parsedNode.getNumChildren() > 0 && (Arrays.asList(needsBraces).contains(parsedNode.getChild(0).getName())
				|| (parsedNode.getChild(0).getNumChildren() > 0
				&& Arrays.asList(needsBraces).contains(parsedNode.getChild(0).getChild(0).getName()))))) {
			newArtifact = new PhpArtifactData();
//			{
//				@Override
//				public String toString() {
//					String s = "\n" + this.getData() + " { ";
//					for (Node child : this.getContainingNode().getChildren()) {
//						s += " " + child.getArtifact().toString();
//					}
//					s += "\n }";
//					return s;
//				}
//			};
			newArtifact.setType(PhpArtifactData.Type.BLOCK);
			// for function type node place the parameters next to function declaration,
			// then, every other child between brackets
			// top_statement can be both class node or when missing, function node; an additional check is required
		} else if ((parsedNode.getName() == "top_statement" && isFunctionNode(parsedNode))
				|| parsedNode.getName() == "class_statement") {
			newArtifact = new PhpArtifactData();
//			{
//				@Override
//				public String toString() {
//					String s = "\n" + this.getData().replace("( )", "");
//					boolean parameters = true;
//					for (Node child : this.getContainingNode().getChildren()) {
//						if (parameters) {
//							s += " " + child.getArtifact().toString() + " \n{ ";
//							parameters = false;
//						} else {
//							s += child.getArtifact().toString();
//						}
//					}
//					s += "\n }";
//					return s;
//				}
//
//
//			};
			newArtifact.setType(PhpArtifactData.Type.FUNCTION_OR_CLASS);
			// deal with formating the parameters of a function
		} else if (parsedNode.getName() == "parameter_list") {
			newArtifact = new PhpArtifactData();
//			{
//				@Override
//				public String toString() {
//					String s = "(";
//					if (this.getContainingNode().getChildren().size() > 0) {
//						for (Node child : this.getContainingNode().getChildren()) {
//							s += child.getArtifact().getData() + ",";
//						}
//						s = s.substring(0, s.length() - 1);
//					}
//					return s + ")";
//				}
//			};
			newArtifact.setType(PhpArtifactData.Type.PARAMETERS);
		} else {
			newArtifact = new PhpArtifactData();
		}
		return newArtifact;
	}

	public boolean isFunctionNode(ParseNode parsedNode) {
		return (parsedNode.getNumChildren() > 0 && parsedNode.getChild(0).getNumChildren() > 0
				&& parsedNode.getChild(0).getChild(0).getNumChildren() > 0 &&
				parsedNode.getChild(0).getChild(0).getChild(0).getName().equals("T_FUNCTION"));
	}

	private PhpArtifactData createIfBaseArtifact() {
		PhpArtifactData newArtifact = new PhpArtifactData();
//		{
//			@Override
//			public String toString() {
//				String s = "";
//				for (Node child : this.getContainingNode().getChildren()) {
//					s += child.getArtifact().toString();
//				}
//				s += "\n ";
//				return s;
//			}
//		};
		newArtifact.setType(PhpArtifactData.Type.BASE);
		return newArtifact;
	}

	public static String[] getStatements() {
		return statements;
	}

	public static String[] getSkippedStatements() {
		return skippedStatements;
	}

	public static String[] getIfStatements() {
		return ifStatements;
	}

	public static String[] getNeedsBraces() {
		return needsBraces;
	}

	public static void setNeedsBraces(String[] needsBraces) {
		PhpReader.needsBraces = needsBraces;
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