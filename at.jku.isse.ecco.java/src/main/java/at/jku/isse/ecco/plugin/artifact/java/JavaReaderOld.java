package at.jku.isse.ecco.plugin.artifact.java;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.eclipse.jdt.core.BindingKey;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class JavaReaderOld implements ArtifactReader<Path, Set<Node>> {

	private final EntityFactory entityFactory;

	@Inject
	public JavaReaderOld(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
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


	@Override
	public String getPluginId() {
		return JavaPlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{"text", "java"};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	@Override
	public boolean canRead(Path path) {
		// TODO: actually check contents of file to see if it is a text file
		if (!Files.isDirectory(path) && Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".java"))
			return true;
		else
			return false;
	}


	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {
		Set<Node> nodes = new HashSet<>();

		referencing.clear();
		referenced.clear();

		parse(input, base, nodes);

		return nodes;
	}


	private static final String SOURCE_TYPE = "java";
	private final IdentityHashMap<IBinding, Artifact> referenced = new IdentityHashMap<>();
	private final List<Pair> referencing = new LinkedList<>();


	private void parse(Path[] sources, final Path sourcePath, final Set<Node> nodes) {

		ASTParser parser = ASTParser.newParser(AST.JLS8);

		String[] classpath = {System.getProperty("java.class.path")};
		parser.setEnvironment(classpath, new String[]{sourcePath.toString()}, new String[]{"UTF-8"}, true);

		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(true);

		parser.setKind(ASTParser.K_COMPILATION_UNIT);

		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
		parser.setCompilerOptions(options);

		List<String> bindingKeys = new ArrayList<>();

		for (Path typeName : sources) {
			// relative path to sourcePath
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
				Artifact cuArtifact = entityFactory.createArtifact(cuArtifactData);
				checkForReferences(cuArtifact, cu);

				//String source = new File(sourceFilePath).getAbsolutePath().replace(sourcePath[0], "").substring(1);
				String source = sourceFilePath;
				cuArtifactData.setSource(source);
				cuArtifactData.setSourceType(SOURCE_TYPE);

				final Node cuNode = entityFactory.createNode(cuArtifact);

				//nodes.add(cuNode);
				Artifact<PluginArtifactData> pluginArtifact = entityFactory.createArtifact(new PluginArtifactData(JavaReaderOld.this.getPluginId(), sourcePath.toAbsolutePath().relativize(Paths.get(sourceFilePath))));
				Node pluginNode = entityFactory.createOrderedNode(pluginArtifact);
				nodes.add(pluginNode);
				pluginNode.addChild(cuNode);

				traverseAST(cu, cuNode, cu, source);
			}
		};

		String[] absoluteSources = new String[sources.length];
		int i = 0;
		for (Path path : sources) {
			absoluteSources[i] = sourcePath.resolve(path).toAbsolutePath().toString();
			i++;
		}

		parser.createASTs(absoluteSources, null, bindingKeys.toArray(new String[bindingKeys.size()]), requestor, null);

		resolveReverences();
	}


	// TODO: make an extra component to calculate identifiers, so it can be reused in other parts when they need to be recalculated
	private static String getIdentifier(ASTNode astNode) {
		if (astNode == null) {
			return null;
		}
		boolean first = true;
		switch (astNode.getClass().getName()) {
			case "org.eclipse.jdt.core.dom.Block":
				return "BLOCK";
			case "org.eclipse.jdt.core.dom.TryStatement":
				return "TRY";
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

	private static boolean isOrdered(ChildListPropertyDescriptor desc) {
		return !(desc.getNodeClass() == CompilationUnit.class
				|| desc.getNodeClass() == TypeDeclaration.class
				|| desc.getNodeClass() == FieldDeclaration.class
				|| (desc.getNodeClass() == MethodDeclaration.class && desc.getId().equals("modifiers"))
				|| desc.getNodeClass() == Javadoc.class);
	}

	private void checkForReferences(Artifact artifact, ASTNode node) {

		// referenced node types
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
		}

		// both
		else if (node instanceof EnumConstantDeclaration) {
			referenced.put(((EnumConstantDeclaration) node).resolveVariable(), artifact);
			referencing.add(new Pair(artifact, ((EnumConstantDeclaration) node).resolveConstructorBinding()));
		} else if (node instanceof Annotation) {
			referencing.add(new Pair(artifact, ((Expression) node).resolveTypeBinding()));
			referenced.put(((Annotation) node).resolveAnnotationBinding(), artifact);
		}

		// referencing node types
		else if (node instanceof Type) {
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
			Artifact ref = referenced.get(pair.binding);
			if (ref != null) {
				ArtifactReference reference = entityFactory.createArtifactReference(pair.artifact, ref);
				// TODO check if reference already exists

				pair.artifact.addUses(reference);
				ref.addUsedBy(reference);
			}
		}
	}

	private void traverseAST(ASTNode astNode, Node parent, final CompilationUnit cu, final String source) {
		if (astNode instanceof ExpressionStatement || astNode instanceof ImportDeclaration || astNode instanceof PackageDeclaration) {
			parent.getArtifact().setAtomic(true);
		}

		List<StructuralPropertyDescriptor> stucture = astNode.structuralPropertiesForType();
		for (StructuralPropertyDescriptor desc : stucture) {
			Object obj = astNode.getStructuralProperty(desc);
			if (obj != null) {
				if (desc instanceof ChildPropertyDescriptor) {
					JDTPropertyArtifactData propertyArtifactData = new JDTPropertyArtifactData(desc.toString(), desc.getId(), desc.getClass().getName(), ((ChildPropertyDescriptor) desc).isMandatory());
					Node propertyNode = entityFactory.createNode(propertyArtifactData);
					parent.addChild(propertyNode);

					propertyArtifactData.setSource(source);
					propertyArtifactData.setSourceType(SOURCE_TYPE);

					ASTNode objNode = (ASTNode) obj;

					if (objNode instanceof FieldDeclaration
							|| objNode instanceof VariableDeclarationStatement) {
						variable(objNode, propertyNode, cu, source);
					} else {
						String ident = getIdentifier(objNode);
						JDTNodeArtifactData jdtArtifactData = new JDTNodeArtifactData(ident, ident, objNode.getClass().getName() + ":" + objNode.getNodeType());
						Artifact jdtArtifact = entityFactory.createArtifact(jdtArtifactData);
						checkForReferences(jdtArtifact, objNode);

						jdtArtifactData.setSource(source);
						jdtArtifactData.setSourceType(SOURCE_TYPE);

						Node node = entityFactory.createNode(jdtArtifact);

						traverseAST(objNode, node, cu, source);

						propertyNode.addChild(node);
					}

				} else if (desc instanceof ChildListPropertyDescriptor) {
					JDTPropertyArtifactData propertyArtifactData = new JDTPropertyArtifactData(desc.toString(), desc.getId(), desc.getClass().getName(), false);
					Node propertyNode;
					if (isOrdered((ChildListPropertyDescriptor) desc)) {
						propertyNode = entityFactory.createOrderedNode(propertyArtifactData);
					} else {
						propertyNode = entityFactory.createNode(propertyArtifactData);
					}
					parent.addChild(propertyNode);

					propertyArtifactData.setSource(source);
					propertyArtifactData.setSourceType(SOURCE_TYPE);

					List<ASTNode> list = (List<ASTNode>) obj;
					for (ASTNode item : list) {

						if (item instanceof FieldDeclaration || item instanceof VariableDeclarationStatement) {
							variable(item, propertyNode, cu, source);
						} else {
							String ident = getIdentifier(item);
							JDTNodeArtifactData jdtArtifactData = new JDTNodeArtifactData(ident, ident, item.getClass().getName() + ":" + item.getNodeType());
							Artifact jdtArtifact = entityFactory.createArtifact(jdtArtifactData);
							checkForReferences(jdtArtifact, item);

							jdtArtifactData.setSource(source);
							jdtArtifactData.setSourceType(SOURCE_TYPE);

							Node node = entityFactory.createNode(jdtArtifact);

							traverseAST(item, node, cu, source);

							propertyNode.addChild(node);
						}
					}
				} else if (desc instanceof SimplePropertyDescriptor) {
					JDTPropertyArtifactData propertyArtifact = new JDTPropertyArtifactData(desc.toString(), desc.getId(), desc.getClass().getName(), ((SimplePropertyDescriptor) desc).isMandatory());

					Node propertyNode = entityFactory.createNode(propertyArtifact);
					parent.addChild(propertyNode);

					//parent.getArtifact().setAtomic(true);

					propertyArtifact.setSource(source);
					propertyArtifact.setSourceType(SOURCE_TYPE);

					JDTNodeArtifactData jdtArtifactData = new JDTNodeArtifactData(obj.toString(), obj.toString(), obj.getClass().getName(), true);

					jdtArtifactData.setSource(source);
					jdtArtifactData.setSourceType(SOURCE_TYPE);

					Node node = entityFactory.createNode(jdtArtifactData);
					propertyNode.addChild(node);
				}
			}
		}
	}

	private void variable(ASTNode var, Node parent, final CompilationUnit cu, final String source) {
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
			JDTNodeArtifactData artifact = new JDTNodeArtifactData(ident, ident, var.getClass().getName() + ":" + var.getNodeType());

			artifact.setSource(source);
			artifact.setSourceType(SOURCE_TYPE);

			Node node = entityFactory.createNode(artifact);
			parent.addChild(node);

			// modifiers
			JDTPropertyArtifactData modifiersArtifact = new JDTPropertyArtifactData("modifiers", "modifiers", ChildListPropertyDescriptor.class.getName(), false);

			modifiersArtifact.setSource(source);
			modifiersArtifact.setSourceType(SOURCE_TYPE);

			Node modifiersNode = entityFactory.createNode(modifiersArtifact);
			node.addChild(modifiersNode);

			for (IExtendedModifier modifier : modifiers) {
				String modifierIdent = getIdentifier((ASTNode) modifier);
				JDTNodeArtifactData modifierArtifact = new JDTNodeArtifactData(modifierIdent, modifierIdent, modifier.getClass().getName() + ":" + ((ASTNode) modifier).getNodeType());

				modifierArtifact.setSource(source);
				modifierArtifact.setSourceType(SOURCE_TYPE);

				Node modifierNode = entityFactory.createNode(modifierArtifact);
				modifiersNode.addChild(modifierNode);

				traverseAST((ASTNode) modifier, modifierNode, cu, source);
			}

			// type
			JDTPropertyArtifactData typeArtifact = new JDTPropertyArtifactData("type", "type", ChildPropertyDescriptor.class.getName(), true);
			Node typeNode = entityFactory.createNode(typeArtifact);
			node.addChild(typeNode);

			String typeIdent = getIdentifier(type);
			JDTNodeArtifactData tyArtifact = new JDTNodeArtifactData(typeIdent, typeIdent, type.getClass().getName() + ":" + type.getNodeType());

			tyArtifact.setSource(source);
			tyArtifact.setSourceType(SOURCE_TYPE);

			Node tyNode = entityFactory.createNode(tyArtifact);
			typeNode.addChild(tyNode);

			traverseAST(type, tyNode, cu, source);

			// fragments
			JDTPropertyArtifactData fragmentsArtifact = new JDTPropertyArtifactData("fragments", "fragments", ChildListPropertyDescriptor.class.getName(), false);

			fragmentsArtifact.setSource(source);
			fragmentsArtifact.setSourceType(SOURCE_TYPE);

			Node fragmentsNode = entityFactory.createNode(fragmentsArtifact);
			node.addChild(fragmentsNode);

			String fragmentIdent = getIdentifier(fragment);
			JDTNodeArtifactData fragmentArtifact = new JDTNodeArtifactData(fragmentIdent, fragmentIdent, fragment.getClass().getName() + ":" + fragment.getNodeType());

			fragmentArtifact.setSource(source);
			fragmentArtifact.setSourceType(SOURCE_TYPE);

			Node fragmentNode = entityFactory.createNode(fragmentArtifact);
			fragmentsNode.addChild(fragmentNode);

			traverseAST(fragment, fragmentNode, cu, source);
		}

	}

	private class Pair {
		Artifact artifact;
		IBinding binding;

		Pair(Artifact artifact, IBinding binding) {
			this.binding = binding;
			this.artifact = artifact;
		}
	}

}
