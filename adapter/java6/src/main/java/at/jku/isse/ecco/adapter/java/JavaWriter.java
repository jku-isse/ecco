package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JavaWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return JavaPlugin.class.getName();
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<>();
		Map<Path, List<ASTNode>> fileMap = new HashMap<>();

		List<ASTNode> astNodes = buildAST(input, fileMap);

		for (Map.Entry<Path, List<ASTNode>> entry : fileMap.entrySet()) {
			output.add(entry.getKey());

			Path src = base.resolve(entry.getKey());
			try {
//				// create directories
//				Files.createDirectories(src.getParent());

				// create code string
				StringWriter writer = new StringWriter();
				for (ASTNode n : entry.getValue()) {
					writer.append(n.toString());
				}
				writer.flush();
				writer.close();

				String code = writer.toString();

				// format code string
				CodeFormatter cf = new DefaultCodeFormatter();
				TextEdit te = cf.format(CodeFormatter.K_COMPILATION_UNIT, code, 0, code.length(), 0, null);
				IDocument dc = new Document(code);
				try {
					te.apply(dc);
					code = dc.get();
				} catch (MalformedTreeException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (BadLocationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// write to file
				FileWriter fileWriter = new FileWriter(src.toFile());
				fileWriter.write(code);
				fileWriter.flush();
				fileWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		return output.toArray(new Path[output.size()]);
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}


	private Collection<WriteListener> listeners = new ArrayList<>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}


	public List<ASTNode> buildAST(Collection<? extends Node> nodes, Map<Path, List<ASTNode>> fileMap) {
		List<ASTNode> astNodes = new LinkedList<>();
		AST ast = AST.newAST(AST.JLS8);
		for (Node n : nodes) {
			if (n instanceof RootNode) { // when node is root node (i.e. contains no artifact) process its children
				if (n.getChildren() != null) {
					astNodes.addAll(buildAST(n.getChildren(), fileMap));
				}
			} else if (n.getArtifact() != null && n.getArtifact().getData() instanceof PluginArtifactData) { // when node is plugin node process its children
				PluginArtifactData pluginArtifactData = (PluginArtifactData) n.getArtifact().getData();
				if (n.getChildren() != null) {
					List<ASTNode> tempAstNodes = buildAST(n.getChildren(), fileMap);

					List<ASTNode> tempList = fileMap.get(pluginArtifactData.getPath());
					if (tempList == null) {
						fileMap.put(pluginArtifactData.getPath(), tempAstNodes);
					} else {
						tempList.addAll(tempAstNodes);
					}

					astNodes.addAll(tempAstNodes);
				}
			} else { // when node is a java ast node
				ASTNode astNode = buildAST(n, ast);
				if (astNode != null) {
					astNodes.add(astNode);
				}
			}
		}
		return astNodes;
	}

	private ASTNode buildAST(Node node, AST ast) {
		return buildAST(node, ast, null);
	}

	private ASTNode buildAST(Node node, AST ast, ASTNode parent) {
		if (node.getArtifact() == null) {
			return null;
		}

		ArtifactData artifactData = node.getArtifact().getData();

		ASTNode astNode = null;
		if (artifactData instanceof JDTNodeArtifactData) {
			astNode = generateAstNode((JDTNodeArtifactData) artifactData, ast);
			if (astNode != null) {
//				astNode.setProperty(SOURCE_FILE, ((JDTArtifactData) artifactData).getSource());

				for (Node child : node.getChildren()) {
					buildAST(child, ast, astNode);
				}
				if (node.getChildren().isEmpty() && astNode instanceof SwitchCase)
					((SwitchCase) astNode).setExpression(null);
			}
		} else if (artifactData instanceof JDTPropertyArtifactData) {
			JDTPropertyArtifactData propertyArtifactData = (JDTPropertyArtifactData) artifactData;

			String type = propertyArtifactData.getType();
			switch (type) {
				case "org.eclipse.jdt.core.dom.ChildListPropertyDescriptor":
					StructuralPropertyDescriptor desc = getStructuralProperty(parent, propertyArtifactData.getIdentifier());
					if (desc != null) {
						for (Node child : node.getChildren()) {
							ASTNode childNode = buildAST(child, ast, astNode);
							List<Object> children = (List<Object>) parent.getStructuralProperty(desc);
							children.add(childNode);
						}
					}
					break;
				case "org.eclipse.jdt.core.dom.ChildPropertyDescriptor":
					desc = getStructuralProperty(parent, propertyArtifactData.getIdentifier());
					if (desc != null) {
						for (Node child : node.getChildren()) {
							ASTNode childNode = buildAST(child, ast, astNode);
							parent.setStructuralProperty(desc, childNode);
						}
					}
					break;
				case "org.eclipse.jdt.core.dom.SimplePropertyDescriptor":
					desc = getStructuralProperty(parent, propertyArtifactData.getIdentifier());
					if (desc != null) {
						for (Node child : node.getChildren()) {

							JDTArtifactData childArtifactData = (JDTArtifactData) child.getArtifact().getData();

							String primitivType = childArtifactData.getType();
							switch (primitivType) {
								case "java.lang.Boolean":
									parent.setStructuralProperty(desc, Boolean.parseBoolean(childArtifactData.getIdentifier()));
									break;
								case "java.lang.Byte":
									parent.setStructuralProperty(desc, Byte.parseByte(childArtifactData.getIdentifier()));
									break;
								case "java.lang.Short":
									parent.setStructuralProperty(desc, Short.parseShort(childArtifactData.getIdentifier()));
									break;
								case "java.lang.Integer":
									parent.setStructuralProperty(desc, Integer.parseInt(childArtifactData.getIdentifier()));
									break;
								case "java.lang.Long":
									parent.setStructuralProperty(desc, Long.parseLong(childArtifactData.getIdentifier()));
									break;
								case "java.lang.Float":
									parent.setStructuralProperty(desc, Float.parseFloat(childArtifactData.getIdentifier()));
									break;
								case "java.lang.Double":
									parent.setStructuralProperty(desc, Double.parseDouble(childArtifactData.getIdentifier()));
									break;
								case "java.lang.Character":
									parent.setStructuralProperty(desc, childArtifactData.getIdentifier().charAt(0));
									break;
								case "java.lang.String":
									parent.setStructuralProperty(desc, childArtifactData.getIdentifier());
									break;
								//JDT specific types
								case "org.eclipse.jdt.core.dom.InfixExpression$Operator":
									parent.setStructuralProperty(desc, InfixExpression.Operator.toOperator(childArtifactData.getIdentifier()));
									break;
								case "org.eclipse.jdt.core.dom.Modifier$ModifierKeyword":
									parent.setStructuralProperty(desc, Modifier.ModifierKeyword.toKeyword(childArtifactData.getIdentifier()));
									break;
								case "org.eclipse.jdt.core.dom.PrimitiveType$Code":
									parent.setStructuralProperty(desc, PrimitiveType.toCode(childArtifactData.getIdentifier()));
									break;
								case "org.eclipse.jdt.core.dom.Assignment$Operator":
									parent.setStructuralProperty(desc, Assignment.Operator.toOperator(childArtifactData.getIdentifier()));
									break;
								case "org.eclipse.jdt.core.dom.PrefixExpression$Operator":
									parent.setStructuralProperty(desc, PrefixExpression.Operator.toOperator(childArtifactData.getIdentifier()));
									break;
								case "org.eclipse.jdt.core.dom.PostfixExpression$Operator":
									parent.setStructuralProperty(desc, PostfixExpression.Operator.toOperator(childArtifactData.getIdentifier()));
									break;

								default:
									System.err.println("ERROR: Type " + primitivType + " is not handled!");
							}
						}
					}
					break;
				default:
					return null;
			}
			astNode = parent;
		} else {
			throw new IllegalArgumentException("Writer works only for JDTArtifacts! (" + artifactData.getClass() + ")");
		}

		return astNode;
	}

	private ASTNode generateAstNode(JDTNodeArtifactData artifactData, AST ast) {
		if (artifactData == null) {
			return null;
		}
		String[] split = artifactData.getType().split(":");
		if (split.length < 2) {
			return null;
		}
		int nodeType = Integer.parseInt(split[1]);
		ASTNode astNode = ast.createInstance(nodeType);
		if (astNode instanceof ArrayType) {
			((ArrayType) astNode).dimensions().clear();
		}
		return astNode;
	}

	private Node findNode(List<? extends Node> path, Collection<? extends Node> in) {
		for (Node n : in) {
			Node ret = findNode(path, n);
			if (ret != null) {
				return ret;
			}
		}
		return null;
	}

	private Node findNode(List<? extends Node> path, Node in) {
		if (path.isEmpty()) {
			return in.getParent();
		}
		Node n = path.get(0);
		if (n.equals(in)) {
			if (path.size() == 1) {
				return in;
			} else {
				return findNode(path.subList(1, path.size()), in.getChildren());
			}
		}
		return null;
	}

	private StructuralPropertyDescriptor getStructuralProperty(ASTNode astNode, String name) {
		List<StructuralPropertyDescriptor> stucture = astNode.structuralPropertiesForType();
		for (StructuralPropertyDescriptor desc : stucture) {
			if (desc.getId().equals(name)) {
				return desc;
			}
		}
		return null;
	}

}
