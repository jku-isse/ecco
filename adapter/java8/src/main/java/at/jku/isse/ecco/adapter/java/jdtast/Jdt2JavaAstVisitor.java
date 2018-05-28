package at.jku.isse.ecco.adapter.java.jdtast;

import at.jku.isse.ecco.adapter.java.JavaTreeArtifactData;
import at.jku.isse.ecco.adapter.java.TODO;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.jku.isse.ecco.adapter.java.JavaTreeArtifactData.NodeType.*;

public class Jdt2JavaAstVisitor extends SingleJDTNodeAstVisitor {

    private final Node.Op parentEccoNode;
    private final Function<JavaTreeArtifactData, Node.Op> newNode;
    private final BiConsumer<Artifact.Op<?>, ASTNode> referenceCheckingConsumer;
    private final BiConsumer<ASTNode, Node.Op> recursiveReadAst;

    public Jdt2JavaAstVisitor(Node.Op parentEccoNode, Function<JavaTreeArtifactData, Node.Op> newNode, BiConsumer<Artifact.Op<?>, ASTNode> referenceCheckingConsumer, BiConsumer<ASTNode, Node.Op> recursiveReadAst) {
        this.parentEccoNode = parentEccoNode;
        this.newNode = newNode;
        this.referenceCheckingConsumer = referenceCheckingConsumer;
        this.recursiveReadAst = recursiveReadAst;
    }

    private void TODO(ASTNode node) {
        throw new TODO("No JDT --> Java implementation for " + node.getClass().getCanonicalName() + "!");
    }

    private static final Supplier<RuntimeException> java9NotSupported = () -> new IllegalStateException("Java 9 is not supported!");


    public boolean visit(MethodDeclaration node) {
        //Does not need an ID
        JavaTreeArtifactData methodDeclarationData = new JavaTreeArtifactData();
        methodDeclarationData.setType(METHOD_DECLARATION);
        SimpleName methodName = node.getName();

        final Node.Op methodNode = newNode.apply(methodDeclarationData);
        calculateLineNumbers(node, methodNode);
        parentEccoNode.addChild(methodNode);
        List<SingleVariableDeclaration> singleVariableDeclarationList = node.parameters();
        String parameters = getStringFromVariableDeclaration(singleVariableDeclarationList, methodNode.getArtifact());

        handleModifiers(node.modifiers(), methodNode);
        final Type returnType = node.getReturnType2();

        handleGenericParameters(node.typeParameters(), methodNode);

        String beforeChildren = (returnType == null ? "" : returnType.toString() + " ") + methodName.getIdentifier() + "(" + parameters + ")";

        referenceCheckingConsumer.accept(methodNode.getArtifact(), node);
        referenceCheckingConsumer.accept(methodNode.getArtifact(), returnType);

        List<Type> throwsList = node.thrownExceptionTypes();
        if (!throwsList.isEmpty()) {
            JavaTreeArtifactData throwsArtifactData = new JavaTreeArtifactData();
            throwsArtifactData.setType(JavaTreeArtifactData.NodeType.THROWS_LIST);
            throwsArtifactData.setOrdered(false);
            throwsArtifactData.setDataAsString(" throws ");
            final Node.Op throwsNode = newNode.apply(throwsArtifactData);
            for (Type cur : throwsList) {
                referenceCheckingConsumer.accept(throwsNode.getArtifact(), cur);
                final JavaTreeArtifactData typeArtifact = artifactFromSimpleNode(cur);
                throwsNode.addChild(newNode.apply(typeArtifact));
            }

            methodNode.addChild(throwsNode);
        }
        methodDeclarationData.setDataAsString(beforeChildren);

        recursiveReadAst.accept(node.getBody(), methodNode);
        return super.visit(node);
    }

    private void handleModifiers(List<IExtendedModifier> modifiers, Node.Op forNode) {
        JavaTreeArtifactData modifiersData = new JavaTreeArtifactData();
        modifiersData.setType(MODIFIER);
        modifiersData.setOrdered(true);
        final Node.Op modifierNode = newNode.apply(modifiersData);
        forNode.addChildren(modifierNode);
        for (IExtendedModifier extendedModifier : modifiers) {
            if (extendedModifier instanceof ASTNode) {
                calculateLineNumbers((ASTNode) extendedModifier, modifierNode);
            }
            if (extendedModifier instanceof Annotation) {
                Annotation annotation = (Annotation) extendedModifier;
                referenceCheckingConsumer.accept(forNode.getArtifact(), annotation);
            }

            JavaTreeArtifactData data = new JavaTreeArtifactData();
            data.setType(SIMPLE_JUST_A_STRING);
            data.setDataAsString(extendedModifier.toString());
            final Node.Op dataNode = newNode.apply(data);
            modifierNode.addChildren(dataNode);
        }
    }

    private void handleGenericParameters(List<TypeParameter> typeParameterList, Node.Op parentNode) {
        if (typeParameterList.isEmpty())
            return;
        JavaTreeArtifactData genericInfoData = new JavaTreeArtifactData();
        genericInfoData.setType(GENERIC_TYPE_INFO);
        genericInfoData.setOrdered(true);
        final Node.Op genericInfoNode = newNode.apply(genericInfoData);
        parentNode.addChild(genericInfoNode);
        typeParameterList.forEach(node -> {
            final JavaTreeArtifactData data = artifactFromSimpleNode(node);
            final Node.Op node1 = newNode.apply(data);
            genericInfoNode.addChild(node1);
            referenceCheckingConsumer.accept(node1.getArtifact(), node);
        });
    }


    private String getStringFromVariableDeclaration(List<SingleVariableDeclaration> singleVariableDeclarationList, Artifact.Op<?> artifact) {
        StringJoiner stringJoiner = new StringJoiner(",");
        for (SingleVariableDeclaration svd : singleVariableDeclarationList) {
            referenceCheckingConsumer.accept(artifact, svd);
            stringJoiner.add(svd.toString());
        }
        return stringJoiner.toString();
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        //Does not need an ID
        JavaTreeArtifactData typeDeclarationData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        typeDeclarationData.setType(ANNOTATIONTYPE_DECLARATION);
        body.setType(BLOCK);
        final Node.Op typeDeclarationNode = newNode.apply(typeDeclarationData), bodyNode = newNode.apply(body);
        calculateLineNumbers(node, typeDeclarationNode);
        parentEccoNode.addChild(typeDeclarationNode);
        typeDeclarationNode.addChild(bodyNode);
        referenceCheckingConsumer.accept(typeDeclarationNode.getArtifact(), node);
        handleModifiers(node.modifiers(), typeDeclarationNode);
        typeDeclarationData.setDataAsString(" @interface " + node.getName());

        ((List<BodyDeclaration>) node.bodyDeclarations()).forEach(bodyDeclaration -> recursiveReadAst.accept(bodyDeclaration, bodyNode));

        return super.visit(node);
    }

    @Override
    public boolean visit(AnnotationTypeMemberDeclaration node) {
        //Does not need an ID
        JavaTreeArtifactData memberData = new JavaTreeArtifactData();
        memberData.setType(ANNOTATIONMEMBER);
        memberData.setDataAsString(node.getType() + " " + node.getName() + "()");
        final Node.Op memberNode = newNode.apply(memberData);
        calculateLineNumbers(node, memberNode);
        referenceCheckingConsumer.accept(memberNode.getArtifact(), node);
        parentEccoNode.addChildren(memberNode);
        final Expression nodeDefault = node.getDefault();
        if (nodeDefault != null) {
            JavaTreeArtifactData defaultData = new JavaTreeArtifactData();
            defaultData.setType(ANNOTATIONMEMBER_DEFAULT);
            final Node.Op defaultNode = newNode.apply(defaultData);
            memberNode.addChild(defaultNode);
            recursiveReadAst.accept(nodeDefault, defaultNode);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(AnonymousClassDeclaration node) {
        //Does not need an ID
        JavaTreeArtifactData anonymousClasDeclaration = new JavaTreeArtifactData();
        anonymousClasDeclaration.setType(ANONYMOUS_CLASS_DECLARATION);
        final Node.Op anonymousClassDeclarationNode = newNode.apply(anonymousClasDeclaration);
        calculateLineNumbers(node, anonymousClassDeclarationNode);
        referenceCheckingConsumer.accept(anonymousClassDeclarationNode.getArtifact(), node);
        parentEccoNode.addChild(anonymousClassDeclarationNode);

        ((List<BodyDeclaration>) node.bodyDeclarations()).forEach(declaration -> recursiveReadAst.accept(declaration, anonymousClassDeclarationNode));


        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayAccess node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayCreation node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayInitializer node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayType node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(AssertStatement node) {
        JavaTreeArtifactData assertData = new JavaTreeArtifactData(), assertMessageData = new JavaTreeArtifactData();
        assertData.setType(STATEMENT_ASSERT);
        assertMessageData.setType(STATEMENT_ASSERT_MESSAGE);

        final Node.Op assertNode = newNode.apply(assertData), assertMessageNode = newNode.apply(assertMessageData);
        calculateLineNumbers(node, assertNode);
        parentEccoNode.addChild(assertNode);
        assertNode.addChild(assertMessageNode);
        referenceCheckingConsumer.accept(assertNode.getArtifact(), node);

        recursiveReadAst.accept(node.getMessage(), assertMessageNode);
        //ID of root node should be the assertion condition
        //assertData.setDataAsString(node.getExpression().toString().trim());
		assertData.setDataAsString(node.toString());
        return super.visit(node);
    }

    @Override
    public boolean visit(Assignment node) {
        final JavaTreeArtifactData assignmentData = new JavaTreeArtifactData();
        //ID of assignment should be the node on the left
        assignmentData.setDataAsString(node.getLeftHandSide() + node.getOperator().toString());
        assignmentData.setType(ASSIGNMENT);
        final JavaTreeArtifactData before = new JavaTreeArtifactData(), after = new JavaTreeArtifactData();
        before.setType(BEFORE);
        after.setType(AFTER);

        Node.Op assignmentNode = newNode.apply(assignmentData), beforeNode = newNode.apply(before), afterNode = newNode.apply(after);
        calculateLineNumbers(node, assignmentNode);
        parentEccoNode.addChild(assignmentNode);
        assignmentNode.addChildren(beforeNode, afterNode);


        recursiveReadAst.accept(node.getRightHandSide(), afterNode);

        return super.visit(node);
    }

    @Override
    public boolean visit(Block node) {
        JavaTreeArtifactData treeArtifactData = new JavaTreeArtifactData();
        treeArtifactData.setOrdered(true);
        treeArtifactData.setType(BLOCK);
        final Node.Op blockNode = newNode.apply(treeArtifactData);
        calculateLineNumbers(node, blockNode);
        parentEccoNode.addChild(blockNode);
        referenceCheckingConsumer.accept(blockNode.getArtifact(), node);

        ((List<Statement>) node.statements()).forEach(child -> recursiveReadAst.accept(child, blockNode));
        return super.visit(node);
    }

    @Override
    public boolean visit(BlockComment node) {
        // No handling of comments
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean visit(BooleanLiteral node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(BreakStatement node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(CastExpression node) {
        JavaTreeArtifactData castData = new JavaTreeArtifactData();
        castData.setType(EXPRESSION_CAST);
        Type castType = node.getType();
        castData.setDataAsString('(' + castType.toString() + ')');
        final Node.Op castNode = newNode.apply(castData);
        calculateLineNumbers(node, castNode);
        parentEccoNode.addChild(castNode);
        referenceCheckingConsumer.accept(castNode.getArtifact(), node);
        recursiveReadAst.accept(node.getExpression(), castNode);

        return super.visit(node);
    }

    @Override
    public boolean visit(CatchClause node) {
        JavaTreeArtifactData catchClauseData = new JavaTreeArtifactData(), bodyData = new JavaTreeArtifactData();
        catchClauseData.setType(CATCH);
        catchClauseData.setDataAsString(node.getException().toString());
        bodyData.setType(AFTER);

        final Node.Op catchNode = newNode.apply(catchClauseData), bodyNode = newNode.apply(bodyData);
        calculateLineNumbers(node, catchNode);
        parentEccoNode.addChild(catchNode);
        catchNode.addChild(bodyNode);
        referenceCheckingConsumer.accept(catchNode.getArtifact(), node);

        recursiveReadAst.accept(node.getBody(), bodyNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(CharacterLiteral node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        JavaTreeArtifactData newData = new JavaTreeArtifactData();
        newData.setType(CLASS_INSTANCE_CREATION);
        newData.setDataAsString(node.getType().toString());
        final Node.Op newClassNode = newNode.apply(newData);
        calculateLineNumbers(node, newClassNode);
        parentEccoNode.addChild(newClassNode);
        referenceCheckingConsumer.accept(newClassNode.getArtifact(), node);
        Type type = node.getType();
        referenceCheckingConsumer.accept(newClassNode.getArtifact(), type);
        recursiveReadAst.accept(node.getAnonymousClassDeclaration(), newClassNode);

        JavaTreeArtifactData parameters = new JavaTreeArtifactData();
        parameters.setOrdered(true);
        parameters.setType(PARAMETERS);
        final Node.Op argumentNode = newNode.apply(parameters);
        newClassNode.addChild(argumentNode);

        List<Expression> arguments = node.arguments();
        for (Expression p : arguments)
            recursiveReadAst.accept(p, argumentNode);

        return super.visit(node);
    }


    @Override
    public boolean visit(final CompilationUnit node) {
        final List<AbstractTypeDeclaration> types = (List<AbstractTypeDeclaration>) node.types();
        //Check if errors are detected
        IProblem[] problems = node.getProblems();
        boolean notCompiling = Arrays.stream(problems).anyMatch(p -> p.getMessage().startsWith("Syntax error"));
        if (notCompiling)
            throw new Error("Compilation problem detected at" + types.toString(), new Error(Arrays.toString(problems)));
        //No new node needs to be generated
        final PackageDeclaration packageDeclaration = node.getPackage();
        if (packageDeclaration != null)
            visit(packageDeclaration);
        List<ImportDeclaration> imports = (List<ImportDeclaration>) node.imports();
        if (imports != null)
            imports.forEach(this::visit);

        calculateLineNumbers(node, parentEccoNode);
        types.forEach(abstractTypeDeclaration -> recursiveReadAst.accept(abstractTypeDeclaration, parentEccoNode));
        referenceCheckingConsumer.accept(parentEccoNode.getArtifact(), node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ConditionalExpression node) {
        JavaTreeArtifactData meta = new JavaTreeArtifactData(), condition = new JavaTreeArtifactData(), ifBranch = new JavaTreeArtifactData(), elseBranch = new JavaTreeArtifactData();
        meta.setType(EXPRESSION_TRENARY);
        condition.setType(CONDITION);
        ifBranch.setType(BEFORE);
        elseBranch.setType(AFTER);

        final Node.Op metaNode = newNode.apply(meta), conditionNode = newNode.apply(condition), ifNode = newNode.apply(ifBranch), elseNode = newNode.apply(elseBranch);
        calculateLineNumbers(node, metaNode);
        parentEccoNode.addChild(metaNode);
        metaNode.addChildren(conditionNode, ifNode, elseNode);
        recursiveReadAst.accept(node.getExpression(), conditionNode);
        recursiveReadAst.accept(node.getThenExpression(), ifNode);
        recursiveReadAst.accept(node.getElseExpression(), elseNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(CreationReference node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(Dimension node) {
        JavaTreeArtifactData dimensionData = new JavaTreeArtifactData();
        dimensionData.setType(DIMENSION);
        final Node.Op dimensionNode = newNode.apply(dimensionData);
        calculateLineNumbers(node, dimensionNode);
        parentEccoNode.addChild(dimensionNode);
        handleModifiers(node.annotations(), dimensionNode);
        TODO(node); // Needs test
        return super.visit(node);
    }

    @Override
    public boolean visit(DoStatement node) {
        JavaTreeArtifactData whileData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        whileData.setType(LOOP_DO_WHILE);
        body.setType(AFTER);

        final Node.Op whileNode = newNode.apply(whileData), bodyNode = newNode.apply(body);
        calculateLineNumbers(node, whileNode);
        parentEccoNode.addChild(whileNode);
        whileNode.addChild(bodyNode);

        // Condition of the do-while is the ID
        whileData.setDataAsString(node.getExpression().toString());
        recursiveReadAst.accept(node.getBody(), bodyNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(EmptyStatement node) {
        JavaTreeArtifactData emptyStatement = new JavaTreeArtifactData();
        emptyStatement.setDataAsString(";");
        final Node.Op emptyStatementNode = newNode.apply(emptyStatement);
        calculateLineNumbers(node, emptyStatementNode);
        parentEccoNode.addChild(emptyStatementNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        JavaTreeArtifactData enhancedForData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        enhancedForData.setType(LOOP_ENHANCED_FOR);
        body.setType(AFTER);
        final SingleVariableDeclaration parameter = node.getParameter();

        //The ID of the enhanced for should be the element, which it iterates over
        enhancedForData.setDataAsString(node.getParameter() + ":" + node.getExpression());

        Node.Op enhancedForNode = newNode.apply(enhancedForData), bodyNode = newNode.apply(body);
        calculateLineNumbers(node, enhancedForNode);
        parentEccoNode.addChild(enhancedForNode);
        enhancedForNode.addChild(bodyNode);
        referenceCheckingConsumer.accept(enhancedForNode.getArtifact(), parameter);
        referenceCheckingConsumer.accept(enhancedForNode.getArtifact(), parameter.getType());

        recursiveReadAst.accept(node.getBody(), bodyNode);

        return super.visit(node);
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        final JavaTreeArtifactData enumDeclarationData = new JavaTreeArtifactData(), implementsData = new JavaTreeArtifactData(), enumConstants = new JavaTreeArtifactData(),
                bodyDeclerationData = new JavaTreeArtifactData();
        enumDeclarationData.setType(ENUM_DECLARATION);

        implementsData.setType(DECLARATION_IMPLEMENTS);

        enumConstants.setType(ENUM_CONSTANTS);
        enumConstants.setOrdered(true);

        bodyDeclerationData.setType(AFTER);

        final Node.Op enumNode = newNode.apply(enumDeclarationData), implementsNode = newNode.apply(implementsData),
                enumConstantsNode = newNode.apply(enumConstants), bodyDeclarations = newNode.apply(bodyDeclerationData);
        calculateLineNumbers(node, enumNode);
        parentEccoNode.addChild(enumNode);
        enumNode.addChildren(implementsNode, enumConstantsNode, bodyDeclarations);

        referenceCheckingConsumer.accept(enumNode.getArtifact(), node);
        handleModifiers(node.modifiers(), enumNode);
        enumDeclarationData.setDataAsString(" enum " + node.getName());

        ((List<Type>) node.superInterfaceTypes()).forEach(type -> recursiveReadAst.accept(type, implementsNode));
        ((List<EnumConstantDeclaration>) node.enumConstants()).forEach(enumConstantDeclaration -> recursiveReadAst.accept(enumConstantDeclaration, enumConstantsNode));
        ((List<BodyDeclaration>) node.bodyDeclarations()).forEach(bodyDeclaration -> recursiveReadAst.accept(bodyDeclaration, bodyDeclarations));

        return super.visit(node);
    }

    @Override
    public boolean visit(ExportsDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(ExpressionMethodReference node) {
        handleMethodReference(node);
        return super.visit(node);
    }

    private void handleMethodReference(MethodReference methodReference) {
        performAction(methodReference);
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        JavaTreeArtifactData expressionStatementData = new JavaTreeArtifactData();
        expressionStatementData.setType(STATEMENT_EXPRESSION);
        final Node.Op expressionStatementNode = newNode.apply(expressionStatementData);
        calculateLineNumbers(node, expressionStatementNode);
        parentEccoNode.addChild(expressionStatementNode);
        recursiveReadAst.accept(node.getExpression(), expressionStatementNode);
        //Create ID for parent node
        final JavaTreeArtifactData childData = (JavaTreeArtifactData) expressionStatementNode.getChildren().get(0).getArtifact().getData();

        String id = childData.getType().ordinal() + "-" + childData.getDataAsString();
        //expressionStatementData.setDataAsString(id);
		expressionStatementData.setDataAsString(node.toString());
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldAccess node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        JavaTreeArtifactData fieldDeclarationData = new JavaTreeArtifactData();
        fieldDeclarationData.setType(FIELD_DECLARATION);
        final Node.Op fieldCreationNode = newNode.apply(fieldDeclarationData);
        calculateLineNumbers(node, fieldCreationNode);
        parentEccoNode.addChild(fieldCreationNode);
        handleModifiers(node.modifiers(), fieldCreationNode);
        final Type type = node.getType();

        referenceCheckingConsumer.accept(fieldCreationNode.getArtifact(), node);
        referenceCheckingConsumer.accept(fieldCreationNode.getArtifact(), type);

        fieldDeclarationData.setDataAsString(type.toString());

        ((List<VariableDeclarationFragment>) node.fragments()).forEach(fragment -> recursiveReadAst.accept(fragment, fieldCreationNode));

        return super.visit(node);
    }

    @Override
    public boolean visit(ForStatement node) {
        JavaTreeArtifactData forData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        forData.setType(LOOP_FOR);
        body.setType(AFTER);

        final Node.Op forNode = newNode.apply(forData);
        calculateLineNumbers(node, forNode);
        final Node.Op bodyNode = newNode.apply(body);
        parentEccoNode.addChild(forNode);
        forNode.addChild(bodyNode);

        StringJoiner sj = new StringJoiner(";");
        final Stream<String> inits = node.initializers().stream().map(e -> e.toString());
        sj.add(inits.collect(Collectors.joining(",")));
        sj.add(Optional.ofNullable(node.getExpression()).map(e -> e.toString()).orElse(""));
        final Stream<String> updaters = node.updaters().stream().map(e -> e.toString());
        sj.add(updaters.collect(Collectors.joining(",")));
        forData.setDataAsString(sj.toString());

        recursiveReadAst.accept(node.getBody(), bodyNode);

        return super.visit(node);
    }

    @Override
    public boolean visit(IfStatement node) {
//        Node.Op iffsNode;
//        {
//            JavaTreeArtifactData parentArtifactData = (JavaTreeArtifactData) parentEccoNode.getArtifact().getData();
//            if (parentArtifactData.getType() == STATEMENT_IFFS) {
//                iffsNode = parentEccoNode;
//            } else {
//                JavaTreeArtifactData iffs = new JavaTreeArtifactData();
//                iffs.setOrdered(true);
//                iffs.setType(STATEMENT_IFFS);
//                iffsNode = newNode.apply(iffs);
//                parentEccoNode.addChild(iffsNode);
//            }
//        }

        JavaTreeArtifactData ifData = new JavaTreeArtifactData();
        ifData.setType(STATEMENT_IF);
        ifData.setDataAsString("if(" + node.getExpression() + ")");

        final Node.Op ifNode = newNode.apply(ifData);
        calculateLineNumbers(node, ifNode);
        parentEccoNode.addChild(ifNode);

        recursiveReadAst.accept(node.getThenStatement(), ifNode);

        final Statement elseStatement = node.getElseStatement();
        if (elseStatement != null) {
            JavaTreeArtifactData.NodeType elseStatementType = elseStatement instanceof IfStatement ? STATEMENT_IF : STATEMENT_ELSE;

            if (elseStatementType == STATEMENT_IF) {
                // else if
                // insert "duzmmy else" node without any children
                JavaTreeArtifactData elseData = new JavaTreeArtifactData();
                elseData.setDataAsString("else");
                elseData.setType(STATEMENT_ELSE);
                final Node.Op elseNode = newNode.apply(elseData);
                calculateLineNumbers(elseStatement, elseNode);
                parentEccoNode.addChild(elseNode);
                // add "if child" to "parentEccoNode"
                recursiveReadAst.accept(elseStatement, parentEccoNode);
            } else {
                // else
                // add else node with block as child
                JavaTreeArtifactData elseData = new JavaTreeArtifactData();
                elseData.setDataAsString("else");
                elseData.setType(STATEMENT_ELSE);
                final Node.Op elseNode = newNode.apply(elseData);
                calculateLineNumbers(elseStatement, elseNode);
                parentEccoNode.addChild(elseNode);

                recursiveReadAst.accept(elseStatement, elseNode);
            }

        }
        return super.visit(node);
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        performAction(node);
        return super.visit(node);
    }

    //TODO maybe more fine granular handling
    @Override
    public boolean visit(InfixExpression node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(Initializer node) {
        JavaTreeArtifactData data = new JavaTreeArtifactData();
        data.setType(BLOCK);
        data.setOrdered(true);
        final Node.Op initializerNode = newNode.apply(data);
        parentEccoNode.addChild(initializerNode);
        calculateLineNumbers(node, initializerNode);

        handleModifiers(node.modifiers(), initializerNode);

        ((List<Statement>) node.getBody().statements()).forEach(it -> recursiveReadAst.accept(it, initializerNode));

        return super.visit(node);
    }

    @Override
    public boolean visit(InstanceofExpression node) {
        JavaTreeArtifactData data = new JavaTreeArtifactData();
        data.setDataAsString(" instanceof ");
        data.setType(ASSIGNMENT);
        JavaTreeArtifactData before = new JavaTreeArtifactData(), after = new JavaTreeArtifactData();

        before.setType(BEFORE);
        after.setType(AFTER);

        final Node.Op instanceOfNode = newNode.apply(data), beforeNode = newNode.apply(before), afterNode = newNode.apply(after);
        calculateLineNumbers(node, instanceOfNode);

        parentEccoNode.addChild(instanceOfNode);
        instanceOfNode.addChildren(beforeNode, afterNode);

        recursiveReadAst.accept(node.getLeftOperand(), beforeNode);
        recursiveReadAst.accept(node.getRightOperand(), afterNode);

        referenceCheckingConsumer.accept(instanceOfNode.getArtifact(), node);
        referenceCheckingConsumer.accept(instanceOfNode.getArtifact(), node.getRightOperand());

        return super.visit(node);
    }

    @Override
    public boolean visit(IntersectionType node) {
        throw new IllegalStateException("Not needed in current implementation");
    }

    @Override
    public boolean visit(Javadoc node) {
        throw new IllegalStateException("Not supported in current implementation");
    }

    @Override
    public boolean visit(LabeledStatement node) {
        JavaTreeArtifactData label = new JavaTreeArtifactData();
        label.setType(SIMPLE_JUST_A_STRING);
        label.setDataAsString(node.getLabel() + ":");
        Node.Op eccoNode = newNode.apply(label);
        calculateLineNumbers(node, eccoNode);
        parentEccoNode.addChild(eccoNode);
        recursiveReadAst.accept(node.getBody(), parentEccoNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(LambdaExpression node) {
        //Does not need an ID, no 2 lambdas following each other
        JavaTreeArtifactData lambdaData = new JavaTreeArtifactData(), parametersData = new JavaTreeArtifactData();
        lambdaData.setType(LAMBDA);
        parametersData.setType(LAMBDA_PARAMETERS);

        final Node.Op lambdaNode = newNode.apply(lambdaData), parameterNode = newNode.apply(parametersData);
        parentEccoNode.addChild(lambdaNode);
        lambdaNode.addChild(parameterNode);
        calculateLineNumbers(node, lambdaNode);

        final List<VariableDeclaration> parameters = node.parameters();

        parameters.forEach(parameter -> recursiveReadAst.accept(parameter, parameterNode));


        lambdaData.setDataAsString(Boolean.toString(node.hasParentheses()));
        ASTNode body = node.getBody();

        recursiveReadAst.accept(body, lambdaNode);
        referenceCheckingConsumer.accept(lambdaNode.getArtifact(), node);

        return super.visit(node);
    }

    @Override
    public boolean visit(LineComment node) {
        throw new IllegalStateException("Comments are not supported in current implementation");
    }

    @Override
    public boolean visit(MarkerAnnotation node) {
        throw new IllegalStateException("Not needed in current implementation!");
    }

    @Override
    public boolean visit(MemberRef node) {
        throw new IllegalStateException("Comments are currently not supported!");
    }

    @Override
    public boolean visit(MemberValuePair node) {
        TODO(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(MethodRef node) {
        throw new IllegalStateException("Comments are not supported!");
    }

    @Override
    public boolean visit(MethodRefParameter node) {
        throw new IllegalStateException("Comments are not supported!");
    }


    @Override
    public boolean visit(MethodInvocation node) {
        JavaTreeArtifactData methodInvocationData = new JavaTreeArtifactData();
        methodInvocationData.setOrdered(true);
        methodInvocationData.setType(METHOD_INVOCATION);

        final Node.Op methodInvocationNode = newNode.apply(methodInvocationData);
        calculateLineNumbers(node, methodInvocationNode);
        parentEccoNode.addChild(methodInvocationNode);

        Expression e = node.getExpression();

        if (e == null || e instanceof Name || e instanceof ThisExpression) {
            String base = e == null ? "" : e.toString() + ".";
            methodInvocationData.setDataAsString(base + node.getName().toString());
        } else {
            methodInvocationData.setDataAsString(node.getName().toString());
            JavaTreeArtifactData expressionBefore = new JavaTreeArtifactData();
            expressionBefore.setType(BEFORE);
            final Node.Op expressionNode = newNode.apply(expressionBefore);
            methodInvocationNode.addChild(expressionNode);
            recursiveReadAst.accept(e, expressionNode);

        }

        final List<Expression> arguments = node.arguments();

        JavaTreeArtifactData argumentData = new JavaTreeArtifactData();
        argumentData.setOrdered(true);
        argumentData.setType(PARAMETERS);
        final Node.Op argumentNode = newNode.apply(argumentData);
        methodInvocationNode.addChild(argumentNode);
        arguments.forEach(argument -> recursiveReadAst.accept(argument, argumentNode));
        return super.visit(node);
    }

    @Override
    public boolean visit(Modifier node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ModuleDeclaration node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(ModuleModifier node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(NameQualifiedType node) {
        TODO(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(NormalAnnotation node) {
        throw new IllegalStateException("Not needed in current implementation");
    }

    @Override
    public boolean visit(NullLiteral node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(NumberLiteral node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(OpensDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ParameterizedType node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ParenthesizedExpression node) {
        //Does not really need an ID
        JavaTreeArtifactData expressionData = new JavaTreeArtifactData();
        expressionData.setType(EXPRESSION_PARENTHESIS);
        final Node.Op expressionNode = newNode.apply(expressionData);
        calculateLineNumbers(node, expressionNode);
        parentEccoNode.addChild(expressionNode);
        recursiveReadAst.accept(node.getExpression(), expressionNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(PostfixExpression node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(PrefixExpression node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ProvidesDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(PrimitiveType node) {
        performAction(node);
        TODO(node); //Needs testing
        return super.visit(node);
    }

    @Override
    public boolean visit(QualifiedName node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(QualifiedType node) {
        performAction(node);
        TODO(node); //Needs testing
        return super.visit(node);
    }

    @Override
    public boolean visit(RequiresDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(ReturnStatement node) {
        //Does not need an ID
        final JavaTreeArtifactData returnData = new JavaTreeArtifactData();
        returnData.setType(STATEMENT_RETURN);
        returnData.setDataAsString(node.toString());
        final Node.Op returnNode = newNode.apply(returnData);
        calculateLineNumbers(node, returnNode);
        parentEccoNode.addChild(returnNode);
        recursiveReadAst.accept(node.getExpression(), returnNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(SimpleName node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SimpleType node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SingleMemberAnnotation node) {
        throw new IllegalStateException("Not needed in current implementation");
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(StringLiteral node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperFieldAccess node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        JavaTreeArtifactData methodInvocationData = new JavaTreeArtifactData();
        methodInvocationData.setDataAsString("super." + node.getName()); //Might need something more dynamic than super
        methodInvocationData.setOrdered(true);
        methodInvocationData.setType(METHOD_INVOCATION);
        final Node.Op methodInvocationNode = newNode.apply(methodInvocationData);
        calculateLineNumbers(node, methodInvocationNode);
        parentEccoNode.addChild(methodInvocationNode);

        final List<Expression> arguments = node.arguments();

        JavaTreeArtifactData argumentData = new JavaTreeArtifactData();
        argumentData.setType(PARAMETERS);
        final Node.Op argumentNode = newNode.apply(argumentData);
        methodInvocationNode.addChild(argumentNode);
        arguments.forEach(argument -> recursiveReadAst.accept(argument, argumentNode));
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodReference node) {
        handleMethodReference(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SwitchCase node) {
        throw new IllegalStateException("SwitchCase parsing not expected!");
    }

    @Override
    public boolean visit(SwitchStatement node) {
        JavaTreeArtifactData switchData = new JavaTreeArtifactData();
        switchData.setOrdered(true);
        switchData.setType(SWITCH_SWITCH);
        switchData.setDataAsString("switch(" + node.getExpression() + ")");
        final Node.Op switchNode = newNode.apply(switchData);
        calculateLineNumbers(node, switchNode);
        parentEccoNode.addChild(switchNode);
        final List<Statement> statements = node.statements();
        Node.Op curNode = null;
        for (Statement cur : statements) {
            if (cur instanceof SwitchCase) {
                JavaTreeArtifactData curData = new JavaTreeArtifactData();
                curData.setDataAsString(cur.toString().trim());
                curData.setType(SWITCH_CASE);
                curData.setOrdered(true);
                curNode = newNode.apply(curData);
                switchNode.addChild(curNode);
            } else if (curNode != null) {
                recursiveReadAst.accept(cur, curNode);
            } else
                throw new IllegalStateException("Unexpected first child of switch statement:" + System.lineSeparator() + node);
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
        JavaTreeArtifactData synchronizedData = new JavaTreeArtifactData();
        synchronizedData.setType(SYNCHRONIZED_STATEMENT);
        synchronizedData.setDataAsString(node.getExpression().toString());
        final Node.Op synchronizedNode = newNode.apply(synchronizedData);
        calculateLineNumbers(node, synchronizedNode);
        parentEccoNode.addChild(synchronizedNode);
        recursiveReadAst.accept(node.getBody(), synchronizedNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(TagElement node) {
        throw new IllegalStateException("Comments are not supported in the current implementation");
    }

    @Override
    public boolean visit(TextElement node) {
        throw new IllegalStateException("Comments are not supported in the current implementation");
    }

    @Override
    public boolean visit(ThisExpression node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ThrowStatement node) {
        JavaTreeArtifactData throwData = new JavaTreeArtifactData();
        throwData.setType(THROW_STATEMENT);
        final Node.Op throwNode = newNode.apply(throwData);
        calculateLineNumbers(node, throwNode);
        parentEccoNode.addChild(throwNode);
        recursiveReadAst.accept(node.getExpression(), throwNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(TryStatement node) {
        //No ID needed here
        final JavaTreeArtifactData metaData = new JavaTreeArtifactData(), try7Data = new JavaTreeArtifactData(), catchMeta = new JavaTreeArtifactData(),
                finallyData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        metaData.setType(TRY_META);

        try7Data.setType(TRY_RESSOURCES);

        catchMeta.setType(CATCH_META);
        catchMeta.setOrdered(true);

        finallyData.setType(FINALLY);

        body.setType(AFTER);

        Node.Op metaNode = newNode.apply(metaData), try7Node = newNode.apply(try7Data), catchNode = newNode.apply(catchMeta),
                finallyNode = newNode.apply(finallyData), bodyNode = newNode.apply(body);
        calculateLineNumbers(node, metaNode);

        parentEccoNode.addChild(metaNode);
        metaNode.addChildren(try7Node, catchNode, finallyNode, bodyNode);

        metaData.setDataAsString("try");
        finallyData.setDataAsString("finally");

        ((List<Expression>) node.resources()).forEach(expression -> recursiveReadAst.accept(expression, try7Node));
        recursiveReadAst.accept(node.getBody(), bodyNode);
        ((List<CatchClause>) node.catchClauses()).forEach(catchClause -> recursiveReadAst.accept(catchClause, catchNode));
        Optional.ofNullable(node.getFinally()).ifPresent(finallyAstNode -> recursiveReadAst.accept(finallyAstNode, finallyNode));


        return super.visit(node);
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        JavaTreeArtifactData typeDeclarationData = new JavaTreeArtifactData(), extendsDataMeta = new JavaTreeArtifactData(),
                implementsDataMeta = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        typeDeclarationData.setType(TYPE_DECLARATION);
        extendsDataMeta.setType(DECLARATION_EXTENDS);
        implementsDataMeta.setType(DECLARATION_IMPLEMENTS);
        body.setType(AFTER);
        body.setOrdered(true);

        Node.Op typeDeclarationNode = newNode.apply(typeDeclarationData), extendsNode = newNode.apply(extendsDataMeta),
                implementsNode = newNode.apply(implementsDataMeta), bodyNode = newNode.apply(body);
        parentEccoNode.addChildren(typeDeclarationNode);
        calculateLineNumbers(node, typeDeclarationNode);
        typeDeclarationNode.addChildren(extendsNode, implementsNode, bodyNode);

        handleModifiers(node.modifiers(), typeDeclarationNode);
        referenceCheckingConsumer.accept(typeDeclarationNode.getArtifact(), node);

        final boolean isInterface = node.isInterface();
        String keyword = (isInterface ? "interface" : "class");

        handleGenericParameters(node.typeParameters(), typeDeclarationNode);

        typeDeclarationData.setDataAsString(keyword + " " + node.getName());

        recursiveReadAst.accept(node.getSuperclassType(), extendsNode);

        ((List<Type>) node.superInterfaceTypes()).forEach(type -> recursiveReadAst.accept(type, isInterface ? extendsNode : implementsNode));

        ((List<BodyDeclaration>) node.bodyDeclarations()).forEach(bodyDeclaration -> recursiveReadAst.accept(bodyDeclaration, bodyNode));

        return super.visit(node);
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        final AbstractTypeDeclaration declaration = node.getDeclaration();
        if (declaration instanceof TypeDeclaration)
            visit((TypeDeclaration) declaration);
        else if (declaration instanceof EnumDeclaration)
            visit((EnumDeclaration) declaration);
        else
            throw new IllegalStateException("Subclass not expected!");
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeLiteral node) {
        performAction(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeMethodReference node) {
        handleMethodReference(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeParameter node) {
        throw new IllegalStateException("Not needed in current implementation");
    }

    @Override
    public boolean visit(UnionType node) {
        throw new IllegalStateException("Not used in current implementation. Used in catch");
    }

    @Override
    public boolean visit(UsesDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        JavaTreeArtifactData variableDeclaration = new JavaTreeArtifactData();
        variableDeclaration.setType(EXPRESSION_VARIABLE_DECLARATION);
        variableDeclaration.setOrdered(true);
        final Node.Op variableDeclarationNode = newNode.apply(variableDeclaration);
        calculateLineNumbers(node, variableDeclarationNode);
        parentEccoNode.addChildren(variableDeclarationNode);
        handleModifiers(node.modifiers(), variableDeclarationNode);
        Type type = node.getType();

        referenceCheckingConsumer.accept(variableDeclarationNode.getArtifact(), node);
        referenceCheckingConsumer.accept(variableDeclarationNode.getArtifact(), type);


        variableDeclaration.setDataAsString(type.toString());

        variableDeclaration.setOrdered(true);

        ((List<VariableDeclarationFragment>) node.fragments()).forEach(fragment -> recursiveReadAst.accept(fragment, variableDeclarationNode));
        return super.visit(node);
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        JavaTreeArtifactData variableDeclaration = new JavaTreeArtifactData();
        variableDeclaration.setType(STATEMENT_VARIABLE_DECLARATION);
        variableDeclaration.setOrdered(true);
        variableDeclaration.setDataAsString(node.toString());
        final Node.Op variableDeclarationNode = newNode.apply(variableDeclaration);
        calculateLineNumbers(node, variableDeclarationNode);
        parentEccoNode.addChild(variableDeclarationNode);
        handleModifiers(node.modifiers(), variableDeclarationNode);
        final Type type = node.getType();

        referenceCheckingConsumer.accept(variableDeclarationNode.getArtifact(), node);
        referenceCheckingConsumer.accept(variableDeclarationNode.getArtifact(), type);

        String before = type.toString();

        variableDeclaration.setDataAsString(before);

        ((List<VariableDeclarationFragment>) node.fragments()).forEach(fragment -> recursiveReadAst.accept(fragment, variableDeclarationNode));
        return super.visit(node);
    }

    @Override
    public boolean visit(VariableDeclarationFragment node) {
        JavaTreeArtifactData variableDeclarationFragment = new JavaTreeArtifactData();
        variableDeclarationFragment.setType(VARIABLE_DECLARATION_FRAGMENT);
        Expression fragment = node.getInitializer();

        variableDeclarationFragment.setDataAsString(node.getName() + (fragment == null ? "" : "="));

        final Node.Op variableDeclarationFragmentNode = newNode.apply(variableDeclarationFragment);
        calculateLineNumbers(node, variableDeclarationFragmentNode);
        parentEccoNode.addChild(variableDeclarationFragmentNode);

        recursiveReadAst.accept(fragment, variableDeclarationFragmentNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(WhileStatement node) {
        JavaTreeArtifactData whileData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        whileData.setType(LOOP_WHILE);
        whileData.setDataAsString(node.getExpression().toString());
        body.setType(AFTER);

        final Node.Op whileNode = newNode.apply(whileData), bodyNode = newNode.apply(body);
        calculateLineNumbers(node, whileNode);
        parentEccoNode.addChild(whileNode);
        whileNode.addChild(bodyNode);
        recursiveReadAst.accept(node.getBody(), bodyNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(WildcardType node) {
        throw new IllegalStateException("Not used in current implementation!");
    }

    private JavaTreeArtifactData artifactFromSimpleNode(ASTNode astNode) {
        JavaTreeArtifactData javaTreeArtifactData = new JavaTreeArtifactData();
        javaTreeArtifactData.setType(SIMPLE_JUST_A_STRING);
        javaTreeArtifactData.setDataAsString(astNode.toString().trim());
        return javaTreeArtifactData;
    }

    private void performAction(ASTNode node) {
        JavaTreeArtifactData data = artifactFromSimpleNode(node);
        Node.Op a = newNode.apply(data);
        referenceCheckingConsumer.accept(a.getArtifact(), node);
        calculateLineNumbers(node, a);
        parentEccoNode.addChild(a);
    }


    private static String LINE_START = "LINE_START", LINE_END = "LINE_END";

    private void calculateLineNumbers(ASTNode astNode, Node.Op eccoNode) {
        CompilationUnit cu = (CompilationUnit) astNode.getRoot();
        if (cu == astNode) {
            eccoNode.putProperty(LINE_START, 0);
            eccoNode.putProperty(LINE_END, 0);
        } else {
            int start = astNode.getStartPosition(),
                    end = start + astNode.getLength();
            eccoNode.putProperty(LINE_START, cu.getLineNumber(start));
            eccoNode.putProperty(LINE_END, cu.getLineNumber(end));
        }
    }
}
