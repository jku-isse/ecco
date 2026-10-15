package at.jku.cdl.ecco.adapter.java;

import java.util.Comparator;
import com.github.javaparser.ast.comments.Comment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleDirective;
import com.github.javaparser.ast.nodeTypes.NodeWithBlockStmt;
import com.github.javaparser.ast.nodeTypes.NodeWithBody;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import com.google.inject.Inject;

import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTComment;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTConstructorData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTModuleData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTRepeatedData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTSimpleStringData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTTryData;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.Node.Op;

public class JavaASTReader implements ArtifactReader<Path, Set<Node.Op>> {

	private static final Logger LOGGER = Logger.getLogger(JavaASTReader.class.getSimpleName());

	// These 2 are used for FORCE^2 environment properties and have no usage to ECCO
	// Attaching these properties to nodes for line number feature mappings
	public static final String PROPERTY_LINE_START = "LINE_START";
	public static final String PROPERTY_LINE_END = "LINE_END";

	/** Ends the data of a switch entry written with an arrow ({@code case A -> ...}). */
	public static final String ARROW = " ->";

	/** The label stored for a switch's default entry (a label named DEFAULT is stored with a trailing space). */
	public static final String DEFAULT_ENTRY = "DEFAULT";

	private static Map<Integer, String[]> prioritizedPatterns;

	// defines file extensions which can be handled by this plugn
	static {
		prioritizedPatterns = new HashMap<>();
		// TextReader also claims "**.java" at priority 1 (as a catch-all alongside .txt/.xml/...),
		// so priority 1 here would tie with it - which of the two wins is then just whatever order
		// Guice's Set<ArtifactReader> happens to iterate in, i.e. undefined. Integer.MAX_VALUE
		// matches the convention adapter/java's JavaBlockReader and adapter/challenge's
		// JavaChallengeReader already use to guarantee a "**.java" reader beats the generic ones.
		prioritizedPatterns.put(Integer.MAX_VALUE, new String[] { "**.java" });
	}

	// new JavaParser()'s default ParserConfiguration.languageLevel is POPULAR = JAVA_11, so records,
	// sealed classes, pattern matching (instanceof and switch), and switch expressions all failed
	// outright (and text blocks/module-info.java worse - see below). JAVA_21 (JavaParser 3.26+) also
	// covers Java 21's pattern-matching switch and record patterns.
	private static final ParserConfiguration PARSER_CONFIGURATION = new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

	private final EntityFactory entityFactory;
	private final PrettyPrinterConfiguration PPC;
	/** For blocks stored as one text (initializers, finally): their comments are part of that text. */
	private final PrettyPrinterConfiguration PPC_WITH_COMMENTS;

	@Inject
	public JavaASTReader(final EntityFactory entityFactory) {
		this.entityFactory = entityFactory;
		PPC = new PrettyPrinterConfiguration();
		PPC.setPrintComments(false);
		PPC_WITH_COMMENTS = new PrettyPrinterConfiguration();
	}

	@Override
	public String getPluginId() {
		return JavaASTPlugin.class.getName();
	}

	@Override
	public Map<Integer, String[]> getPrioritizedPatterns() {
		return prioritizedPatterns;
	}

	@Override

	public String readContext(Path base) {

		// a file's tree depends on nothing but the file

		return "";

	}


	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		// Initial checks and logging
		if (base == null) {
			throw new EccoException(getPluginId() + ": Reader base path is null!");
		}
		Long startTime = System.currentTimeMillis();
		LOGGER.info("Java parsing started...");

		// result set nodes are files/compilation units
		Set<Node.Op> resultNodes = new HashSet<>(input.length);

		for (Path jpath : input) {
			// Absolute path for the parser required
			Path javafile = base.resolve(jpath);
			// as ECCO is still in development, retrieving relative or absolute path changed
			// once, check is to be save for potential future changes
			if (javafile.equals(jpath)) {
				// for the Plug-in artifact nodes, relative path is required
				jpath = base.relativize(jpath);
			}

			// PluginArtifactData is the root artifact of a Plug-in parsed artifact,
			// i.e. file, compilation unit,...
			Artifact.Op<PluginArtifactData> javaFileArtifact = this.entityFactory
					.createArtifact(new PluginArtifactData(this.getPluginId(), jpath));

			// Encapsulate artifact in ECCO Node
			Node.Op javaFileNode = this.entityFactory.createNode(javaFileArtifact);

			// For Java we use the relatively lightweight open source JavaParser
			JavaParser parser = new JavaParser(PARSER_CONFIGURATION);
			try {
				// Parse the Java File
				ParseResult<CompilationUnit> parseResult = parser.parse(javafile);
				// A non-empty problem list means the parse hit something it couldn't fully make
				// sense of - JavaParser can still return a best-effort (possibly badly truncated)
				// CompilationUnit in that case rather than an empty result, so checking only
				// getResult().isPresent() previously let a truncated parse through silently (e.g. a
				// class using pattern-matching switch would silently vanish from the tree entirely,
				// and a subsequent write() would then "successfully" overwrite the original file
				// with nothing). Failing loudly here instead - consistent with every other real
				// error path in this reader/writer pair - trades that silent data loss for a visible
				// commit failure.
				if (!parseResult.getProblems().isEmpty()) {
					String problems = parseResult.getProblems().stream().map(Problem::toString)
							.collect(Collectors.joining("; "));
					throw new EccoException(getPluginId() + ": Unable to fully parse " + javafile + ": " + problems);
				}
				// Following the Java AST is recreated with ECCO nodes.
				// The artifact data objects contain information of later
				// reconstruction of the original AST Elements
				CompilationUnit cu = parseResult.getResult().orElse(null);
				if (cu != null) {
					JavaASTPrinter.keepCommentsOnce(cu);
					attributeOrphanComments(cu);
					// module-info.java parses cleanly (no problems) but its content lives in
					// cu.getModule(), a ModuleDeclaration - a file shaped nothing like a regular
					// class file (no package/imports/types), so it's handled entirely separately.
					if (cu.getModule().isPresent()) {
						addModuleDeclaration(cu.getModule().get(), javaFileNode);
					} else {
						PackageDeclaration pd = cu.getPackageDeclaration().orElse(null);
						String packageName = "";
						if (pd != null) {
							packageName = pd.getNameAsString();
						}
						// Important Data is stored in an ArtifactData element
						JavaASTSimpleStringData packDeclData = new JavaASTSimpleStringData(packageName);
						packDeclData.setType(ASTNodeType.PACKAGEDECLARATION);
						packDeclData.setTreeFormat(JavaASTData.TREE_FORMAT);
						if (pd != null)
							commented(packDeclData, pd);
						// a comment heading the file (e.g. a license) belongs to the compilation unit; with
						// one of the package's own as well, it was dropped - it comes before the package's
						if (packDeclData.getComment() == null) {
							commented(packDeclData, cu);
						} else if (cu.getComment().isPresent()) {
							List<JavaASTComment> leading = new ArrayList<>();
							leading.add(JavaASTComment.of(cu.getComment().get()));
							leading.addAll(packDeclData.getLeadingComments());
							packDeclData.setLeadingComments(leading);
						}
						orphans(packDeclData, cu);
						Artifact.Op<JavaASTSimpleStringData> packDeclArtifact = this.entityFactory
								.createArtifact(packDeclData);
						// The artifact data is encapsulated in the ECCO Node
						Node.Op packDeclNode = this.entityFactory.createNode(packDeclArtifact);
						javaFileNode.addChild(packDeclNode);
						extractImportNodes(cu).forEach(c -> javaFileNode.addChild(c));
						cu.getTypes().stream().forEach(type -> addTypeDeclaration(type, javaFileNode));
					}
				}

			} catch (IOException e) {
				// fail the commit rather than committing an empty file (see UnreadableFileCommitTest)
				throw new EccoException("Unable to parse java file: " + javafile, e);
			}

			// add java file node to result set
			resultNodes.add(javaFileNode);
		}
		Long endTime = System.currentTimeMillis();
		LOGGER.info("Java parsing completed: " + (endTime - startTime) + "ms");
		return resultNodes;
	}

	/**
	 * module-info.java's module header (name + open-flag) becomes one MODULE_DECLARATION node, with
	 * one opaque-text MODULE_DIRECTIVE child per requires/exports/opens/uses/provides clause - the
	 * same granularity IMPORT_DECLARATION already uses for the analogous "one declarative clause per
	 * node" case. See JavaASTWriteHandler.addModuleDeclaration() for the reverse direction.
	 */
	private void addModuleDeclaration(ModuleDeclaration module, Node.Op parent) {
		JavaASTModuleData moduleData = new JavaASTModuleData(module.getNameAsString(), module.isOpen());
		Artifact.Op<JavaASTModuleData> moduleArtifact = this.entityFactory.createArtifact(moduleData);
		Node.Op moduleNode = this.entityFactory.createOrderedNode(moduleArtifact);
		parent.addChild(moduleNode);

		for (ModuleDirective directive : module.getDirectives()) {
			JavaASTSimpleStringData directiveData = new JavaASTSimpleStringData(directive.toString(PPC));
			directiveData.setType(ASTNodeType.MODULE_DIRECTIVE);
			commented(directiveData, directive);
			Artifact.Op<JavaASTSimpleStringData> directiveArtifact = this.entityFactory.createArtifact(directiveData);
			Node.Op directiveNode = this.entityFactory.createOrderedNode(directiveArtifact);
			directiveNode.putProperty(PROPERTY_LINE_START, getStartLine(directive));
			directiveNode.putProperty(PROPERTY_LINE_END, getEndLine(directive));
			moduleNode.addChild(directiveNode);
		}
	}

	private void addTypeDeclaration(TypeDeclaration<?> clazz, Node.Op parent) {
		TypeDeclaration<?> tempClazz = clazz.clone();
		tempClazz.setMembers(new NodeList<>());
		tempClazz.ifEnumDeclaration(ed -> ed.setEntries(new NodeList<>()));
		JavaASTSimpleStringData clazzData = new JavaASTSimpleStringData(tempClazz.toString(PPC));
		clazzData.setType(ASTNodeType.TYPE_DECLARATION);
		commented(clazzData, clazz);
		orphans(clazzData, clazz);
		Artifact.Op<JavaASTSimpleStringData> clazzArtifact = this.entityFactory.createArtifact(clazzData);
		Node.Op clazzNode = this.entityFactory.createNode(clazzArtifact);
		// These 2 statements are a requirement of the FORCE^2 environment and have no
		// usage to ECCO
		clazzNode.putProperty(PROPERTY_LINE_START, getStartLine(clazz));
		clazzNode.putProperty(PROPERTY_LINE_END, getEndLine(clazz));
		clazz.ifEnumDeclaration(ed -> {
			addEnumConstants(ed, clazzNode);
			clazzData.setType(ASTNodeType.ENUM_DECLARATION);
		});
		if (!clazz.getFields().isEmpty()) {
			JavaASTSimpleStringData fieldGroup = new JavaASTSimpleStringData("FIELDS");
			fieldGroup.setType(ASTNodeType.FIELD_GROUP);
			Artifact.Op<JavaASTSimpleStringData> fieldsGroupArtifact = this.entityFactory.createArtifact(fieldGroup);
			Node.Op fieldsGroupNode = this.entityFactory.createOrderedNode(fieldsGroupArtifact);
			clazzNode.addChild(fieldsGroupNode);
			for (FieldDeclaration fd : clazz.getFields()) {
				JavaASTSimpleStringData fdData = new JavaASTSimpleStringData(fd.toString(PPC));
				Artifact.Op<JavaASTSimpleStringData> fdArtifact = this.entityFactory.createArtifact(fdData);
				fdData.setType(ASTNodeType.FIELD_DECLARATION);
				commented(fdData, fd);
				// e.g. in an array initializer: { RED, // the first
				fdData.setTextWithComments(textWithInnerComments(fd));
				Node.Op fieldNode = this.entityFactory.createNode(fdArtifact);
				fieldNode.putProperty(PROPERTY_LINE_START, getStartLine(fd));
				fieldNode.putProperty(PROPERTY_LINE_END, getEndLine(fd));
				fieldsGroupNode.addChild(fieldNode);
			}
		}
		extractConstructors(clazz).forEach(c -> clazzNode.addChild(c));
		extractMethods(clazz).forEach(c -> clazzNode.addChild(c));
		clazz.getMembers().stream().filter(BodyDeclaration::isInitializerDeclaration)
				.map(InitializerDeclaration.class::cast).forEach(new java.util.function.Consumer<>() {
					// how often each text occurred before in this class: a repeated one is told apart
					private final Map<String, Integer> seen = new HashMap<>();

					@Override
					public void accept(InitializerDeclaration init) {
						String text = init.toString(PPC_WITH_COMMENTS);
						addInitializer(text, this.seen.merge(text, 1, Integer::sum) - 1, clazzNode);
					}
				});
		clazz.getMembers().stream().filter(BodyDeclaration::isTypeDeclaration).map(TypeDeclaration.class::cast)
				.forEach(type -> addTypeDeclaration(type, clazzNode));
		parent.addChild(clazzNode);
	}

	/** An initializer block; [occurrence] how many blocks with the same text came before it in the class. */
	private void addInitializer(String text, int occurrence, Op clazzNode) {
		JavaASTSimpleStringData initData = occurrence == 0 ? new JavaASTSimpleStringData(text) : new JavaASTRepeatedData(text, occurrence);
		initData.setType(ASTNodeType.INITIALIZER_DECLARATION);
		Artifact.Op<JavaASTSimpleStringData> initArtifact = this.entityFactory.createArtifact(initData);
		Node.Op initNode = this.entityFactory.createOrderedNode(initArtifact);
		clazzNode.addChild(initNode);
	}

	/**
	 * The constants of an enum go into an ordered node of their own: as unordered children of the
	 * type's node, like its members, a constant only some variants have was checked out wherever the
	 * associations put it - BLUE, RED, GREEN as RED, GREEN, BLUE - and ordinal() and values() changed.
	 * Its text is no identifier, so it equals no constant, field group or member.
	 */
	static final String ENUM_CONSTANT_GROUP = "ENUM CONSTANTS";

	private void addEnumConstants(EnumDeclaration enumdec, Op clazzNode) {
		if (enumdec.getEntries().isEmpty())
			return;
		JavaASTSimpleStringData groupData = new JavaASTSimpleStringData(ENUM_CONSTANT_GROUP);
		groupData.setType(ASTNodeType.ENUM_CONSTANT_GROUP);
		Node.Op groupNode = this.entityFactory.createOrderedNode(this.entityFactory.createArtifact(groupData));
		groupNode.putProperty(PROPERTY_LINE_START, getStartLine(enumdec.getEntries().getFirst().get()));
		groupNode.putProperty(PROPERTY_LINE_END, getEndLine(enumdec.getEntries().getLast().get()));
		clazzNode.addChild(groupNode);
		for (EnumConstantDeclaration ecd : enumdec.getEntries()) {
			// the annotations were lost; a constant without any is stored by its name, as before
			String constant = ecd.getNameAsString();
			if (!ecd.getAnnotations().isEmpty())
				constant = ecd.getAnnotations().stream().map(a -> a.toString(PPC)).collect(Collectors.joining(" ")) + " " + constant;
			JavaASTSimpleStringData enumData = new JavaASTSimpleStringData(constant);
			enumData.setType(ASTNodeType.ENUM_CONSTANTS);
			commented(enumData, ecd);
			Artifact.Op<JavaASTSimpleStringData> enumArtifact = this.entityFactory.createArtifact(enumData);
			Node.Op enumNode = this.entityFactory.createOrderedNode(enumArtifact);
			enumNode.putProperty(PROPERTY_LINE_START, getStartLine(ecd));
			enumNode.putProperty(PROPERTY_LINE_END, getEndLine(ecd));
			groupNode.addChild(enumNode);
			for (Expression arg : ecd.getArguments()) {
				JavaASTSimpleStringData argData = new JavaASTSimpleStringData(arg.toString(PPC));
				argData.setType(ASTNodeType.EXPRESSION);
				Artifact.Op<JavaASTSimpleStringData> argArtifact = this.entityFactory.createArtifact(argData);
				Node.Op argNode = this.entityFactory.createNode(argArtifact);
				argNode.putProperty(PROPERTY_LINE_START, getStartLine(arg));
				argNode.putProperty(PROPERTY_LINE_END, getEndLine(arg));
				enumNode.addChild(argNode);
			}
			for (BodyDeclaration<?> body : ecd.getClassBody()) {
				JavaASTSimpleStringData bodyData = new JavaASTSimpleStringData(body.toString(PPC));
				bodyData.setType(ASTNodeType.BODY_DECLARATION);
				Artifact.Op<JavaASTSimpleStringData> bodyArtifact = this.entityFactory.createArtifact(bodyData);
				Node.Op bodyNode = this.entityFactory.createNode(bodyArtifact);
				bodyNode.putProperty(PROPERTY_LINE_START, getStartLine(body));
				bodyNode.putProperty(PROPERTY_LINE_END, getEndLine(body));
				enumNode.addChild(bodyNode);
			}
		}
	}

	/**
	 * JavaParser leaves a comment followed by an empty line attributed to no node (e.g. a file's
	 * header, or a "section" comment between methods). Kept with their container, such comments
	 * could only be written at its end - a file header ended up below the class. Each is given to
	 * the next declaration or statement after it instead, if that has no comment of its own; only
	 * comments after the last one stay with the container.
	 */
	private void attributeOrphanComments(CompilationUnit cu) {
		this.leadingComments.clear();
		List<com.github.javaparser.ast.Node> containers = new ArrayList<>();
		cu.walk(node -> {
			if (!node.getOrphanComments().isEmpty())
				containers.add(node);
		});
		for (com.github.javaparser.ast.Node container : containers) {
			boolean decomposed = decomposed(container);
			Map<com.github.javaparser.ast.Node, List<Comment>> before = new LinkedHashMap<>();
			List<Comment> orphans = new ArrayList<>(container.getOrphanComments());
			orphans.removeIf(orphan -> orphan.getRange().isEmpty());
			orphans.sort(Comparator.comparing(orphan -> orphan.getRange().get().begin));
			for (Comment orphan : orphans) {
				com.github.javaparser.ast.Node next = container.getChildNodes().stream()
						.filter(child -> child instanceof BodyDeclaration<?> || child instanceof Statement
								|| child instanceof ImportDeclaration || child instanceof PackageDeclaration
								|| child instanceof ModuleDirective || child instanceof SwitchEntry)
						.filter(child -> child.getRange().isPresent()
								&& child.getRange().get().begin.isAfterOrEqual(orphan.getRange().get().end))
						.min(Comparator.comparing(child -> child.getRange().get().begin))
						.orElse(null);
				if (next == null)
					continue;
				if (decomposed && !(next instanceof InitializerDeclaration)) {
					before.computeIfAbsent(next, n -> new ArrayList<>()).add(orphan);
				} else if (next.getComment().isEmpty()) {
					// printed as text with its comments: one comment per node, the others stay where they are
					container.removeOrphanComment(orphan);
					next.setComment(orphan);
				}
			}
			// all comments before a node stay before it, in order: the last is its comment, if it has none
			before.forEach((next, comments) -> {
				comments.forEach(container::removeOrphanComment);
				if (next.getComment().isEmpty())
					next.setComment(comments.remove(comments.size() - 1));
				if (!comments.isEmpty())
					this.leadingComments.put(next, comments);
			});
		}
	}

	/** The comments before a node besides its own (see JavaASTData#getLeadingComments), by the node. */
	private final Map<com.github.javaparser.ast.Node, List<Comment>> leadingComments = new IdentityHashMap<>();

	/**
	 * Whether the reader makes the children of {@code container} nodes of the tree, which keep their
	 * comments (commented()), rather than text printed with its comments: not inside an expression (a
	 * lambda, an anonymous class), a labeled statement, a local class, an initializer, an enum
	 * constant's body or a finally block.
	 */
	private static boolean decomposed(com.github.javaparser.ast.Node container) {
		for (com.github.javaparser.ast.Node node = container; node != null; node = node.getParentNode().orElse(null)) {
			if (node instanceof Expression || node instanceof InitializerDeclaration || node instanceof LabeledStmt
					|| node instanceof LocalClassDeclarationStmt || node instanceof LocalRecordDeclarationStmt
					|| node instanceof EnumConstantDeclaration)
				return false;
			if (node.getParentNode().orElse(null) instanceof TryStmt tryStmt && tryStmt.getFinallyBlock().orElse(null) == node)
				return false;
		}
		return true;
	}

	/** Keeps the comments JavaParser attributed to {@code source}, and those before them, with {@code data} - see JavaASTData. */
	private <T extends JavaASTData> T commented(T data, com.github.javaparser.ast.Node source) {
		source.getComment().ifPresent(comment -> data.setComment(JavaASTComment.of(comment)));
		List<Comment> leading = this.leadingComments.get(source);
		if (leading != null)
			data.setLeadingComments(leading.stream().map(JavaASTComment::of).collect(Collectors.toList()));
		return data;
	}

	/** Keeps the comments directly inside {@code container} that belong to no node of their own with {@code data}. */
	private static void orphans(JavaASTData data, com.github.javaparser.ast.Node container) {
		if (container.getOrphanComments().isEmpty())
			return;
		List<JavaASTComment> orphans = new ArrayList<>(data.getOrphanComments());
		container.getOrphanComments().forEach(comment -> orphans.add(JavaASTComment.of(comment)));
		data.setOrphanComments(orphans);
	}

	/**
	 * The comments inside a body, as orphans(): also the body's own comment, which JavaParser gives
	 * to a block from a comment before its brace ({@code if (x > 0 /* c *&#47;) {}) and which was lost -
	 * a body is no node of its own. It is written inside the block.
	 */
	private static void bodyOrphans(JavaASTData data, Statement body) {
		if (body instanceof BlockStmt && body.getComment().isPresent()) {
			List<JavaASTComment> orphans = new ArrayList<>(data.getOrphanComments());
			orphans.add(JavaASTComment.of(body.getComment().get()));
			data.setOrphanComments(orphans);
		}
		orphans(data, body);
	}

	private int getEndLine(com.github.javaparser.ast.Node node) {
		return node.getEnd().orElse(new Position(-1, -1)).line;
	}

	private int getStartLine(com.github.javaparser.ast.Node node) {
		return node.getBegin().orElse(new Position(-1, -1)).line;
	}

	private Collection<Node.Op> extractConstructors(TypeDeclaration<?> clazz) {
		List<Node.Op> constrNodes = new ArrayList<>(clazz.getConstructors().size());
		for (ConstructorDeclaration constructor : clazz.getConstructors()) {
			JavaASTConstructorData constrData = commented(new JavaASTConstructorData(constructor.getNameAsString()), constructor);
			orphans(constrData, constructor.getBody());
			Artifact.Op<JavaASTConstructorData> constrArtifact = this.entityFactory.createArtifact(constrData);
			Node.Op constrNode = this.entityFactory.createOrderedNode(constrArtifact);
			constrNode.putProperty(PROPERTY_LINE_START, getStartLine(constructor));
			constrNode.putProperty(PROPERTY_LINE_END, getEndLine(constructor));
			constructor.getModifiers().forEach(m -> constrData.addModifier(m.getKeyword().asString()));
			constructor.getParameters().forEach(p -> constrData.addParameter(p.toString(PPC)));
			constrData.setParametersWithComments(withComments(constructor.getParameters()));
			constructor.getTypeParameters().forEach(tp -> constrData.addTypeParameter(tp.toString(PPC)));
			constructor.getThrownExceptions().forEach(te -> constrData.addThrowException(te.toString(PPC)));
			constructor.getAnnotations().forEach(a -> constrData.addAnnotation(a.toString(PPC)));
			addChildren(constructor.getBody(), constrNode);
			constrNodes.add(constrNode);
		}
		// A record's compact constructor (e.g. "public Point { ... }") is a CompactConstructorDeclaration,
		// a distinct BodyDeclaration subtype that clazz.getConstructors() above does not return - without
		// this, its body was silently dropped on every round trip.
		if (clazz instanceof RecordDeclaration) {
			for (CompactConstructorDeclaration compact : ((RecordDeclaration) clazz).getCompactConstructors()) {
				JavaASTConstructorData constrData = commented(new JavaASTConstructorData(compact.getNameAsString()), compact);
				orphans(constrData, compact.getBody());
				constrData.setCompact(true);
				Artifact.Op<JavaASTConstructorData> constrArtifact = this.entityFactory.createArtifact(constrData);
				Node.Op constrNode = this.entityFactory.createOrderedNode(constrArtifact);
				constrNode.putProperty(PROPERTY_LINE_START, getStartLine(compact));
				constrNode.putProperty(PROPERTY_LINE_END, getEndLine(compact));
				compact.getModifiers().forEach(m -> constrData.addModifier(m.getKeyword().asString()));
				compact.getTypeParameters().forEach(tp -> constrData.addTypeParameter(tp.toString(PPC)));
				compact.getThrownExceptions().forEach(te -> constrData.addThrowException(te.toString(PPC)));
				compact.getAnnotations().forEach(a -> constrData.addAnnotation(a.toString(PPC)));
				addChildren(compact.getBody(), constrNode);
				constrNodes.add(constrNode);
			}
		}
		return constrNodes;
	}

	private Collection<Node.Op> extractMethods(TypeDeclaration<?> clazz) {
		List<Node.Op> methodNodes = new ArrayList<>(clazz.getMethods().size());
		for (MethodDeclaration mdec : clazz.getMethods()) {
			MethodDeclaration temp = mdec.clone();
			temp.setBody(null);
			JavaASTSimpleStringData mdData = new JavaASTSimpleStringData(temp.toString(PPC));
			mdData.setType(ASTNodeType.METHOD_DECLARATION);
			commented(mdData, mdec);
			// in its header: at an annotation, a parameter, before the return type
			mdData.setTextWithComments(textWithInnerComments(temp));
			mdec.getBody().ifPresent(methodBody -> orphans(mdData, methodBody));
			Artifact.Op<JavaASTSimpleStringData> constrArtifact = this.entityFactory.createArtifact(mdData);
			Node.Op methNode = this.entityFactory.createOrderedNode(constrArtifact);
			methNode.putProperty(PROPERTY_LINE_START, getStartLine(mdec));
			methNode.putProperty(PROPERTY_LINE_END, getEndLine(mdec));
			methodNodes.add(methNode);
			if (mdec.getBody().isPresent()) {
				addChildren(mdec.getBody().get(), methNode);
			}
		}
		return methodNodes;
	}

	/**
	 * Keeps the comments inside a statement - in a lambda body, between the operands of an expression -
	 * which its identifying text (printed without comments) loses: the statement's own comment is kept
	 * by commented(), so only the text without it is printed with comments.
	 */
	private void withInnerComments(JavaASTData data, Statement statement) {
		String text = textWithInnerComments(statement);
		if (text != null)
			data.setTextWithComments(text);
	}

	/**
	 * {@code node} printed with the comments inside it, but not its own (kept by commented()), or
	 * null if it has none: its identifying text, printed without comments, loses them.
	 */
	private String textWithInnerComments(com.github.javaparser.ast.Node node) {
		if (node.getAllContainedComments().isEmpty())
			return null;
		com.github.javaparser.ast.Node withoutOwnComment = node.clone();
		withoutOwnComment.removeComment();
		String text = JavaASTPrinter.print(withoutOwnComment);
		return text.equals(withoutOwnComment.toString(PPC)) ? null : text;
	}

	/**
	 * A part of a statement or declaration stored as text without comments (a try resource, a catch
	 * or constructor parameter, a case label) printed with its comments, or null if it has none:
	 * kept as metadata outside the artifact's identity, as withInnerComments() does for statements.
	 */
	private String withComments(com.github.javaparser.ast.Node part) {
		if (!hasComments(part))
			return null;
		String text = JavaASTPrinter.print(part.clone());
		return text.equals(part.toString(PPC)) ? null : text;
	}

	/** withComments() of each part, or null if none of them has comments. */
	private List<String> withComments(List<? extends com.github.javaparser.ast.Node> parts) {
		List<String> texts = new ArrayList<>(parts.size());
		boolean any = false;
		for (com.github.javaparser.ast.Node part : parts) {
			String text = withComments(part);
			any |= text != null;
			texts.add(text != null ? text : part.toString(PPC));
		}
		return any ? texts : null;
	}

	private void addChildren(Statement body, Node.Op parent) {
		if (body instanceof NodeWithBlockStmt<?> withBlock) {
			// synchronized: used to be dropped, only the statements of its block were kept
			Statement tmp = body.clone();
			((NodeWithBlockStmt<?>) tmp).setBody(new BlockStmt());
			addNesting(body, tmp.toString(PPC), withBlock.getBody(), parent);
		} else if (body instanceof NodeWithBody<?>) {
			Statement tmp = body.clone();
			((NodeWithBody<?>)tmp).setBody(new EmptyStmt());
			JavaASTSimpleStringData sdData = new JavaASTSimpleStringData(tmp.toString(PPC));
			sdData.setType(ASTNodeType.STATEMENT);
			sdData.setBlockBody(((NodeWithBody<?>) body).getBody() instanceof BlockStmt);
			commented(sdData, body);
			// in its header: for (int i = 0; i < n; i++ // c
			sdData.setTextWithComments(textWithInnerComments(tmp));
			bodyOrphans(sdData, ((NodeWithBody<?>) body).getBody());
			Artifact.Op<JavaASTSimpleStringData> sdArtifact = this.entityFactory.createArtifact(sdData);
			Node.Op node = this.entityFactory.createOrderedNode(sdArtifact);
			node.putProperty(PROPERTY_LINE_START, getStartLine(body));
			node.putProperty(PROPERTY_LINE_END, getEndLine(body));
			parent.addChild(node);
			addChildren(((NodeWithBody<?>) body).getBody(), node);
		} else if (body instanceof NodeWithStatements<?>) {
			((NodeWithStatements<?>) body).getStatements().forEach(stmt -> addStatement(stmt, parent));
		} else if (body instanceof IfStmt) {
			addIfStatement((IfStmt) body, parent);
		} else if (body instanceof SwitchNode) {
			addSwitch((SwitchNode) body, parent);
		} else if (body instanceof TryStmt) {
			addTryStatement((TryStmt) body, parent);
		} else {
			JavaASTSimpleStringData sdData = new JavaASTSimpleStringData(body.toString(PPC));
			sdData.setType(ASTNodeType.STATEMENT);
			commented(sdData, body);
			withInnerComments(sdData, body);
			Artifact.Op<JavaASTSimpleStringData> sdArtifact = this.entityFactory.createArtifact(sdData);
			Node.Op node = this.entityFactory.createNode(sdArtifact);
			node.putProperty(PROPERTY_LINE_START, getStartLine(body));
			node.putProperty(PROPERTY_LINE_END, getEndLine(body));
			parent.addChild(node);
		}

	}

	/**
	 * A statement of a statement list: a block among the statements (e.g. {@code case 0: { int x; }})
	 * is a node of its own. Its statements used to be added to the list, which lost the block's
	 * scope - two such blocks declaring the same variable no longer compiled.
	 */
	private void addStatement(Statement stmt, Node.Op parent) {
		if (stmt instanceof BlockStmt block)
			addNesting(block, new BlockStmt().toString(PPC), block, parent);
		else
			addChildren(stmt, parent);
	}

	/** A statement written as {@code text} with an empty block, and the statements of {@code block} as its children. */
	private void addNesting(Statement stmt, String text, BlockStmt block, Node.Op parent) {
		JavaASTSimpleStringData data = new JavaASTSimpleStringData(text);
		data.setType(ASTNodeType.STATEMENT);
		commented(data, stmt);
		// in its header: synchronized (/* c */ lock)
		if (stmt instanceof NodeWithBlockStmt<?> && !(stmt instanceof BlockStmt)) {
			Statement header = stmt.clone();
			((NodeWithBlockStmt<?>) header).setBody(new BlockStmt());
			data.setTextWithComments(textWithInnerComments(header));
		}
		orphans(data, block);
		Artifact.Op<JavaASTSimpleStringData> artifact = this.entityFactory.createArtifact(data);
		Node.Op node = this.entityFactory.createOrderedNode(artifact);
		node.putProperty(PROPERTY_LINE_START, getStartLine(stmt));
		node.putProperty(PROPERTY_LINE_END, getEndLine(stmt));
		parent.addChild(node);
		addChildren(block, node);
	}

	private void addTryStatement(TryStmt trystmt, Op parent) {
		boolean hasExpression = trystmt.getResources().size() > 0;

		JavaASTTryData tryData = new JavaASTTryData();
		tryData.setType(ASTNodeType.TRYBLOCK);
		commented(tryData, trystmt);
		orphans(tryData, trystmt.getTryBlock());
		Artifact.Op<JavaASTTryData> sdArtifact = this.entityFactory.createArtifact(tryData);
		Node.Op tryNode = this.entityFactory.createOrderedNode(sdArtifact);
		tryNode.putProperty(PROPERTY_LINE_START, getStartLine(trystmt));
		tryNode.putProperty(PROPERTY_LINE_END, getEndLine(trystmt));
		tryData.setFinally(trystmt.getFinallyBlock().isPresent());
		parent.addChild(tryNode);

		if (hasExpression) {
			for (Expression e : trystmt.getResources()) {
				tryData.addExpression(e.toString(PPC));
			}
			tryData.setExpressionsWithComments(withComments(trystmt.getResources()));
		}

		for (CatchClause cc : trystmt.getCatchClauses()) {
			if (!hasExpression) {
				tryData.addCatchParam(cc.getParameter().toString(PPC));
			}
			JavaASTSimpleStringData catchclause = new JavaASTSimpleStringData(cc.getParameter().toString(PPC));
			catchclause.setType(ASTNodeType.CATCHCLAUSE);
			catchclause.setTextWithComments(withComments(cc.getParameter()));
			commented(catchclause, cc);
			orphans(catchclause, cc.getBody());
			Artifact.Op<JavaASTSimpleStringData> ccArtifact = this.entityFactory.createArtifact(catchclause);
			Node.Op ccNode = this.entityFactory.createOrderedNode(ccArtifact);
			ccNode.putProperty(PROPERTY_LINE_START, getStartLine(cc));
			ccNode.putProperty(PROPERTY_LINE_END, getEndLine(cc));
			addChildren(cc.getBody(), ccNode);
			tryNode.addChild(ccNode);
		}

		if (trystmt.getFinallyBlock().isPresent()) {
			JavaASTSimpleStringData finData = new JavaASTSimpleStringData(
					trystmt.getFinallyBlock().get().toString(PPC_WITH_COMMENTS));
			finData.setType(ASTNodeType.FINALLY);
			Artifact.Op<JavaASTSimpleStringData> finArtifact = this.entityFactory.createArtifact(finData);
			Node.Op finNode = this.entityFactory.createNode(finArtifact);
			finNode.putProperty(PROPERTY_LINE_START, getStartLine(trystmt.getFinallyBlock().get()));
			finNode.putProperty(PROPERTY_LINE_END, getEndLine(trystmt.getFinallyBlock().get()));
			tryNode.addChild(finNode);
		}
		addChildren(trystmt.getTryBlock(), tryNode);

	}

	/**
	 * @param sw
	 * @param parent
	 */
	private void addSwitch(SwitchNode sw, Op parent) {
		JavaASTSimpleStringData switchData = new JavaASTSimpleStringData(sw.getSelector().toString(PPC));
		switchData.setType(ASTNodeType.SWITCH_STATEMENT);
		commented(switchData, (com.github.javaparser.ast.Node) sw);
		// e.g. commented-out case labels, more than one before an entry
		orphans(switchData, (com.github.javaparser.ast.Node) sw);
		Artifact.Op<JavaASTSimpleStringData> switchArtifact = this.entityFactory.createArtifact(switchData);
		Node.Op switchNode = this.entityFactory.createOrderedNode(switchArtifact);
		switchNode.putProperty(PROPERTY_LINE_START, getStartLine(sw.getSelector()));
		switchNode.putProperty(PROPERTY_LINE_END, getEndLine(sw.getSelector()));
		parent.addChild(switchNode);
		for (SwitchEntry se : sw.getEntries()) {
			String label = label(se, part -> part.toString(PPC));
			JavaASTSimpleStringData entryData = new JavaASTSimpleStringData(label);
			entryData.setType(ASTNodeType.SWITCH_ENTRIES);
			// the label with its comments (case /* c */ 1:), which the identifying label leaves out
			if (se.getLabels().stream().anyMatch(JavaASTReader::hasComments) || se.getGuard().filter(JavaASTReader::hasComments).isPresent()) {
				String withComments = label(se, part -> JavaASTPrinter.print(part.clone()));
				if (!withComments.equals(label))
					entryData.setTextWithComments(withComments);
			}
			commented(entryData, se);
			orphans(entryData, se);
			Artifact.Op<JavaASTSimpleStringData> entryArtifact = this.entityFactory.createArtifact(entryData);
			Node.Op entryNode = this.entityFactory.createOrderedNode(entryArtifact);
			entryNode.putProperty(PROPERTY_LINE_START, getStartLine(se));
			entryNode.putProperty(PROPERTY_LINE_END, getEndLine(se));
			switchNode.addChild(entryNode);
			se.getStatements().forEach(stmt -> addStatement(stmt, entryNode));
		}
	}

	private static boolean hasComments(com.github.javaparser.ast.Node node) {
		return node.getComment().isPresent() || !node.getAllContainedComments().isEmpty();
	}

	/** The data of a switch entry: its labels, a "default" among them and its guard, printed by {@code print}. */
	private static String label(SwitchEntry se, java.util.function.Function<Expression, String> print) {
		String label = String.join(",",
				se.getLabels().stream().map(print).collect(Collectors.toList()));
		if (label.equals("")) {
			label = DEFAULT_ENTRY;
		} else if (label.equals(DEFAULT_ENTRY)) {
			// a constant named DEFAULT (case DEFAULT:) was stored like the default entry and written
			// as "default:", which changed what the switch does; a trailing space tells it apart and
			// parses the same. The default entry stays "DEFAULT", as stored before.
			label = DEFAULT_ENTRY + " ";
		} else {
			// "case null, default" and a pattern's guard ("case Integer i when i > 0") were left
			// out, which changed the code; an entry without them is stored as before
			if (se.isDefault())
				label += ",default";
			if (se.getGuard().isPresent())
				label += " when " + print.apply(se.getGuard().get());
		}
		// "case A -> ..." does not fall through, unlike "case A:" - it used to be written as the latter
		if (se.getType() != SwitchEntry.Type.STATEMENT_GROUP) {
			label += ARROW;
		}
		return label;
	}

	private void addIfStatement(IfStmt ifstmt, Op parent) {
		JavaASTSimpleStringData ifData = new JavaASTSimpleStringData("if " + ifstmt.getCondition().toString(PPC));
		ifData.setType(ASTNodeType.IF_STATEMENT);
		commented(ifData, ifstmt);
		Artifact.Op<JavaASTSimpleStringData> ifArtifact = this.entityFactory.createArtifact(ifData);
		Node.Op ifNode = this.entityFactory.createOrderedNode(ifArtifact);
		ifNode.putProperty(PROPERTY_LINE_START, getStartLine(ifstmt));
		ifNode.putProperty(PROPERTY_LINE_END, getEndLine(ifstmt));
		parent.addChild(ifNode);
		JavaASTSimpleStringData conditionData = new JavaASTSimpleStringData(ifstmt.getCondition().toString(PPC));
		conditionData.setType(ASTNodeType.IF_CONDITION);
		conditionData.setTextWithComments(withComments(ifstmt.getCondition()));
		conditionData.setBlockBody(ifstmt.getThenStmt() instanceof BlockStmt);
		bodyOrphans(conditionData, ifstmt.getThenStmt());
		// between the then branch and else (e.g. a commented-out else-if): at the then branch's end
		orphans(conditionData, ifstmt);
		Artifact.Op<JavaASTSimpleStringData> conditionArtifact = this.entityFactory.createArtifact(conditionData);
		Node.Op conditionNode = this.entityFactory.createOrderedNode(conditionArtifact);
		addChildren(ifstmt.getThenStmt(), conditionNode);
		ifNode.addChild(conditionNode);
		if (ifstmt.hasCascadingIfStmt()) {
			addIfCondition((IfStmt) ifstmt.getElseStmt().get(), ifNode);
		} else if (ifstmt.hasElseBranch()) {
			JavaASTSimpleStringData elseData = new JavaASTSimpleStringData("else " + ifstmt.getCondition());
			elseData.setType(ASTNodeType.ELSE_BRANCH);
			elseData.setBlockBody(ifstmt.getElseStmt().get() instanceof BlockStmt);
			bodyOrphans(elseData, ifstmt.getElseStmt().get());
			Artifact.Op<JavaASTSimpleStringData> elseArtifact = this.entityFactory.createArtifact(elseData);
			Node.Op elseNode = this.entityFactory.createOrderedNode(elseArtifact);
			elseNode.putProperty(PROPERTY_LINE_START, getStartLine(ifstmt.getElseStmt().get()));
			elseNode.putProperty(PROPERTY_LINE_END, getEndLine(ifstmt.getElseStmt().get()));
			conditionNode.addChild(elseNode);
			addChildren(ifstmt.getElseStmt().get(), elseNode);
		}
	}

	private void addIfCondition(IfStmt ifstmt, Op ifNode) {
		JavaASTSimpleStringData conditionData = new JavaASTSimpleStringData(ifstmt.getCondition().toString(PPC));
		conditionData.setType(ASTNodeType.IF_CONDITION);
		conditionData.setTextWithComments(withComments(ifstmt.getCondition()));
		// the comment of an else-if: } else // c  if (...)
		commented(conditionData, ifstmt);
		conditionData.setBlockBody(ifstmt.getThenStmt() instanceof BlockStmt);
		bodyOrphans(conditionData, ifstmt.getThenStmt());
		// between the then branch and else (e.g. a commented-out else-if): at the then branch's end
		orphans(conditionData, ifstmt);
		Artifact.Op<JavaASTSimpleStringData> conditionArtifact = this.entityFactory.createArtifact(conditionData);
		Node.Op conditionNode = this.entityFactory.createOrderedNode(conditionArtifact);
		conditionNode.putProperty(PROPERTY_LINE_START, getStartLine(ifstmt));
		conditionNode.putProperty(PROPERTY_LINE_END, getEndLine(ifstmt));
		addChildren(ifstmt.getThenStmt(), conditionNode);
		ifNode.addChild(conditionNode);
		if (ifstmt.hasCascadingIfStmt()) {
			addIfCondition((IfStmt) ifstmt.getElseStmt().get(), ifNode);
		} else if (ifstmt.hasElseBranch()) {
			JavaASTSimpleStringData elseData = new JavaASTSimpleStringData("else" + ifstmt.getCondition());
			elseData.setType(ASTNodeType.ELSE_BRANCH);
			elseData.setBlockBody(ifstmt.getElseStmt().get() instanceof BlockStmt);
			bodyOrphans(elseData, ifstmt.getElseStmt().get());
			Artifact.Op<JavaASTSimpleStringData> elseArtifact = this.entityFactory.createArtifact(elseData);
			Node.Op elseNode = this.entityFactory.createOrderedNode(elseArtifact);
			elseNode.putProperty(PROPERTY_LINE_START, getStartLine(ifstmt.getElseStmt().get()));
			elseNode.putProperty(PROPERTY_LINE_END, getEndLine(ifstmt.getElseStmt().get()));
			conditionNode.addChild(elseNode);
			addChildren(ifstmt.getElseStmt().get(), elseNode);
		}

	}

	private Collection<Node.Op> extractImportNodes(CompilationUnit cu) {
		List<Node.Op> importNodes = new ArrayList<>(cu.getImports().size());
		// an import written twice is valid Java and means the same as once - read once, as the
		// file's imports are an unordered set of children, which refuses two equal ones
		Set<String> imported = new HashSet<>();
		for (ImportDeclaration id : cu.getImports()) {
			if (!imported.add(id.toString(PPC)))
				continue;
			JavaASTSimpleStringData importData = new JavaASTSimpleStringData(id.toString(PPC));
			importData.setType(ASTNodeType.IMPORT_DECLARATION);
			commented(importData, id);
			Artifact.Op<JavaASTSimpleStringData> importArtifact = this.entityFactory.createArtifact(importData);
			Node.Op importNode = this.entityFactory.createNode(importArtifact);
			importNode.putProperty(PROPERTY_LINE_START, getStartLine(id));
			importNode.putProperty(PROPERTY_LINE_END, getEndLine(id));
			importNodes.add(importNode);
		}
		return importNodes;
	}

	@Override
	public Set<Node.Op> read(Path[] input) {
		return read(null, input);
	}

	private Collection<ReadListener> listeners = new ArrayList<ReadListener>();

	@Override
	public void addListener(ReadListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		listeners.remove(listener);
	}

}
