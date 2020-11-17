package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class JavaBlockReader implements ArtifactReader<Path, Set<Node.Op>> {

	private final EntityFactory entityFactory;

	@Inject
	public JavaBlockReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return JavaPlugin.class.getName();
	}

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.java"});
	}

	@Override
	public Map<Integer, String[]> getPrioritizedPatterns() {
		return Collections.unmodifiableMap(prioritizedPatterns);
	}

	@Override
	public Set<Node.Op> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		Set<Node.Op> nodes = new HashSet<>();

		for (Path path : input) {
			Path resolvedPath = base.resolve(path);

			Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
			Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
			nodes.add(pluginNode);


			try {
				CompilationUnit cu = JavaParser.parse(resolvedPath);

				String packageName = "";
				if (cu.getPackageDeclaration().isPresent())
					packageName = cu.getPackageDeclaration().get().getName().toString();


				for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {

					String className = typeDeclaration.getName().toString();
					Artifact.Op<ClassArtifactData> classArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className));
					Node.Op classNode = this.entityFactory.createNode(classArtifact);
					pluginNode.addChild(classNode);

					//add classChild from imports
					for (ImportDeclaration importDeclaration : cu.getImports()) {
						String importName = "import " + importDeclaration.getName().asString();
						Artifact.Op<ImportArtifactData> importsArtifact = this.entityFactory.createArtifact(new ImportArtifactData(importName));
						Node.Op importNode = this.entityFactory.createNode(importsArtifact);
						classNode.addChild(importNode);
					}

					for (BodyDeclaration<?> node : typeDeclaration.getMembers()) {
						//get enums of a class
						if (node instanceof EnumDeclaration) {
							String enumName = ((EnumDeclaration) node).getName().toString();
							Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(enumName));
							Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
							classNode.addChild(lineNode);
							int entries = ((EnumDeclaration) node).getEntries().size();
							for (int i = 0; i < entries; i++) {
								String enumInitializerName = ((EnumDeclaration) node).getEntries().get(i).getName().toString();
								Artifact.Op<LineArtifactData> lineArtifactChild = this.entityFactory.createArtifact(new LineArtifactData(enumInitializerName));
								Node.Op lineNodeChild = this.entityFactory.createNode(lineArtifactChild);
								lineNode.addChild(lineNodeChild);
							}
						} else
							//add classChild from fields
							if (node instanceof FieldDeclaration) {
								addFieldChild(node, classNode);
							} else
								//add classChild from constructorMethod
								if (node instanceof ConstructorDeclaration) {
									String methodSignature = ((ConstructorDeclaration) node).getSignature().toString();
									Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
									Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
									classNode.addChild(methodNode);
									if (((ConstructorDeclaration) node).getBody().getStatements().size() > 0) {
										for (Statement stm : ((ConstructorDeclaration) node).getBody().getStatements()) {
											addMethodChild(stm, methodNode);
										}
									}
								}
					}

					//add classChild from Methods
					for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {
						String methodSignature = methodDeclaration.getSignature().toString();
						Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
						Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
						classNode.addChild(methodNode);
						Optional<BlockStmt> block = methodDeclaration.getBody();
						Boolean hasAnyStatement = false;
						if (methodDeclaration.getBody().isPresent()) {
							for (com.github.javaparser.ast.Node node : methodDeclaration.getBody().get().getChildNodes()) {
								if (node instanceof FieldDeclaration) {
									addFieldChild(node, methodNode);
								} else {
									addMethodChild(node, methodNode);
								}
							}
						}

					}
				}

			} catch (IOException e) {
				e.printStackTrace();
				throw new EccoException("Error parsing java file.", e);
			}

		}
		return nodes;
	}

	public void addFieldChild(com.github.javaparser.ast.Node node, Node.Op patternNode) {
		if (((FieldDeclaration) node).getVariables().get(0).getInitializer().isPresent()) {
			if (((FieldDeclaration) node).getVariables().get(0).getInitializer().get() instanceof ArrayInitializerExpr) {
				String stmt = "Array " + ((FieldDeclaration) node).getVariables().get(0).getName().toString();
				Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(stmt));
				Node.Op fieldNode = this.entityFactory.createOrderedNode(fieldArtifact);
				patternNode.addChild(fieldNode);
				for (com.github.javaparser.ast.Node childNode : ((FieldDeclaration) node).getVariables().get(0).getChildNodes().get(2).getChildNodes()) {
					String stmtChild = childNode.toString();
					Artifact.Op<LineArtifactData> lineArtifactChild = this.entityFactory.createArtifact(new LineArtifactData(stmtChild));
					Node.Op lineNodeChild = this.entityFactory.createNode(lineArtifactChild);
					fieldNode.addChild(lineNodeChild);
				}

			} else {
				if (((FieldDeclaration) node).getJavadocComment().isPresent()) {
					((FieldDeclaration) node).removeJavaDocComment();
				}
				if (node.getComment().isPresent()) {
					node.removeComment();
				}
				String stmtChild = node.toString();
				Artifact.Op<FieldArtifactData> fieldArtifactChild = this.entityFactory.createArtifact(new FieldArtifactData(stmtChild));
				Node.Op fieldNode = this.entityFactory.createNode(fieldArtifactChild);
				patternNode.addChild(fieldNode);
			}
		} else {

			String fieldOfClass = node.removeComment().toString();
			Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(fieldOfClass));
			Node.Op fieldNode = this.entityFactory.createNode(fieldArtifact);
			patternNode.addChild(fieldNode);
		}
	}


	public void addMethodChild(com.github.javaparser.ast.Node node, Node.Op methodNode) {
		if (node instanceof IfStmt) {
			String stmt = "if (" + node.getChildNodes().get(0).toString() + ")";
			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
			Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);

			if (((IfStmt) node).getThenStmt().getChildNodes().size() > 0) {
				for (int j = 0; j < ((IfStmt) node).getThenStmt().getChildNodes().size(); j++) {
					//if (!(((IfStmt) node).getCondition() == node.getChildNodes().get(j))) {
					// addMethodChild(node.getChildNodes().get(j), blockNode);
					// }
					addMethodChild(((IfStmt) node).getThenStmt().getChildNodes().get(j), blockNode);
				}
			}
			if (((IfStmt) node).getElseStmt().isPresent()) {
				String elseStmt = "else";
				Artifact.Op<BlockArtifactData> blockElseArtifact = this.entityFactory.createArtifact(new BlockArtifactData(elseStmt));
				Node.Op blockNodeElse = this.entityFactory.createOrderedNode(blockElseArtifact);
				methodNode.addChild(blockNodeElse);
				if (((IfStmt) node).getElseStmt().get().getChildNodes().size() > 0) {
					for (int j = 0; j < ((IfStmt) node).getElseStmt().get().getChildNodes().size(); j++) {
						addMethodChild(((IfStmt) node).getElseStmt().get().getChildNodes().get(j), blockNodeElse);
					}
				}
			}
		} else if (node instanceof ForStmt) {
			String part1 = "", part2 = "", part3 = "";
			String stmt;
			if (node.getChildNodes().size() > 3) {
				if (node.getChildNodes().get(0) instanceof VariableDeclarationExpr) {
					part1 = node.getChildNodes().get(0).toString() + "; ";
				}
				if (node.getChildNodes().get(1) instanceof BinaryExpr) {
					part2 = node.getChildNodes().get(1).toString() + "; ";
				}
				if (node.getChildNodes().get(2) instanceof UnaryExpr) {
					part3 = node.getChildNodes().get(2).toString();
				}
				stmt = "for(" + part1 + part2 + part3 + ")";
			} else {
				stmt = "for(" + node.getChildNodes().get(0).toString() + ")";
			}

			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
			Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);

			if (((ForStmt) node).getBody().getChildNodes().size() > 0) {
				for (com.github.javaparser.ast.Node stmtaux : ((ForStmt) node).getBody().getChildNodes()) {
					addMethodChild(stmtaux, blockNode);
				}
			}
		} else if (node instanceof DoStmt) {
			Node.Op blockNode = null;
			int endDo = node.toString().indexOf("{");
			String dostmt = node.toString().substring(0, endDo - 1) + " (" + ((DoStmt) node).getCondition().toString() + " )";
			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(dostmt));
			blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);
			DoStmt doStmt = (DoStmt) node;
			if (doStmt.getChildNodes().size() > 1) {
				for (com.github.javaparser.ast.Node nodeChild : node.getChildNodes()) {
					addMethodChild(nodeChild, blockNode);
				}
			}
		} else if (node instanceof ForeachStmt) {
			String stmt = "for (" + node.getChildNodes().get(0).toString() + " : " + node.getChildNodes().get(1).toString() + ")";
			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
			Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);

			if (node.getChildNodes().size() > 2) {
				for (int l = 2; l < node.getChildNodes().size(); l++) {
					addMethodChild(node.getChildNodes().get(l), blockNode);
				}
			}

		} else if (node instanceof ReturnStmt) {
			int inicio = node.toString().indexOf("return");
			String stmt = node.toString().substring(inicio);
			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
			Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);

			if (node.getChildNodes().size() > 1) {
				for (int j = 1; j < node.getChildNodes().size(); j++) {
					if (node.getChildNodes().get(j) instanceof BlockComment) {
					} else {
						addMethodChild(node.getChildNodes().get(j), blockNode);
					}
				}
			}

		} else if (node instanceof SwitchStmt) {
			int end = node.toString().indexOf("{");
			String stmt = node.toString().substring(0, end - 1);
			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
			Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);

			if (((SwitchStmt) node).getEntries().size() > 1) {
				for (SwitchEntryStmt entryStmt : ((SwitchStmt) node).getEntries()) {
					SwitchEntryStmt switchEntryStmt = entryStmt;
					int endSwitchEntry = switchEntryStmt.toString().indexOf(":");
					String entry = switchEntryStmt.toString().substring(0, endSwitchEntry);
					Artifact.Op<BlockArtifactData> blockArtifactEntry = this.entityFactory.createArtifact(new BlockArtifactData(entry));
					Node.Op blockNodeEntry = this.entityFactory.createOrderedNode(blockArtifactEntry);
					blockNode.addChild(blockNodeEntry);
					addMethodChild(switchEntryStmt, blockNodeEntry);
				}

			}

		} else if (node instanceof ThrowStmt) {
			String stmt = node.getChildNodes().get(0).toString();
			Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
			Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
			methodNode.addChild(lineNode);
			if (node.getChildNodes().size() > 1) {
				for (int j = 0; j < node.getChildNodes().size(); j++) {
					addMethodChild(node.getChildNodes().get(j), methodNode);
				}
			}

		} else if (node instanceof TryStmt) {
			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("try"));
			Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);

			if (node.getChildNodes().size() > 1) {
				for (com.github.javaparser.ast.Node childNode : node.getChildNodes()) {
					addMethodChild(childNode, blockNode);
				}
			}

		} else if (node instanceof WhileStmt) {
			String whilestmt = "while(" + ((WhileStmt) node).getCondition().toString() + ")";
			Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(whilestmt));
			Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
			methodNode.addChild(blockNode);

			if (node.getChildNodes().size() > 1) {
				for (com.github.javaparser.ast.Node stmtaux : ((WhileStmt) node).getBody().getChildNodes()) {
					addMethodChild(stmtaux, blockNode);
				}
			}

		} else if (node instanceof BlockStmt) {
			if (node.getChildNodes().size() > 0) {
				for (com.github.javaparser.ast.Node st : node.getChildNodes()) {
					addMethodChild(st, methodNode);
				}
			}
		} else if (node instanceof ExpressionStmt) {
			ExpressionStmt exp = (ExpressionStmt) node;
			VariableDeclarationExpr expr;
			if ((exp.getExpression() instanceof VariableDeclarationExpr)) {
				expr = (VariableDeclarationExpr) exp.getExpression();

				if (expr.getVariable(0).getInitializer().isPresent()) {
					if (expr.getVariable(0).getInitializer().get() instanceof ArrayInitializerExpr) {
						String stmt = "Array " + expr.getVariable(0).getName().toString();
						Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
						Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
						methodNode.addChild(lineNode);
						for (com.github.javaparser.ast.Node childNode : expr.getVariable(0).getChildNodes().get(2).getChildNodes()) {
							String stmtChild = childNode.toString();
							Artifact.Op<LineArtifactData> lineArtifactChild = this.entityFactory.createArtifact(new LineArtifactData(stmtChild));
							Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifactChild);
							lineNode.addChild(lineNodeChild);
						}
					} else {
						String stmt = expr.toString();
						Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
						Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
						methodNode.addChild(lineNode);
					}
				} else {
					String stmt = node.getChildNodes().get(0).toString();
					Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
					Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
					methodNode.addChild(lineNode);
					if (node.getChildNodes().size() > 1) {
						for (int j = 1; j < node.getChildNodes().size(); j++) {
							addMethodChild(node.getChildNodes().get(j), lineNode);
						}
					}
				}
			} else {
				String stmt = node.toString();
				Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
				Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
				methodNode.addChild(lineNode);
			}

		} else if (node instanceof SwitchEntryStmt) {

			if (((SwitchEntryStmt) node).getStatements().size() > 1) {
				for (com.github.javaparser.ast.Node switchentry : node.getChildNodes()) {
					addMethodChild(switchentry, methodNode);
				}
			}
		} else if (node instanceof BreakStmt) {
			String stmt = "break";
			Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
			Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
			methodNode.addChild(lineNode);
		} else if (node instanceof CatchClause) {
			Artifact.Op<BlockArtifactData> catchArtifact = this.entityFactory.createArtifact(new BlockArtifactData(node.toString().substring(0, node.toString().indexOf(")") + 1)));
			Node.Op catchNode = this.entityFactory.createOrderedNode(catchArtifact);
			methodNode.addChild(catchNode);
			if (((CatchClause) node).getBody().getChildNodes().size() > 0) {
				for (com.github.javaparser.ast.Node childNode : ((CatchClause) node).getBody().getChildNodes()) {
					addMethodChild(childNode, catchNode);
				}
			}
		} else if (node instanceof MethodCallExpr || node instanceof ContinueStmt || node instanceof ExplicitConstructorInvocationStmt) {
			Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.toString()));
			Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
			methodNode.addChild(lineNode);
		} /*else {
            if (!(node instanceof JavadocComment)&& !(node instanceof LineComment)) {
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.toString()));
                Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                methodNode.addChild(lineNode);
            }
        }*/
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

}
