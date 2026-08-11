package at.jku.cdl.ecco.adapter.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import com.google.inject.Inject;

import at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTConstructorData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTModuleData;
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
	// outright (and text blocks/module-info.java worse - see below). JAVA_18 is the highest
	// non-preview level this JavaParser version (3.25.8) offers; it does NOT cover the Java 21
	// finalization of pattern-matching switch/record patterns, so those remain unsupported - a
	// JavaParser version bump would be needed to close that gap (tracked as follow-up).
	private static final ParserConfiguration PARSER_CONFIGURATION = new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_18);

	private final EntityFactory entityFactory;
	private final PrettyPrinterConfiguration PPC;

	@Inject
	public JavaASTReader(final EntityFactory entityFactory) {
		this.entityFactory = entityFactory;
		PPC = new PrettyPrinterConfiguration();
		PPC.setPrintComments(false);
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
				LOGGER.severe("Unable to parse java file: " + javafile.toString());
				LOGGER.severe(e.getMessage());
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
				Node.Op fieldNode = this.entityFactory.createNode(fdArtifact);
				fieldNode.putProperty(PROPERTY_LINE_START, getStartLine(fd));
				fieldNode.putProperty(PROPERTY_LINE_END, getEndLine(fd));
				fieldsGroupNode.addChild(fieldNode);
			}
		}
		extractConstructors(clazz).forEach(c -> clazzNode.addChild(c));
		extractMethods(clazz).forEach(c -> clazzNode.addChild(c));
		clazz.getMembers().stream().filter(BodyDeclaration::isInitializerDeclaration)
				.map(InitializerDeclaration.class::cast).forEach(init -> addInitializer(init, clazzNode));
		clazz.getMembers().stream().filter(BodyDeclaration::isTypeDeclaration).map(TypeDeclaration.class::cast)
				.forEach(type -> addTypeDeclaration(type, clazzNode));
		parent.addChild(clazzNode);
	}

	private void addInitializer(InitializerDeclaration init, Op clazzNode) {
		JavaASTSimpleStringData initData = new JavaASTSimpleStringData(init.toString(PPC));
		initData.setType(ASTNodeType.INITIALIZER_DECLARATION);
		Artifact.Op<JavaASTSimpleStringData> initArtifact = this.entityFactory.createArtifact(initData);
		Node.Op initNode = this.entityFactory.createOrderedNode(initArtifact);
		clazzNode.addChild(initNode);
	}

	private void addEnumConstants(EnumDeclaration enumdec, Op clazzNode) {
		for (EnumConstantDeclaration ecd : enumdec.getEntries()) {
			JavaASTSimpleStringData enumData = new JavaASTSimpleStringData(ecd.getNameAsString());
			enumData.setType(ASTNodeType.ENUM_CONSTANTS);
			Artifact.Op<JavaASTSimpleStringData> enumArtifact = this.entityFactory.createArtifact(enumData);
			Node.Op enumNode = this.entityFactory.createOrderedNode(enumArtifact);
			enumNode.putProperty(PROPERTY_LINE_START, getStartLine(ecd));
			enumNode.putProperty(PROPERTY_LINE_END, getEndLine(ecd));
			clazzNode.addChild(enumNode);
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

	private int getEndLine(com.github.javaparser.ast.Node node) {
		return node.getEnd().orElse(new Position(-1, -1)).line;
	}

	private int getStartLine(com.github.javaparser.ast.Node node) {
		return node.getBegin().orElse(new Position(-1, -1)).line;
	}

	private Collection<Node.Op> extractConstructors(TypeDeclaration<?> clazz) {
		List<Node.Op> constrNodes = new ArrayList<>(clazz.getConstructors().size());
		for (ConstructorDeclaration constructor : clazz.getConstructors()) {
			JavaASTConstructorData constrData = new JavaASTConstructorData(constructor.getNameAsString());
			Artifact.Op<JavaASTConstructorData> constrArtifact = this.entityFactory.createArtifact(constrData);
			Node.Op constrNode = this.entityFactory.createOrderedNode(constrArtifact);
			constrNode.putProperty(PROPERTY_LINE_START, getStartLine(constructor));
			constrNode.putProperty(PROPERTY_LINE_END, getEndLine(constructor));
			constructor.getModifiers().forEach(m -> constrData.addModifier(m.getKeyword().asString()));
			constructor.getParameters().forEach(p -> constrData.addParameter(p.toString(PPC)));
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
				JavaASTConstructorData constrData = new JavaASTConstructorData(compact.getNameAsString());
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

	private void addChildren(Statement body, Node.Op parent) {
		if (body instanceof NodeWithBlockStmt<?>) {
			addChildren(((NodeWithBlockStmt<?>) body).getBody(), parent);
		} else if (body instanceof NodeWithBody<?>) {
			Statement tmp = body.clone();
			((NodeWithBody<?>)tmp).setBody(new EmptyStmt());
			JavaASTSimpleStringData sdData = new JavaASTSimpleStringData(tmp.toString(PPC));
			sdData.setType(ASTNodeType.STATEMENT);
			Artifact.Op<JavaASTSimpleStringData> sdArtifact = this.entityFactory.createArtifact(sdData);
			Node.Op node = this.entityFactory.createOrderedNode(sdArtifact);
			node.putProperty(PROPERTY_LINE_START, getStartLine(body));
			node.putProperty(PROPERTY_LINE_END, getEndLine(body));
			parent.addChild(node);
			addChildren(((NodeWithBody<?>) body).getBody(), node);
		} else if (body instanceof NodeWithStatements<?>) {
			((NodeWithStatements<?>) body).getStatements().forEach(stmt -> addChildren(stmt, parent));
		} else if (body instanceof IfStmt) {
			addIfStatement((IfStmt) body, parent);
		} else if (body instanceof SwitchNode) {
			addSwitch((SwitchNode) body, parent);
		} else if (body instanceof TryStmt) {
			addTryStatement((TryStmt) body, parent);
		} else {
			JavaASTSimpleStringData sdData = new JavaASTSimpleStringData(body.toString(PPC));
			sdData.setType(ASTNodeType.STATEMENT);
			Artifact.Op<JavaASTSimpleStringData> sdArtifact = this.entityFactory.createArtifact(sdData);
			Node.Op node = this.entityFactory.createNode(sdArtifact);
			node.putProperty(PROPERTY_LINE_START, getStartLine(body));
			node.putProperty(PROPERTY_LINE_END, getEndLine(body));
			parent.addChild(node);
		}

	}

	private void addTryStatement(TryStmt trystmt, Op parent) {
		boolean hasExpression = trystmt.getResources().size() > 0;

		JavaASTTryData tryData = new JavaASTTryData();
		tryData.setType(ASTNodeType.TRYBLOCK);
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
		}

		for (CatchClause cc : trystmt.getCatchClauses()) {
			if (!hasExpression) {
				tryData.addCatchParam(cc.getParameter().toString(PPC));
			}
			JavaASTSimpleStringData catchclause = new JavaASTSimpleStringData(cc.getParameter().toString(PPC));
			catchclause.setType(ASTNodeType.CATCHCLAUSE);
			Artifact.Op<JavaASTSimpleStringData> ccArtifact = this.entityFactory.createArtifact(catchclause);
			Node.Op ccNode = this.entityFactory.createOrderedNode(ccArtifact);
			ccNode.putProperty(PROPERTY_LINE_START, getStartLine(cc));
			ccNode.putProperty(PROPERTY_LINE_END, getEndLine(cc));
			addChildren(cc.getBody(), ccNode);
			tryNode.addChild(ccNode);
		}

		if (trystmt.getFinallyBlock().isPresent()) {
			JavaASTSimpleStringData finData = new JavaASTSimpleStringData(
					trystmt.getFinallyBlock().get().toString(PPC));
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
		Artifact.Op<JavaASTSimpleStringData> switchArtifact = this.entityFactory.createArtifact(switchData);
		Node.Op switchNode = this.entityFactory.createOrderedNode(switchArtifact);
		switchNode.putProperty(PROPERTY_LINE_START, getStartLine(sw.getSelector()));
		switchNode.putProperty(PROPERTY_LINE_END, getEndLine(sw.getSelector()));
		parent.addChild(switchNode);
		for (SwitchEntry se : sw.getEntries()) {
			String label = String.join(",",
					se.getLabels().stream().map(lab -> lab.toString(PPC)).collect(Collectors.toList()));
			if (label.equals("")) {
				label = "DEFAULT";
			}
			JavaASTSimpleStringData entryData = new JavaASTSimpleStringData(label);
			entryData.setType(ASTNodeType.SWITCH_ENTRIES);
			Artifact.Op<JavaASTSimpleStringData> entryArtifact = this.entityFactory.createArtifact(entryData);
			Node.Op entryNode = this.entityFactory.createOrderedNode(entryArtifact);
			entryNode.putProperty(PROPERTY_LINE_START, getStartLine(se));
			entryNode.putProperty(PROPERTY_LINE_END, getEndLine(se));
			switchNode.addChild(entryNode);
			se.getStatements().forEach(stmt -> addChildren(stmt, entryNode));
		}
	}

	private void addIfStatement(IfStmt ifstmt, Op parent) {
		JavaASTSimpleStringData ifData = new JavaASTSimpleStringData("if " + ifstmt.getCondition().toString(PPC));
		ifData.setType(ASTNodeType.IF_STATEMENT);
		Artifact.Op<JavaASTSimpleStringData> ifArtifact = this.entityFactory.createArtifact(ifData);
		Node.Op ifNode = this.entityFactory.createOrderedNode(ifArtifact);
		ifNode.putProperty(PROPERTY_LINE_START, getStartLine(ifstmt));
		ifNode.putProperty(PROPERTY_LINE_END, getEndLine(ifstmt));
		parent.addChild(ifNode);
		JavaASTSimpleStringData conditionData = new JavaASTSimpleStringData(ifstmt.getCondition().toString(PPC));
		conditionData.setType(ASTNodeType.IF_CONDITION);
		Artifact.Op<JavaASTSimpleStringData> conditionArtifact = this.entityFactory.createArtifact(conditionData);
		Node.Op conditionNode = this.entityFactory.createOrderedNode(conditionArtifact);
		addChildren(ifstmt.getThenStmt(), conditionNode);
		ifNode.addChild(conditionNode);
		if (ifstmt.hasCascadingIfStmt()) {
			addIfCondition((IfStmt) ifstmt.getElseStmt().get(), ifNode);
		} else if (ifstmt.hasElseBranch()) {
			JavaASTSimpleStringData elseData = new JavaASTSimpleStringData("else " + ifstmt.getCondition());
			elseData.setType(ASTNodeType.ELSE_BRANCH);
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
		for (ImportDeclaration id : cu.getImports()) {
			JavaASTSimpleStringData importData = new JavaASTSimpleStringData(id.toString(PPC));
			importData.setType(ASTNodeType.IMPORT_DECLARATION);
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
