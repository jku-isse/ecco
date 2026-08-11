package at.jku.cdl.ecco.adapter.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithBlockStmt;
import com.github.javaparser.ast.nodeTypes.NodeWithBody;
import com.github.javaparser.ast.nodeTypes.NodeWithExpression;
import com.github.javaparser.ast.nodeTypes.NodeWithMembers;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;

import at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTConstructorData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTModuleData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTSimpleStringData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTTryData;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.tree.Node;

public class JavaASTWriteHandler {
	private static final Logger LOGGER = Logger.getLogger(JavaASTWriteHandler.class.getName());

	/**
	 * StaticJavaParser.getParserConfiguration() is backed by a ThreadLocal (a fresh, default
	 * ParserConfiguration - languageLevel JAVA_11 - per thread that has never set one), so setting
	 * this once in a static initializer would only take effect on whichever thread happened to
	 * trigger class loading, not necessarily the thread that later calls write() (e.g. a background
	 * commit/import Task). Set explicitly at the top of both entry points below instead. Matches
	 * JavaASTReader's PARSER_CONFIGURATION (see its javadoc for why JAVA_18, not higher).
	 */
	private static void configureStaticJavaParser() {
		StaticJavaParser.setConfiguration(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_18));
	}

	public static void writeJavaFile(Node fileRoot, Path outputPath) {
		configureStaticJavaParser();
		CompilationUnit cu = new CompilationUnit();
		for (Node child : fileRoot.getChildren()) {
			if (child.getArtifact().getData() instanceof JavaASTData) {
				JavaASTData astData = (JavaASTData) child.getArtifact().getData();
				if (astData.getType() == ASTNodeType.PACKAGEDECLARATION) {
					JavaASTSimpleStringData cuData = (JavaASTSimpleStringData) astData;
					if (!cuData.getData().isEmpty()) {
						cu.setPackageDeclaration(cuData.getData());
					}
				}
			}
		}
		List<ImportDeclaration> imports = fileRoot.getChildren().stream().map(n -> n.getArtifact().getData())
				.filter(JavaASTData.class::isInstance).map(JavaASTData.class::cast)
				.filter(data -> data.getType() == ASTNodeType.IMPORT_DECLARATION)
				.map(idata -> StaticJavaParser.parseImport(idata.toString())).collect(Collectors.toList());
		cu.setImports(new NodeList<>(imports));
		fileRoot.getChildren().forEach(c -> addNode(c, cu));
		try {
			Files.write(outputPath, cu.toString().getBytes(), StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void writeJavaString(Node root, String outputString) {
		configureStaticJavaParser();
		CompilationUnit cu = new CompilationUnit();
		for (Node child : root.getChildren()) {
			if (child.getArtifact().getData() instanceof JavaASTData) {
				JavaASTData astData = (JavaASTData) child.getArtifact().getData();
				if (astData.getType() == ASTNodeType.PACKAGEDECLARATION) {
					JavaASTSimpleStringData cuData = (JavaASTSimpleStringData) astData;
					if (!cuData.getData().isEmpty()) {
						cu.setPackageDeclaration(cuData.getData());
					}
				}
			}
		}
		List<ImportDeclaration> imports = root.getChildren().stream().map(n -> n.getArtifact().getData())
				.filter(JavaASTData.class::isInstance).map(JavaASTData.class::cast)
				.filter(data -> data.getType() == ASTNodeType.IMPORT_DECLARATION)
				.map(idata -> StaticJavaParser.parseImport(idata.toString())).collect(Collectors.toList());
		cu.setImports(new NodeList<>(imports));
		root.getChildren().forEach(c -> addNode(c, cu));
		outputString = cu.toString();
		
	}

	private static com.github.javaparser.ast.Node addNode(Node child, com.github.javaparser.ast.Node parent) {
		if (child.getArtifact().getData() instanceof JavaASTData) {
			JavaASTData childData = (JavaASTData) child.getArtifact().getData();
			switch (childData.getType()) {
			case IMPORT_DECLARATION:
			case PACKAGEDECLARATION:
				break;
			case CONSTRUCTOR_DECLARATION:
				return addConstructor(child, parent);
			case MODULE_DECLARATION:
				return addModuleDeclaration(child, parent);
			case ENUM_CONSTANTS:
				break;
			case ENUM_DECLARATION:
				return addEnumDeclaration(child, parent);
			case EXPRESSION:
				return addExpression(child, parent);
			case FIELD_GROUP:
				return addFieldGroup(child, parent);
			case FIELD_DECLARATION:
				return addFieldDeclaration(child, parent);
			case INITIALIZER_DECLARATION:
				return addInitializerDeclaration(child, parent);
			case IF_STATEMENT:
				return addIfStatement(child, parent);
			case METHOD_DECLARATION:
				return addMethodDeclaration(child, parent);
			case STATEMENT:
				return addStatement(child, parent);
			case SWITCH_STATEMENT:
				return addSwitchStatement(child, parent);
			case TRYBLOCK:
				return addTryBlock(child, parent);
			case TYPE_DECLARATION:
				return addTypeDeclaration(child, parent);
			case UNKNOWN:
				return errorUnkown(child, parent);
			default:
				LOGGER.info("Writer: " + child.getArtifact().toString() + " not handled in addNode, might be ok!");
				break;
			}
		}
		return null;
	}

	private static com.github.javaparser.ast.Node addFieldGroup(Node child, com.github.javaparser.ast.Node parent) {
		child.getChildren().forEach(c -> addNode(c, parent));
		return parent;
	}

	private static com.github.javaparser.ast.Node errorUnkown(Node child, com.github.javaparser.ast.Node parent) {
		LOGGER.severe("Encountered node with unkown type:" + child);
		return new EmptyStmt();
	}

	private static Statement addStatement(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTSimpleStringData stmtData = (JavaASTSimpleStringData) child.getArtifact().getData();
		Statement stmt = new EmptyStmt();
		try {
			stmt = StaticJavaParser.parseStatement(stmtData.getData());
		}catch(ParseProblemException e) {
			throw new EccoException("JavaPlugin - Writer Error - Cannot parse statement: "+stmtData.getData(), e);
		}
		if (parent instanceof NodeWithBlockStmt<?>) {
			NodeWithBlockStmt<?> blockParent = (NodeWithBlockStmt<?>) parent;
			BlockStmt body = blockParent.getBody();
			if (body == null) {
				body = blockParent.createBody();
			}
			body.addStatement(stmt);
		} else if (parent instanceof NodeWithBody<?>) {
			NodeWithBody<?> bodyParent = (NodeWithBody<?>) parent;
			Statement body = bodyParent.getBody();
			if (body == null || body instanceof EmptyStmt) {
				if (child.getParent().getChildren().size() > 1) {
					body = bodyParent.createBlockStatementAsBody();
					((BlockStmt) body).addStatement(stmt);
				} else {
					bodyParent.setBody(stmt);
				}
			} else if (body instanceof BlockStmt) {
				((BlockStmt) body).addStatement(stmt);
			} else {
				throw new UnsupportedOperationException(
						"Missing case - Node: " + stmt.toString() + " || Parent:" + parent.getClass().getName());
			}

		} else if (parent instanceof NodeWithStatements<?>) {
			NodeWithStatements<?> stmtsParent = (NodeWithStatements<?>) parent;
			stmtsParent.addStatement(stmt);
		} else {
			throw new UnsupportedOperationException(
					"Missing case - Node: " + stmt.toString() + " || Parent:" + parent.getClass().getName());
		}
		Statement lambdaStmt = stmt;
		child.getChildren().forEach(c -> addNode(c, lambdaStmt));
		return stmt;
	}

	private static void addStatement(Statement child, com.github.javaparser.ast.Node parent) {
		if (parent instanceof NodeWithBlockStmt<?>) {
			NodeWithBlockStmt<?> blockParent = (NodeWithBlockStmt<?>) parent;
			BlockStmt body = blockParent.getBody();
			if (body == null) {
				body = blockParent.createBody();
			}
			body.addStatement(child);
		} else if (parent instanceof NodeWithBody<?>) {
			NodeWithBody<?> bodyParent = (NodeWithBody<?>) parent;
			Statement body = bodyParent.getBody();
			if (body == null || body instanceof EmptyStmt) {
				if (child instanceof BlockStmt) {
					bodyParent.setBody(child);
				} else {
					body = bodyParent.createBlockStatementAsBody();
					((BlockStmt) body).addStatement(child);
				}
			} else if (body instanceof BlockStmt) {
				((BlockStmt) body).addStatement(child);
			} else {
				throw new UnsupportedOperationException(
						"Missing case - Node: " + child.toString() + " || Parent:" + parent.getClass().getName());
			}

		} else if (parent instanceof NodeWithStatements<?>) {
			NodeWithStatements<?> stmtsParent = (NodeWithStatements<?>) parent;
			stmtsParent.addStatement(child);
		} else {
			throw new UnsupportedOperationException(
					"Missing case - Node: " + child.toString() + " || Parent:" + parent.getClass().getName());
		}
	}

	private static MethodDeclaration addMethodDeclaration(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTSimpleStringData methodDeclData = (JavaASTSimpleStringData) child.getArtifact().getData();
		MethodDeclaration methodDecl = StaticJavaParser.parseMethodDeclaration(methodDeclData.getData());
		BlockStmt body = new BlockStmt();
		child.getChildren().forEach(c -> addNode(c, body));
		
		/* In case of an abstract method, we must not add a method body. If we do so, an abstract method gets an empty
		 * body which results in compilation errors.*/
		if (!methodDecl.isAbstract() && !isInterfaceMethod(child, parent)) {
			methodDecl.setBody(body);
		} 
		if (parent instanceof TypeDeclaration<?>) {
			((TypeDeclaration<?>) parent).addMember(methodDecl);
		} else {
			throw new UnsupportedOperationException(
					"Missing case - Node: " + methodDeclData.toString() + " || Parent:" + parent.getClass().getName());
		}
		return methodDecl;
	}
	
	private static boolean isInterfaceMethod(Node child, com.github.javaparser.ast.Node parent) {
		if (parent instanceof ClassOrInterfaceDeclaration) {
			ClassOrInterfaceDeclaration _parent = (ClassOrInterfaceDeclaration) parent;
			if(_parent.isInterface() && child.getChildren().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static FieldDeclaration addFieldDeclaration(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTSimpleStringData childData = (JavaASTSimpleStringData) child.getArtifact().getData();
		FieldDeclaration parsedFd = StaticJavaParser.parseBodyDeclaration(childData.getData()).asFieldDeclaration();
		if (parent instanceof NodeWithMembers<?>) {
			((NodeWithMembers<?>) parent).addMember(parsedFd);
		} else {
			throw new UnsupportedOperationException(
					"Missing case - Node: " + childData.toString() + " || Parent:" + parent.getClass().getName());
		}
		return parsedFd;
	}

	private static InitializerDeclaration addInitializerDeclaration(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTSimpleStringData childData = (JavaASTSimpleStringData) child.getArtifact().getData();
		InitializerDeclaration parsedInit = StaticJavaParser.parseBodyDeclaration(childData.getData())
				.asInitializerDeclaration();
		if (parent instanceof NodeWithMembers<?>) {
			((NodeWithMembers<?>) parent).addMember(parsedInit);
		} else {
			throw new UnsupportedOperationException(
					"Missing case - Node: " + childData.toString() + " || Parent:" + parent.getClass().getName());
		}
		return parsedInit;
	}

	private static Expression addExpression(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTSimpleStringData childData = (JavaASTSimpleStringData) child.getArtifact().getData();
		Expression expression = StaticJavaParser.parseExpression(childData.getData());
		if (parent instanceof NodeWithExpression<?>) {
			((NodeWithExpression<?>) parent).setExpression(expression);
		} else {
			throw new UnsupportedOperationException(
					"Missing case - Node: " + childData.toString() + " || Parent:" + parent.getClass().getName());
		}
		return expression;
	}

	/** See JavaASTReader.addModuleDeclaration() for the reverse (read) direction. */
	private static ModuleDeclaration addModuleDeclaration(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTModuleData data = (JavaASTModuleData) child.getArtifact().getData();
		ModuleDeclaration module = new ModuleDeclaration(StaticJavaParser.parseName(data.getName()), data.isOpen());
		for (Node directiveNode : child.getChildren()) {
			Object raw = directiveNode.getArtifact().getData();
			if (raw instanceof JavaASTSimpleStringData) {
				module.getDirectives().add(StaticJavaParser.parseModuleDirective(((JavaASTSimpleStringData) raw).getData()));
			}
		}
		if (parent instanceof CompilationUnit) {
			((CompilationUnit) parent).setModule(module);
		}
		return module;
	}

	private static BodyDeclaration<?> addConstructor(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTConstructorData childData = (JavaASTConstructorData) child.getArtifact().getData();
		if (!(parent instanceof TypeDeclaration<?>)) {
			return null;
		}
		Keyword[] modifiers = childData.getModifiers().stream().map(mod -> Keyword.valueOf(mod.toUpperCase()))
				.collect(Collectors.toList()).toArray(new Keyword[0]);
		BlockStmt body = new BlockStmt();
		child.getChildren().forEach(c -> addNode(c, body));

		if (childData.isCompact()) {
			// A record's compact constructor has no explicit parameter list (implicitly the
			// record's components), so childData.getParameters() is never used here - see
			// JavaASTReader.extractConstructors()'s RecordDeclaration branch.
			CompactConstructorDeclaration compactDecl = new CompactConstructorDeclaration();
			compactDecl.setName(childData.getName());
			compactDecl.setBody(body);
			compactDecl.addModifier(modifiers);
			childData.getTypeParameters().forEach(tp -> compactDecl.addTypeParameter(tp));
			childData.getThrowExceptions()
					.forEach(te -> compactDecl.addThrownException(StaticJavaParser.parseType(te).asReferenceType()));
			childData.getAnnotations().forEach(a -> compactDecl.addAnnotation(StaticJavaParser.parseAnnotation(a)));
			((TypeDeclaration<?>) parent).addMember(compactDecl);
			return compactDecl;
		}

		ConstructorDeclaration constDecl = ((TypeDeclaration<?>) parent).addConstructor(modifiers);
		constDecl.setBody(body);
		childData.getParameters().forEach(p -> constDecl.addParameter(StaticJavaParser.parseParameter(p)));
		childData.getTypeParameters().forEach(tp -> constDecl.addTypeParameter(tp));
		childData.getThrowExceptions()
				.forEach(te -> constDecl.addThrownException(StaticJavaParser.parseType(te).asReferenceType()));
		childData.getAnnotations().forEach(a -> constDecl.addAnnotation(StaticJavaParser.parseAnnotation(a)));
		return constDecl;
	}

	/**
	 * StaticJavaParser.parseTypeDeclaration()'s TypeDeclarationParseStart grammar entry point has no
	 * "record" production at all - independent of configured LanguageLevel, this is a hard gap in
	 * that specific entry point, confirmed empirically (parsing the exact same text as a full
	 * compilation unit succeeds and does include the RecordDeclaration). Parsing as a throwaway
	 * compilation unit instead and pulling the single resulting top-level type back out works around
	 * it uniformly for every TypeDeclaration subtype, not just records.
	 */
	private static TypeDeclaration<?> parseTypeDeclarationText(String text) {
		return StaticJavaParser.parse(text).getTypes().get(0);
	}

	private static EnumDeclaration addEnumDeclaration(Node child, com.github.javaparser.ast.Node parent) {
		TypeDeclaration<?> type = parseTypeDeclarationText(child.getArtifact().toString());
		EnumDeclaration enumType = type.asEnumDeclaration();
		List<Node> enumConstDataNodes = child.getChildren().stream()
				.filter(c -> JavaASTData.class.isInstance(c.getArtifact().getData()))
				.filter(data -> ((JavaASTData) data.getArtifact().getData()).getType() == ASTNodeType.ENUM_CONSTANTS)
				.collect(Collectors.toList());
		for (Node enumDataNode : enumConstDataNodes) {
			EnumConstantDeclaration enumConstDecl = new EnumConstantDeclaration(
					enumDataNode.getArtifact().getData().toString());
			List<Expression> arguments = enumDataNode.getChildren().stream().map(n -> n.getArtifact().getData())
					.filter(JavaASTData.class::isInstance).map(JavaASTData.class::cast)
					.filter(data -> data.getType() == ASTNodeType.EXPRESSION)
					.map(expdata -> StaticJavaParser.<Expression>parseExpression(expdata.toString()))
					.collect(Collectors.toList());
			List<BodyDeclaration<?>> classBodies = enumDataNode.getChildren().stream()
					.map(n -> n.getArtifact().getData()).filter(JavaASTData.class::isInstance)
					.map(JavaASTData.class::cast).filter(data -> data.getType() == ASTNodeType.BODY_DECLARATION)
					.map(expdata -> StaticJavaParser.parseBodyDeclaration(expdata.toString()))
					.collect(Collectors.toList());
			enumConstDecl.setArguments(new NodeList<>(arguments));
			enumConstDecl.setClassBody(new NodeList<>(classBodies));
			enumType.addEntry(enumConstDecl);
		}

		child.getChildren().stream().filter(c -> JavaASTData.class.isInstance(c.getArtifact().getData()))
				.filter(data -> ((JavaASTData) data.getArtifact().getData()).getType() != ASTNodeType.ENUM_CONSTANTS)
				.forEach(a -> addNode(a, type));

		if (parent instanceof CompilationUnit) {
			((CompilationUnit) parent).addType(enumType);
		} else if (parent instanceof TypeDeclaration<?>) {
			((TypeDeclaration<?>) parent).addMember(enumType);
		}
		return enumType;
	}

	private static TypeDeclaration<?> addTypeDeclaration(Node child, com.github.javaparser.ast.Node parent) {
		JavaASTSimpleStringData childData = (JavaASTSimpleStringData) child.getArtifact().getData();
		TypeDeclaration<?> type = parseTypeDeclarationText(childData.getData());
		if (parent instanceof CompilationUnit) {
			((CompilationUnit) parent).addType(type);
		} else if (parent instanceof TypeDeclaration<?>) {
			((TypeDeclaration<?>) parent).addMember(type);
		} else {
			throw new UnsupportedOperationException("Class found on unexpected location! Developer: ADD NEW CASE");
		}
		child.getChildren().forEach(c -> addNode(c, type));
		return type;
	}

	private static IfStmt addIfStatement(Node child, com.github.javaparser.ast.Node parent) {
		List<Node> conditionNodes = child.getChildren().stream()
				.filter(data -> ((JavaASTData) data.getArtifact().getData()).getType() == ASTNodeType.IF_CONDITION)
				.collect(Collectors.toList());
		IfStmt ifStmt = new IfStmt();
		addStatement(ifStmt, parent);
		for (int i = 0; i < conditionNodes.size(); i++) {
			addIfCondition(conditionNodes.get(i), ifStmt);
			if (i + 1 < conditionNodes.size()) {
				IfStmt cascadeIf = new IfStmt();
				ifStmt.setElseStmt(cascadeIf);
				ifStmt = cascadeIf;
			}
		}
		return ifStmt;
	}

	private static void addIfCondition(Node child, IfStmt parent) {
		Expression e = StaticJavaParser.<Expression>parseExpression(child.getArtifact().getData().toString());
		parent.setCondition(e);
		List<Node> thenBranch = getThenBranch(child);
		if (thenBranch != null) {
			BlockStmt thenBlock = new BlockStmt();
			parent.setThenStmt(thenBlock);
			thenBranch.forEach(tb -> addNode(tb, thenBlock));
		} else {
			parent.setThenStmt(new EmptyStmt());
		}
		Node elseBranch = getElseBranch(child);
		if (elseBranch != null) {
			BlockStmt elseBlock = new BlockStmt();
			parent.setElseStmt(elseBlock);
			elseBranch.getChildren().forEach(eb -> addNode(eb, elseBlock));
		}
	}

	private static List<Node> getThenBranch(Node child) {
		return child.getChildren().stream().filter(cn -> cn.getArtifact().getData() instanceof JavaASTData)
				.filter(d -> ((JavaASTData) d.getArtifact().getData()).getType() != ASTNodeType.ELSE_BRANCH)
				.collect(Collectors.toList());
	}

	private static Node getElseBranch(Node child) {
		return child.getChildren().stream().filter(cn -> cn.getArtifact().getData() instanceof JavaASTData)
				.filter(d -> ((JavaASTData) d.getArtifact().getData()).getType() == ASTNodeType.ELSE_BRANCH).findAny()
				.orElse(null);
	}

	private static TryStmt addTryBlock(Node child, com.github.javaparser.ast.Node parent) {
		TryStmt tryStmt = new TryStmt();
		JavaASTTryData tryData = (JavaASTTryData) child.getArtifact().getData();
		if (!tryData.getExpressions().isEmpty()) {
			NodeList<Expression> resourceList = new NodeList<>();
			for (String catchParam : tryData.getExpressions()) {
				Expression e = StaticJavaParser.parseExpression(catchParam);
				resourceList.add(e);
			}
			tryStmt.setResources(resourceList);
		}
		BlockStmt body = new BlockStmt();
		tryStmt.setTryBlock(body);
		child.getChildren().stream()
				.filter(ncn -> ((JavaASTData) ncn.getArtifact().getData()).getType() != ASTNodeType.CATCHCLAUSE)
				.forEach(nc -> addNode(nc, body));
		child.getChildren().stream()
				.filter(ccn -> ((JavaASTData) ccn.getArtifact().getData()).getType() == ASTNodeType.CATCHCLAUSE)
				.forEach(cc -> addCatchClause(cc, tryStmt));
		if (tryData.hasFinally()) {
			Node finalNode = child.getChildren().stream()
					.filter(cn -> cn.getArtifact().getData() instanceof JavaASTData)
					.filter(d -> ((JavaASTData) d.getArtifact().getData()).getType() == ASTNodeType.FINALLY).findAny()
					.orElse(null);
			if (finalNode != null) {
				BlockStmt finalBlock = StaticJavaParser.parseBlock(finalNode.getArtifact().toString());
				tryStmt.setFinallyBlock(finalBlock);
			}
		}
		addStatement(tryStmt, parent);
		return tryStmt;
	}

	private static void addCatchClause(Node cc, TryStmt tryStmt) {
		NodeList<CatchClause> catchClauses = new NodeList<>();
		if (tryStmt.getCatchClauses() != null) {
			catchClauses = tryStmt.getCatchClauses();
		}
		if (cc.getArtifact().getData() instanceof JavaASTData
				&& ((JavaASTData) cc.getArtifact().getData()).getType() == ASTNodeType.CATCHCLAUSE) {
			JavaASTSimpleStringData ccData = (JavaASTSimpleStringData) cc.getArtifact().getData();
			CatchClause catchClause = new CatchClause();
			Parameter param = StaticJavaParser.parseParameter(ccData.getData());
			catchClause.setParameter(param);
			cc.getChildren().forEach(c -> addNode(c, catchClause));
			catchClauses.add(catchClause);
		}
		tryStmt.setCatchClauses(catchClauses);
	}

	private static SwitchStmt addSwitchStatement(Node child, com.github.javaparser.ast.Node parent) {
		SwitchStmt switchStmt = new SwitchStmt();
		Expression e = StaticJavaParser.parseExpression(child.getArtifact().getData().toString());
		switchStmt.setSelector(e);
		NodeList<SwitchEntry> entries = new NodeList<>();
		for (Node swEntryNode : child.getChildren()) {
			JavaASTSimpleStringData entryData = (JavaASTSimpleStringData) swEntryNode.getArtifact().getData();
			SwitchEntry entry = new SwitchEntry();
			if (!entryData.getData().equals("DEFAULT")) {
				String[] labelStrs = entryData.getData().split(",");
				NodeList<Expression> labels = new NodeList<>();
				Arrays.stream(labelStrs).forEach(l -> labels.add(StaticJavaParser.parseExpression(l)));
				entry.setLabels(labels);
			} else {
				Optional<SwitchEntry> def = entries.stream().filter(se -> se.getLabels().size() == 0).findAny();
				if (def.isPresent()) {
					entry = def.get();
				}
			}
			SwitchEntry encloseEntry = entry;
			swEntryNode.getChildren().forEach(stmts -> addNode(stmts, encloseEntry));
			entries.add(entry);
		}
		switchStmt.setEntries(entries);
		addStatement(switchStmt, parent);
		return switchStmt;
	}
}
