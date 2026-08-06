package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.eclipse.jdt.core.BindingKey;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class JavaReader implements ArtifactReader<Path, Set<Node.Op>> {

	private final EntityFactory entityFactory;

	@Inject
	public JavaReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
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


	@Override
	public String getPluginId() {
		return JavaPlugin.class.getName();
	}

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(2, new String[]{"**.java"});
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

//		referencing.clear();
//		referenced.clear();
		referencing = new LinkedList<Pair>();
		referenced = new IdentityHashMap<>();


		parse(input, base, nodes, true);

		return nodes;
	}


	private static String SOURCE_TYPE = "java";

	private static List<String> ORDERED = new LinkedList<>();

	/**
	 * true: create JavaJDTPropertyArtifact
	 * false: don't create JavaJDTPropertyArtifact
	 * <p>
	 * CAUTION: JavaJDTPropertyArtifact are needed to create the JDT again, for code generation
	 */
	private static final boolean CREATE_PROPERTY_NODES = true;

	static {
		ORDERED.add("");
	}

	private static List<Pair> referencing = new LinkedList<>();
	private static IdentityHashMap<IBinding, Artifact.Op<?>> referenced = new IdentityHashMap<>();


	@SuppressWarnings("unchecked")
	//private void parse(String[] sources, final String[] sourcePath, final HashSet<Node> nodes, final boolean saveLocationInfromtation) {
	private void parse(Path[] sources, final Path sourcePath, final Set<Node.Op> nodes, final boolean saveLocationInfromtation) {


		ASTParser parser = ASTParser.newParser(AST.JLS8);

		String[] classpath = {System.getProperty("java.class.path")};
		parser.setEnvironment(classpath, new String[]{sourcePath.toString()}, new String[]{"UTF-8"}, true);

		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(true);

		parser.setKind(ASTParser.K_COMPILATION_UNIT);

		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_7);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_7);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_7);
		parser.setCompilerOptions(options);

		List<String> bindingKeys = new ArrayList<>();

		for (Path typeName : sources) {
			//relative path to sourcePath
			//bindingKeys.add(BindingKey.createTypeBindingKey(new File(typeName).getAbsolutePath().replace(sourcePath[0], "")));
			bindingKeys.add(BindingKey.createTypeBindingKey(typeName.toString()));
		}

		FileASTRequestor requestor = new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				super.acceptAST(sourceFilePath, cu);

				String cuString = "";

				if (cu.getPackage() != null) {
					cuString += cu.getPackage().getName().toString() + ".";
				}

				final String cuName = new File(sourceFilePath).getName().replace(".java", "");
				cuString += cuName;

				JDTNodeArtifactData cuArtifactData = new JDTNodeArtifactData(cuString, cuString, cu.getClass().getName() + ":" + cu.getNodeType());
				Artifact.Op<?> cuArtifact = entityFactory.createArtifact(cuArtifactData);
				int pos = cu.getStartPosition();
				int line = cu.getLineNumber(pos);
				int col = cu.getColumnNumber(pos);
				cuArtifact.putProperty("pos", pos);
				cuArtifact.putProperty("line", line);
				cuArtifact.putProperty("col", col);

				checkForReferences(cuArtifact, cu);

				//String source = new File(sourceFilePath).getAbsolutePath().replace(sourcePath[0], "").substring(1);
				String source = sourceFilePath;
				cuArtifactData.setSource(source);
				cuArtifactData.setSourceType(SOURCE_TYPE);

				final Node.Op cuNode = entityFactory.createNode(cuArtifact);

				//nodes.add(cuNode);
				Artifact.Op<PluginArtifactData> pluginArtifact = entityFactory.createArtifact(new PluginArtifactData(JavaReader.this.getPluginId(), sourcePath.toAbsolutePath().relativize(Paths.get(sourceFilePath))));
				Node.Op pluginNode = entityFactory.createOrderedNode(pluginArtifact);
				nodes.add(pluginNode);
				pluginNode.addChild(cuNode);

				traverseAST(cu, cuNode, saveLocationInfromtation, cu, source);
			}
		};

		String[] absoluteSources = new String[sources.length];
		int i = 0;
		for (Path path : sources) {
			absoluteSources[i] = sourcePath.resolve(path).toAbsolutePath().toString();
			i++;
		}

		parser.createASTs(absoluteSources, null, bindingKeys.toArray(new String[0]), requestor, null);

		resolveReverences();
	}

	@SuppressWarnings("unchecked")
	private void traverseAST(ASTNode astNode, Node.Op parent, final boolean saveLocationInfromtation, final CompilationUnit cu, final String source) {
//		if(astNode instanceof Expression){
//			return;
//		}

		if (astNode instanceof ExpressionStatement ||
				astNode instanceof ImportDeclaration ||
				astNode instanceof PackageDeclaration) {

			if (parent.getArtifact() != null) {
				parent.getArtifact().setAtomic(true);
			}
		}

		List<StructuralPropertyDescriptor> stucture = astNode.structuralPropertiesForType();
		for (StructuralPropertyDescriptor desc : stucture) {
			Object obj = astNode.getStructuralProperty(desc);
			if (obj != null) {
				if (desc instanceof ChildPropertyDescriptor) {
					JDTPropertyArtifactData propertyArtifactData = new JDTPropertyArtifactData(desc.toString(), desc.getId(), desc.getClass().getName(), ((ChildPropertyDescriptor) desc).isMandatory());
					Node.Op propertyNode = entityFactory.createNode(propertyArtifactData);
					if (CREATE_PROPERTY_NODES) {
						parent.addChild(propertyNode);
					}

					ASTNode objNode = (ASTNode) obj;

					if (objNode instanceof FieldDeclaration || objNode instanceof VariableDeclarationStatement) {
						if (CREATE_PROPERTY_NODES) {
							variable(objNode, propertyNode, saveLocationInfromtation, cu, source);
						} else {
							variable(objNode, parent, saveLocationInfromtation, cu, source);
						}
					} else {
						String ident = getIdentifier(objNode);
						JDTNodeArtifactData jdtArtifactData = new JDTNodeArtifactData(ident, ident, objNode.getClass().getName() + ":" + objNode.getNodeType());
						Artifact.Op<?> jdtArtifact = entityFactory.createArtifact(jdtArtifactData);
						addProperties(jdtArtifact, objNode, saveLocationInfromtation, cu);
						checkForReferences(jdtArtifact, objNode);

						jdtArtifactData.setSource(source);
						jdtArtifactData.setSourceType(SOURCE_TYPE);

						Node.Op node = entityFactory.createNode(jdtArtifact);

						traverseAST(objNode, node, saveLocationInfromtation, cu, source);

						if (CREATE_PROPERTY_NODES) {
							propertyNode.addChild(node);
						} else {
							parent.addChild(node);
						}
					}

				} else if (desc instanceof ChildListPropertyDescriptor) {
					List<ASTNode> list = (List<ASTNode>) obj;
					if (!list.isEmpty()) {
						JDTPropertyArtifactData propertyArtifactData = new JDTPropertyArtifactData(desc.toString(), desc.getId(), desc.getClass().getName(), false);
						Node.Op propertyNode = entityFactory.createNode(propertyArtifactData);
						if (isOrdered((ChildListPropertyDescriptor) desc)) {
							propertyNode = entityFactory.createOrderedNode(propertyArtifactData);
						} else {
							propertyNode = entityFactory.createNode(propertyArtifactData);
						}
						if (CREATE_PROPERTY_NODES) {
							parent.addChild(propertyNode);
						}

						propertyArtifactData.setSource(source);
						propertyArtifactData.setSourceType(SOURCE_TYPE);

						for (ASTNode item : list) {
							ASTNode objNode = (ASTNode) item;

							if (objNode instanceof FieldDeclaration || objNode instanceof VariableDeclarationStatement) {
								if (CREATE_PROPERTY_NODES) {
									variable(objNode, propertyNode, saveLocationInfromtation, cu, source);
								} else {
									variable(objNode, parent, saveLocationInfromtation, cu, source);
								}
							} else {
								String ident = getIdentifier(objNode);

								JDTNodeArtifactData jdtArtifactData = new JDTNodeArtifactData(ident, ident, objNode.getClass().getName() + ":" + objNode.getNodeType());
								Artifact.Op<?> jdtArtifact = entityFactory.createArtifact(jdtArtifactData);
								addProperties(jdtArtifact, objNode, saveLocationInfromtation, cu);
								checkForReferences(jdtArtifact, objNode);

								jdtArtifactData.setSource(source);
								jdtArtifactData.setSourceType(SOURCE_TYPE);

								Node.Op node = entityFactory.createNode(jdtArtifact);

								traverseAST(objNode, node, saveLocationInfromtation, cu, source);

								if (CREATE_PROPERTY_NODES) {
									propertyNode.addChild(node);
								} else {
									parent.addChild(node);
								}
							}
						}
					}
				} else if (desc instanceof SimplePropertyDescriptor) {
					JDTPropertyArtifactData propertyArtifactData = new JDTPropertyArtifactData(desc.toString(), desc.getId(), desc.getClass().getName(), ((SimplePropertyDescriptor) desc).isMandatory());
					Artifact.Op<?> propertyArtifact = entityFactory.createArtifact(propertyArtifactData);
					Node.Op propertyNode = entityFactory.createNode(propertyArtifact);
					if (CREATE_PROPERTY_NODES) {
						parent.addChild(propertyNode);
					}

					propertyArtifactData.setSource(source);
					propertyArtifactData.setSourceType(SOURCE_TYPE);

					JDTNodeArtifactData jdtArtifactData = new JDTNodeArtifactData(obj.toString(), obj.toString(), obj.getClass().getName(), true);
					Artifact.Op<?> jdtArtifact = entityFactory.createArtifact(jdtArtifactData);

					jdtArtifactData.setSource(source);
					jdtArtifactData.setSourceType(SOURCE_TYPE);

					Node.Op node = entityFactory.createNode(jdtArtifact);
					if (CREATE_PROPERTY_NODES) {
						propertyNode.addChild(node);
					} else {
						parent.addChild(node);
					}
				}
			}
		}
	}

	/**
	 * Special Treatment for variables and fields
	 *
	 * @param var
	 * @param parent
	 * @param saveLocationInfromtation
	 * @param cu
	 */
	@SuppressWarnings("unchecked")
	private void variable(ASTNode var, Node.Op parent, final boolean saveLocationInfromtation, final CompilationUnit cu, final String source) {
		List<VariableDeclarationFragment> fragments;
		List<IExtendedModifier> modifiers;
		Type type;
		String suffix;
		if (var instanceof FieldDeclaration) {
			suffix = "FIELD ";
			fragments = ((FieldDeclaration) var).fragments();
			modifiers = ((FieldDeclaration) var).modifiers();
			type = ((FieldDeclaration) var).getType();
		} else {
			suffix = "VAR ";
			fragments = ((VariableDeclarationStatement) var).fragments();
			modifiers = ((VariableDeclarationStatement) var).modifiers();
			type = ((VariableDeclarationStatement) var).getType();
		}

		for (VariableDeclarationFragment fragment : fragments) {
			String ident = suffix + fragment.getName();
			JDTNodeArtifactData artifactData = new JDTNodeArtifactData(ident, ident, var.getClass().getName() + ":" + var.getNodeType());
			Artifact.Op<?> artifact = entityFactory.createArtifact(artifactData);

			addProperties(artifact, fragment, saveLocationInfromtation, cu);
			checkForReferences(artifact, fragment);
			artifactData.setSource(source);
			artifactData.setSourceType(SOURCE_TYPE);

			Node.Op node = entityFactory.createNode(artifact);
			parent.addChild(node);

			//modifiers
			if (!modifiers.isEmpty()) {
				JDTPropertyArtifactData modifiersArtifactData = new JDTPropertyArtifactData("modifiers", "modifiers", ChildListPropertyDescriptor.class.getName(), false);
				Artifact.Op<?> modifiersArtifact = entityFactory.createArtifact(modifiersArtifactData);

				modifiersArtifactData.setSource(source);
				modifiersArtifactData.setSourceType(SOURCE_TYPE);

				Node.Op modifiersNode = entityFactory.createNode(modifiersArtifact);
				if (CREATE_PROPERTY_NODES) {
					node.addChild(modifiersNode);
				}

				for (IExtendedModifier modifier : modifiers) {
					String modifierIdent = getIdentifier((ASTNode) modifier);
					JDTNodeArtifactData modifierArtifactData = new JDTNodeArtifactData(modifierIdent, modifierIdent, modifier.getClass().getName() + ":" + ((ASTNode) modifier).getNodeType());
					Artifact.Op<?> modifierArtifact = entityFactory.createArtifact(modifierArtifactData);

					addProperties(modifierArtifact, fragment, saveLocationInfromtation, cu);
					modifierArtifactData.setSource(source);
					modifierArtifactData.setSourceType(SOURCE_TYPE);

					Node.Op modifierNode = entityFactory.createNode(modifierArtifact);
					if (CREATE_PROPERTY_NODES) {
						modifiersNode.addChild(modifierNode);
					} else {
						node.addChild(modifierNode);
					}

					traverseAST((ASTNode) modifier, modifierNode, saveLocationInfromtation, cu, source);
				}
			}


			//type
			JDTPropertyArtifactData typeArtifactData = new JDTPropertyArtifactData("type", "type", ChildPropertyDescriptor.class.getName(), true);
			Artifact.Op<?> typeArtifact = entityFactory.createArtifact(typeArtifactData);
			Node.Op typeNode = entityFactory.createNode(typeArtifact);
			if (CREATE_PROPERTY_NODES) {
				node.addChild(typeNode);
			}

			String typeIdent = getIdentifier(type);
			JDTNodeArtifactData tyArtifactData = new JDTNodeArtifactData(typeIdent, typeIdent, type.getClass().getName() + ":" + type.getNodeType());
			Artifact.Op<?> tyArtifact = entityFactory.createArtifact(tyArtifactData);

			addProperties(tyArtifact, type, saveLocationInfromtation, cu);
			checkForReferences(tyArtifact, type);
			tyArtifactData.setSource(source);
			tyArtifactData.setSourceType(SOURCE_TYPE);

			Node.Op tyNode = entityFactory.createNode(tyArtifact);
			if (CREATE_PROPERTY_NODES) {
				typeNode.addChild(tyNode);
			} else {
				node.addChild(tyNode);
			}

			traverseAST(type, tyNode, saveLocationInfromtation, cu, source);

			//fragments
			JDTPropertyArtifactData fragmentsArtifactData = new JDTPropertyArtifactData("fragments", "fragments", ChildListPropertyDescriptor.class.getName(), false);
			Artifact.Op<?> fragmentsArtifact = entityFactory.createArtifact(fragmentsArtifactData);

			fragmentsArtifactData.setSource(source);
			fragmentsArtifactData.setSourceType(SOURCE_TYPE);

			Node.Op fragmentsNode = entityFactory.createNode(fragmentsArtifact);
			if (CREATE_PROPERTY_NODES) {
				node.addChild(fragmentsNode);
			}

			String fragmentIdent = getIdentifier(fragment);
			JDTNodeArtifactData fragmentArtifactData = new JDTNodeArtifactData(fragmentIdent, fragmentIdent, fragment.getClass().getName() + ":" + fragment.getNodeType());
			Artifact.Op<?> fragmentArtifact = entityFactory.createArtifact(fragmentArtifactData);

			addProperties(fragmentArtifact, fragment, saveLocationInfromtation, cu);
			checkForReferences(fragmentArtifact, fragment);
			fragmentArtifactData.setSource(source);
			fragmentArtifactData.setSourceType(SOURCE_TYPE);

			Node.Op fragmentNode = entityFactory.createNode(fragmentArtifact);
			if (CREATE_PROPERTY_NODES) {
				fragmentsNode.addChild(fragmentNode);
			} else {
				node.addChild(fragmentNode);
			}

			traverseAST(fragment, fragmentNode, saveLocationInfromtation, cu, source);
		}

	}

	private void addProperties(Artifact artifact, final ASTNode astNode, final boolean saveLocationInfromtation, final CompilationUnit cu) {
		if (!saveLocationInfromtation) {
			return;
		}
		int pos = astNode.getStartPosition();
		int line = cu.getLineNumber(pos);
		int col = cu.getColumnNumber(pos);
		if (saveLocationInfromtation) {
			artifact.putProperty("pos", pos);
			artifact.putProperty("line", line);
			artifact.putProperty("col", col);
		}
	}

	private void checkForReferences(Artifact.Op<?> artifact, ASTNode node) {
		//referenced node types
		if (node instanceof PackageDeclaration) {
			referenced.put(((PackageDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof TypeDeclaration) {
			referenced.put(((TypeDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof AnonymousClassDeclaration) {
			referenced.put(((AnonymousClassDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof VariableDeclaration) {
			referenced.put(((VariableDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof MethodDeclaration) {
			referenced.put(((MethodDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof AnnotationTypeDeclaration) {
			referenced.put(((AnnotationTypeDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof AnnotationTypeMemberDeclaration) {
			referenced.put(((AnnotationTypeMemberDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof EnumDeclaration) {
			referenced.put(((EnumDeclaration) node).resolveBinding(), artifact);
		} else if (node instanceof TypeParameter) {
			referenced.put(((TypeParameter) node).resolveBinding(), artifact);
		} else if (node instanceof MemberValuePair) {
			referenced.put(((MemberValuePair) node).resolveMemberValuePairBinding(), artifact);


			//both
		} else if (node instanceof EnumConstantDeclaration) {
			referenced.put(((EnumConstantDeclaration) node).resolveVariable(), artifact);
			referencing.add(new Pair(artifact, ((EnumConstantDeclaration) node).resolveConstructorBinding()));
		} else if (node instanceof Annotation) {
			referencing.add(new Pair(artifact, ((Expression) node).resolveTypeBinding()));
			referenced.put(((Annotation) node).resolveAnnotationBinding(), artifact);


			//referencing node types
		} else if (node instanceof Type) {
			referencing.add(new Pair(artifact, ((Type) node).resolveBinding()));
		} else if (node instanceof Name) {
			referencing.add(new Pair(artifact, ((Name) node).resolveBinding()));
			referencing.add(new Pair(artifact, ((Expression) node).resolveTypeBinding()));
		} else if (node instanceof MethodInvocation) {
			referencing.add(new Pair(artifact, ((MethodInvocation) node).resolveMethodBinding()));
			referencing.add(new Pair(artifact, ((Expression) node).resolveTypeBinding()));
		} else if (node instanceof SuperMethodInvocation) {
			referencing.add(new Pair(artifact, ((SuperMethodInvocation) node).resolveMethodBinding()));
			referencing.add(new Pair(artifact, ((Expression) node).resolveTypeBinding()));
		} else if (node instanceof ClassInstanceCreation) {
			referencing.add(new Pair(artifact, ((ClassInstanceCreation) node).resolveConstructorBinding()));
			referencing.add(new Pair(artifact, ((Expression) node).resolveTypeBinding()));
		} else if (node instanceof Expression) {
			referencing.add(new Pair(artifact, ((Expression) node).resolveTypeBinding()));
		} else if (node instanceof FieldAccess) {
			referencing.add(new Pair(artifact, ((FieldAccess) node).resolveFieldBinding()));
		} else if (node instanceof ImportDeclaration) {
			referencing.add(new Pair(artifact, ((ImportDeclaration) node).resolveBinding()));
		}
	}

	private void resolveReverences() {
		for (Pair pair : referencing) {
			Artifact.Op<?> ref = referenced.get(pair.binding);
			if (ref != null) {
				pair.artifact.addUses(ref);

//				ArtifactOperator.ArtifactReferenceOperand reference = entityFactory.createArtifactReference(pair.artifact, ref);
//				//TODO check if reference already exists
//				pair.artifact.addUses(reference);
//				ref.addUsedBy(reference);
			}
		}
	}

	//TODO make an extra component to calculate identifiers, so it can be reused in other parts when they need to be recalculated
	@SuppressWarnings("unchecked")
	public String getIdentifier(ASTNode astNode) {
		if (astNode == null) {
			return null;
		}
		boolean first = true;
		switch (astNode.getClass().getName()) {
			case "org.eclipse.jdt.core.dom.CompilationUnit":
				CompilationUnit cu = (CompilationUnit) astNode;

				String cuString = "";

				if (cu.getPackage() != null) {
					cuString += cu.getPackage().getName().toString() + ".";
				}

//				final String cuName = new File((String) astNode.getProperty(JavaJDTPrinter.SOURCE_FILE)).getName().replace(".java", "");
//				cuString += cuName;

				return cuString;
			case "org.eclipse.jdt.core.dom.FieldDeclaration":
			case "org.eclipse.jdt.core.dom.VariableDeclarationStatement":
				List<VariableDeclarationFragment> fragments;
				String suffix;
				if (astNode instanceof FieldDeclaration) {
					suffix = "FIELD ";
					fragments = ((FieldDeclaration) astNode).fragments();
				} else {
					suffix = "VAR ";
					fragments = ((VariableDeclarationStatement) astNode).fragments();
				}
				String ident = suffix;
				first = true;
				for (VariableDeclarationFragment fragment : fragments) {
					if (!first) {
						ident += ", ";
					}
					ident += fragment.getName();
					first = false;
				}

				return ident;

			case "org.eclipse.jdt.core.dom.Block":
				return "BLOCK";
			case "org.eclipse.jdt.core.dom.TryStatement":
				return "TRY";
			case "org.eclipse.jdt.core.dom.CatchClause":
				CatchClause catchClause = (CatchClause) astNode;

				return "catch (" + catchClause.getException().getType().toString() + ")";
			case "org.eclipse.jdt.core.dom.MethodDeclaration":
				MethodDeclaration methodDeclaration = (MethodDeclaration) astNode;
				String methodString = methodDeclaration.getName().getIdentifier() + "(";
				first = true;
				for (SingleVariableDeclaration parameter : (List<SingleVariableDeclaration>) methodDeclaration.parameters()) {
					if (!first) {
						methodString += ", ";
					}
					methodString += parameter.getType().toString();
					first = false;
				}
				methodString += ")";
				return methodString;
			case "org.eclipse.jdt.core.dom.TypeDeclaration":
				return ((TypeDeclaration) astNode).getName().getIdentifier();
			case "org.eclipse.jdt.core.dom.EnumDeclaration":
				return ((EnumDeclaration) astNode).getName().getIdentifier();
			case "org.eclipse.jdt.core.dom.IfStatement":
				return "if(" + ((IfStatement) astNode).getExpression() + ")";
			case "org.eclipse.jdt.core.dom.SwitchStatement":
				return "switch(" + ((SwitchStatement) astNode).getExpression() + ")";
			case "org.eclipse.jdt.core.dom.ForStatement":
				ForStatement forStatement = (ForStatement) astNode;
				first = true;
				String inits = "";
				for (Object init : forStatement.initializers()) {
					if (!first) {
						inits += ", ";
					}
					inits += init.toString();
					first = false;
				}
				first = true;
				String updaters = "";
				for (Object updater : forStatement.updaters()) {
					if (!first) {
						updaters += ", ";
					}
					updaters += updater.toString();
					first = false;
				}
				return "for(" + inits + "; " + forStatement.getExpression() + "; " + updaters + ")";
			case "org.eclipse.jdt.core.dom.EnhancedForStatement":
				EnhancedForStatement enhancedForStatement = (EnhancedForStatement) astNode;
				return "for(" + enhancedForStatement.getParameter() + " : " + enhancedForStatement.getExpression() + ")";
			case "org.eclipse.jdt.core.dom.WhileStatement":
				WhileStatement whileStatement = (WhileStatement) astNode;
				return "while(" + whileStatement.getExpression() + ")";
			case "org.eclipse.jdt.core.dom.DoStatement":
				DoStatement doStatement = (DoStatement) astNode;
				return "do while(" + doStatement.getExpression() + ")";
			case "org.eclipse.jdt.core.dom.SynchronizedStatement":
				return "synchronized (" + ((SynchronizedStatement) astNode).getExpression() + ")";
			case "org.eclipse.jdt.core.dom.AnnotationTypeDeclaration":
				return ((AnnotationTypeDeclaration) astNode).getName().getIdentifier();
			default:
				return astNode.toString().trim();
		}
	}

	private boolean isOrdered(ChildListPropertyDescriptor desc) {
		if (desc.getNodeClass() == CompilationUnit.class ||
				desc.getNodeClass() == TypeDeclaration.class ||
				desc.getNodeClass() == FieldDeclaration.class ||
				(desc.getNodeClass() == MethodDeclaration.class && desc.getId().equals("modifiers")) ||
				desc.getNodeClass() == Javadoc.class) {
			return false;
		}
		return true;
	}

	private class Pair {
		protected IBinding binding;
		protected Artifact.Op<?> artifact;

		public Pair(IBinding binding, Artifact.Op<?> artifact) {
			this.binding = binding;
			this.artifact = artifact;
		}

		public Pair(Artifact.Op<?> artifact, IBinding binding) {
			this.binding = binding;
			this.artifact = artifact;
		}
	}

}
