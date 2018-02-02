package at.jku.isse.ecco.adapter.java.jdtast;

import at.jku.isse.ecco.adapter.java.JavaTreeArtifactData;
import at.jku.isse.ecco.adapter.java.TODO;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

        JavaTreeArtifactData methodDeclarationData = new JavaTreeArtifactData();
        methodDeclarationData.setType(METHOD_DECLARATION);
        SimpleName methodName = node.getName();

        final Node.Op methodNode = newNode.apply(methodDeclarationData);
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
        JavaTreeArtifactData typeDeclarationData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        typeDeclarationData.setType(ANNOTATIONTYPE_DECLARATION);
        body.setType(BLOCK);
        final Node.Op typeDeclarationNode = newNode.apply(typeDeclarationData), bodyNode = newNode.apply(body);
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
        JavaTreeArtifactData memberData = new JavaTreeArtifactData();
        memberData.setType(ANNOTATIONMEMBER);
        memberData.setDataAsString(node.getType() + " " + node.getName() + "()");
        final Node.Op memberNode = newNode.apply(memberData);
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
        JavaTreeArtifactData anonymousClasDeclaration = new JavaTreeArtifactData();
        anonymousClasDeclaration.setType(ANONYMOUS_CLASS_DECLARATION);
        final Node.Op anonymousClassDeclarationNode = newNode.apply(anonymousClasDeclaration);
        referenceCheckingConsumer.accept(anonymousClassDeclarationNode.getArtifact(), node);
        parentEccoNode.addChild(anonymousClassDeclarationNode);

        ((List<BodyDeclaration>) node.bodyDeclarations()).forEach(declaration -> recursiveReadAst.accept(declaration, anonymousClassDeclarationNode));


        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayAccess node) {
        final JavaTreeArtifactData arrayAccessData = artifactFromSimpleNode(node);
        final Node.Op arrayAccessNode = newNode.apply(arrayAccessData);
        referenceCheckingConsumer.accept(arrayAccessNode.getArtifact(), node);
        parentEccoNode.addChildren(arrayAccessNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayCreation node) {
        final JavaTreeArtifactData arrayCreationData = artifactFromSimpleNode(node);
        final Node.Op arrayCreationNode = newNode.apply(arrayCreationData);
        referenceCheckingConsumer.accept(arrayCreationNode.getArtifact(), node);
        parentEccoNode.addChild(arrayCreationNode);
        //No recursion
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayInitializer node) {
        final JavaTreeArtifactData data = artifactFromSimpleNode(node);
        final Node.Op eccoNode = newNode.apply(data);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), node);
        parentEccoNode.addChild(eccoNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayType node) {
        final Node.Op typeNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(typeNode);
        referenceCheckingConsumer.accept(typeNode.getArtifact(), node);
        return super.visit(node);
    }

    @Override
    public boolean visit(AssertStatement node) {
        JavaTreeArtifactData assertData = new JavaTreeArtifactData(), assertConditionData = new JavaTreeArtifactData(), assertMessageData = new JavaTreeArtifactData();
        assertData.setType(STATEMENT_ASSERT);

        assertConditionData.setType(STATEMENT_ASSERT_CONDITION);
        assertMessageData.setType(STATEMENT_ASSERT_MESSAGE);

        final Node.Op assertNode = newNode.apply(assertData), assertConditionNode = newNode.apply(assertConditionData), assertMessageNode = newNode.apply(assertMessageData);
        parentEccoNode.addChild(assertNode);
        assertNode.addChildren(assertConditionNode, assertMessageNode);
        referenceCheckingConsumer.accept(assertNode.getArtifact(), node);

        recursiveReadAst.accept(node.getExpression(), assertConditionNode);
        recursiveReadAst.accept(node.getMessage(), assertMessageNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(Assignment node) {
        final JavaTreeArtifactData assignmentData = new JavaTreeArtifactData();
        assignmentData.setDataAsString(node.getOperator().toString());
        assignmentData.setType(ASSIGNMENT);
        final JavaTreeArtifactData before = new JavaTreeArtifactData(), after = new JavaTreeArtifactData();
        before.setType(BEFORE);
        after.setType(AFTER);

        Node.Op assignmentNode = newNode.apply(assignmentData), beforeNode = newNode.apply(before), afterNode = newNode.apply(after);
        parentEccoNode.addChild(assignmentNode);
        assignmentNode.addChildren(beforeNode, afterNode);

        recursiveReadAst.accept(node.getLeftHandSide(), beforeNode);
        recursiveReadAst.accept(node.getRightHandSide(), afterNode);

        return super.visit(node);
    }

    @Override
    public boolean visit(Block node) {
        JavaTreeArtifactData treeArtifactData = new JavaTreeArtifactData();
        treeArtifactData.setOrdered(true);
        treeArtifactData.setType(BLOCK);
        final Node.Op blockNode = newNode.apply(treeArtifactData);
        parentEccoNode.addChild(blockNode);
        referenceCheckingConsumer.accept(blockNode.getArtifact(), node);

        ((List<Statement>) node.statements()).forEach(child -> recursiveReadAst.accept(child, blockNode));
        return super.visit(node);
    }

    @Override
    public boolean visit(BlockComment node) {
        // No handling of comments
        throw new NotImplementedException();
    }

    @Override
    public boolean visit(BooleanLiteral node) {
        final JavaTreeArtifactData boolData = artifactFromSimpleNode(node);
        final Node.Op boolNode = newNode.apply(boolData);
        parentEccoNode.addChild(boolNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(BreakStatement node) {
        final JavaTreeArtifactData breakData = artifactFromSimpleNode(node);
        final Node.Op breakNode = newNode.apply(breakData);
        parentEccoNode.addChild(breakNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(CastExpression node) {
        JavaTreeArtifactData castData = new JavaTreeArtifactData();
        castData.setType(EXPRESSION_CAST);
        Type castType = node.getType();
        castData.setDataAsString('(' + castType.toString() + ')');
        final Node.Op castNode = newNode.apply(castData);
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
        parentEccoNode.addChild(catchNode);
        catchNode.addChild(bodyNode);
        referenceCheckingConsumer.accept(catchNode.getArtifact(), node);

        recursiveReadAst.accept(node.getBody(), bodyNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(CharacterLiteral node) {
        final JavaTreeArtifactData characterArtifact = artifactFromSimpleNode(node);
        final Node.Op characterNode = newNode.apply(characterArtifact);
        parentEccoNode.addChild(characterNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        JavaTreeArtifactData newData = new JavaTreeArtifactData();
        newData.setType(CLASS_INSTANCE_CREATION);
        newData.setDataAsString(node.getType().toString());
        final Node.Op newClassNode = newNode.apply(newData);
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
        //Chefk if errors are detected
        IProblem[] problems = node.getProblems();
        boolean notCompiling = Arrays.stream(problems).anyMatch(p -> p.getMessage().startsWith("Syntax error"));
        if (notCompiling)
            throw new IllegalStateException("Compilation problem detected at" + types.toString(), new Error(Arrays.toString(problems)));
        //No new node needs to be generated
        final PackageDeclaration packageDeclaration = node.getPackage();
        if (packageDeclaration != null)
            visit(packageDeclaration);
        List<ImportDeclaration> imports = (List<ImportDeclaration>) node.imports();
        if (imports != null)
            imports.forEach(this::visit);
        // No new Ecco node should be created here TODO


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
        parentEccoNode.addChild(metaNode);
        metaNode.addChildren(conditionNode, ifNode, elseNode);
        recursiveReadAst.accept(node.getExpression(), conditionNode);
        recursiveReadAst.accept(node.getThenExpression(), ifNode);
        recursiveReadAst.accept(node.getElseExpression(), elseNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        //TODO is treating as simple node sufficient
        final JavaTreeArtifactData constructorData = artifactFromSimpleNode(node);
        final Node.Op constructorNode = newNode.apply(constructorData);
        parentEccoNode.addChild(constructorNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
        final Node.Op continueNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(continueNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(CreationReference node) {
        final Node.Op referenceNode = newNode.apply(artifactFromSimpleNode(node));
        referenceCheckingConsumer.accept(referenceNode.getArtifact(), node);
        parentEccoNode.addChild(referenceNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(Dimension node) {
        JavaTreeArtifactData dimensionData = new JavaTreeArtifactData();
        dimensionData.setType(DIMENSION);
        final Node.Op dimensionNode = newNode.apply(dimensionData);
        parentEccoNode.addChild(dimensionNode);
        handleModifiers(node.annotations(), dimensionNode);
        TODO(node); // Needs test
        return super.visit(node);
    }

    @Override
    public boolean visit(DoStatement node) {
        JavaTreeArtifactData whileData = new JavaTreeArtifactData(), condition = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        whileData.setType(LOOP_DO_WHILE);
        condition.setType(CONDITION);
        body.setType(AFTER);

        final Node.Op whileNode = newNode.apply(whileData), conditionNode = newNode.apply(condition), bodyNode = newNode.apply(body);
        parentEccoNode.addChild(whileNode);
        whileNode.addChildren(conditionNode, bodyNode);

        recursiveReadAst.accept(node.getExpression(), conditionNode);
        recursiveReadAst.accept(node.getBody(), bodyNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(EmptyStatement node) {
        JavaTreeArtifactData emptyStatement = new JavaTreeArtifactData();
        emptyStatement.setDataAsString(";");
        final Node.Op emptyStatementNode = newNode.apply(emptyStatement);

        parentEccoNode.addChild(emptyStatementNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        JavaTreeArtifactData enhancedForData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData(), rightSide = new JavaTreeArtifactData();
        enhancedForData.setType(LOOP_ENHANCED_FOR);
        rightSide.setType(BEFORE);
        body.setType(AFTER);
        final SingleVariableDeclaration parameter = node.getParameter();

        enhancedForData.setDataAsString(parameter.toString());
        Node.Op enhancedForNode = newNode.apply(enhancedForData), bodyNode = newNode.apply(body), rightSideNode = newNode.apply(rightSide);
        parentEccoNode.addChild(enhancedForNode);
        enhancedForNode.addChildren(bodyNode, rightSideNode);
        referenceCheckingConsumer.accept(enhancedForNode.getArtifact(), parameter);
        referenceCheckingConsumer.accept(enhancedForNode.getArtifact(), parameter.getType());

        recursiveReadAst.accept(node.getExpression(), rightSideNode);
        recursiveReadAst.accept(node.getBody(), bodyNode);

        return super.visit(node);
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        final Node.Op eccoNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(eccoNode);
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
        final JavaTreeArtifactData data = artifactFromSimpleNode(methodReference);
        // methodReference
        final Node.Op eccoNode = newNode.apply(data);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), methodReference);
        parentEccoNode.addChild(eccoNode);
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        JavaTreeArtifactData expressionStatementData = new JavaTreeArtifactData();
        expressionStatementData.setType(STATEMENT_EXPRESSION);
        final Node.Op expressionStatementNode = newNode.apply(expressionStatementData);
        parentEccoNode.addChild(expressionStatementNode);

        recursiveReadAst.accept(node.getExpression(), expressionStatementNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldAccess node) {
        final Node.Op eccoNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(eccoNode);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), node);
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        JavaTreeArtifactData fieldDeclarationData = new JavaTreeArtifactData();
        fieldDeclarationData.setType(FIELD_DECLARATION);
        final Node.Op fieldCreationNode = newNode.apply(fieldDeclarationData);
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
        JavaTreeArtifactData forData = new JavaTreeArtifactData(), initializer = new JavaTreeArtifactData(), condition = new JavaTreeArtifactData(), updaters = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        forData.setType(LOOP_FOR);

        initializer.setType(FOR_INITALIZER);
        updaters.setType(FOR_UPDATERS);
        body.setType(AFTER);
        condition.setType(CONDITION);

        final Node.Op forNode = newNode.apply(forData);
        final Node.Op initializerNode = newNode.apply(initializer), updaterNode = newNode.apply(updaters), bodyNode = newNode.apply(body), conditionNode = newNode.apply(condition);
        parentEccoNode.addChild(forNode);
        forNode.addChildren(initializerNode, conditionNode, updaterNode, bodyNode);

        ((List<Expression>) node.initializers()).forEach(i -> recursiveReadAst.accept(i, initializerNode));
        // node
        Optional.ofNullable(node.getExpression()).ifPresent(expression -> recursiveReadAst.accept(expression, conditionNode));
        recursiveReadAst.accept(node.getBody(), bodyNode);
        ((List<Expression>) node.updaters()).forEach(i -> recursiveReadAst.accept(i, updaterNode));

        return super.visit(node);
    }

    @Override
    public boolean visit(IfStatement node) {
        Node.Op iffsNode;
        {
            JavaTreeArtifactData parentArtifactData = (JavaTreeArtifactData) parentEccoNode.getArtifact().getData();
            if (parentArtifactData.getType() == STATEMENT_IFFS) {
                iffsNode = parentEccoNode;
            } else {
                JavaTreeArtifactData iffs = new JavaTreeArtifactData();
                iffs.setOrdered(true);
                iffs.setType(STATEMENT_IFFS);
                iffsNode = newNode.apply(iffs);
                parentEccoNode.addChild(iffsNode);
            }
        }

        JavaTreeArtifactData ifData = new JavaTreeArtifactData();
        ifData.setType(STATEMENT_IF);
        ifData.setDataAsString("if(" + node.getExpression() + ")");

        final Node.Op ifNode = newNode.apply(ifData);
        iffsNode.addChild(ifNode);

        recursiveReadAst.accept(node.getThenStatement(), ifNode);

        final Statement elseStatement = node.getElseStatement();
        if (elseStatement != null) {
            JavaTreeArtifactData.NodeType elseStatementType = elseStatement instanceof IfStatement ? STATEMENT_IF : STATEMENT_ELSE;

            if (elseStatementType == STATEMENT_IF) {
                recursiveReadAst.accept(elseStatement, iffsNode);
            } else {
                JavaTreeArtifactData elseData = new JavaTreeArtifactData();
                elseData.setDataAsString("else");
                elseData.setType(STATEMENT_ELSE);
                final Node.Op elseNode = newNode.apply(elseData);
                parentEccoNode.addChild(elseNode);

                recursiveReadAst.accept(elseStatement, elseNode);
            }

        }
        return super.visit(node);
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        final JavaTreeArtifactData importData = artifactFromSimpleNode(node);
        final Node.Op importNode = newNode.apply(importData);
        parentEccoNode.addChild(importNode);
        referenceCheckingConsumer.accept(importNode.getArtifact(), node);
        // End of recursion, no child nodes need to be visited
        return super.visit(node);
    }

    //TODO maybe more fine granular handling
    @Override
    public boolean visit(InfixExpression node) {
        final JavaTreeArtifactData javaTreeArtifactData = artifactFromSimpleNode(node);
        final Node.Op expressionNode = newNode.apply(javaTreeArtifactData);
        parentEccoNode.addChild(expressionNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(Initializer node) {
        JavaTreeArtifactData data = new JavaTreeArtifactData();
        data.setType(BLOCK);
        data.setOrdered(true);
        final Node.Op initializerNode = newNode.apply(data);
        parentEccoNode.addChild(initializerNode);

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
        final Node.Op labeledStatementNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(labeledStatementNode);
        TODO(node); // needs testing
        return super.visit(node);
    }

    @Override
    public boolean visit(LambdaExpression node) {
        JavaTreeArtifactData lambdaData = new JavaTreeArtifactData(), parametersData = new JavaTreeArtifactData();
        lambdaData.setType(LAMBDA);
        parametersData.setType(LAMBDA_PARAMETERS);

        final Node.Op lambdaNode = newNode.apply(lambdaData), parameterNode = newNode.apply(parametersData);
        parentEccoNode.addChild(lambdaNode);
        lambdaNode.addChild(parameterNode);

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
        parentEccoNode.addChild(methodInvocationNode);

        Expression e = node.getExpression();
        if (e != null) {
            JavaTreeArtifactData expressionBefore = new JavaTreeArtifactData();
            expressionBefore.setType(BEFORE);
            final Node.Op expressionNode = newNode.apply(expressionBefore);
            methodInvocationNode.addChild(expressionNode);
            recursiveReadAst.accept(e, expressionNode);
        }

        methodInvocationData.setDataAsString(node.getName().toString());
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
        final Node.Op simpleNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(simpleNode); // End of recursion
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
        final JavaTreeArtifactData nullLiteralData = artifactFromSimpleNode(node);
        final Node.Op nullNode = newNode.apply(nullLiteralData);
        parentEccoNode.addChild(nullNode); // No recursion needed
        return super.visit(node);
    }

    @Override
    public boolean visit(NumberLiteral node) {
        final JavaTreeArtifactData javaTreeArtifactData = artifactFromSimpleNode(node);
        final Node.Op literalNode = newNode.apply(javaTreeArtifactData);
        parentEccoNode.addChild(literalNode);
        return super.visit(node); // End of recursion
    }

    @Override
    public boolean visit(OpensDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        final JavaTreeArtifactData packageData = artifactFromSimpleNode(node);
        final Node.Op packageDataNode = newNode.apply(packageData);
        parentEccoNode.addChild(packageDataNode);
        // End of recursion
        return super.visit(node);
    }

    @Override
    public boolean visit(ParameterizedType node) {
        final JavaTreeArtifactData typeData = artifactFromSimpleNode(node);
        final Node.Op typeNode = newNode.apply(typeData);
        parentEccoNode.addChild(typeNode);
        referenceCheckingConsumer.accept(typeNode.getArtifact(), node);
        // No recursion
        return super.visit(node);
    }

    @Override
    public boolean visit(ParenthesizedExpression node) {
        JavaTreeArtifactData expressionData = new JavaTreeArtifactData();
        expressionData.setType(EXPRESSION_PARENTHESIS);
        final Node.Op expressionNode = newNode.apply(expressionData);
        parentEccoNode.addChild(expressionNode);
        recursiveReadAst.accept(node.getExpression(), expressionNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(PostfixExpression node) {
        JavaTreeArtifactData postfixData = artifactFromSimpleNode(node);
        final Node.Op postfixNode = newNode.apply(postfixData);
        parentEccoNode.addChild(postfixNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(PrefixExpression node) {
        final JavaTreeArtifactData prefixExpressionData = new JavaTreeArtifactData();
        prefixExpressionData.setType(EXPRESSION_PREFIX);
        prefixExpressionData.setDataAsString(node.toString());
        final Node.Op prefixExpressionNode = newNode.apply(prefixExpressionData);
        parentEccoNode.addChild(prefixExpressionNode);

        JavaTreeArtifactData operandData = new JavaTreeArtifactData();
        operandData.setType(AFTER);

        final Node.Op operandNode = newNode.apply(operandData);
        prefixExpressionNode.addChild(operandNode);

        recursiveReadAst.accept(node.getOperand(), operandNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ProvidesDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(PrimitiveType node) {
        final Node.Op node1 = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(node1);
        TODO(node); //Needs testing
        return super.visit(node);
    }

    @Override
    public boolean visit(QualifiedName node) {
        final JavaTreeArtifactData javaTreeArtifactData = artifactFromSimpleNode(node);
        final Node.Op qualifiedNameNode = newNode.apply(javaTreeArtifactData);
        parentEccoNode.addChild(qualifiedNameNode);
        // No recursion needed
        return super.visit(node);
    }

    @Override
    public boolean visit(QualifiedType node) {
        final Node.Op node1 = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(node1);
        TODO(node); //Needs testing
        return super.visit(node);
    }

    @Override
    public boolean visit(RequiresDirective node) {
        throw java9NotSupported.get();
    }

    @Override
    public boolean visit(ReturnStatement node) {
        final JavaTreeArtifactData returnData = new JavaTreeArtifactData();
        returnData.setType(STATEMENT_RETURN);
        final Node.Op returnNode = newNode.apply(returnData);
        parentEccoNode.addChild(returnNode);
        recursiveReadAst.accept(node.getExpression(), returnNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(SimpleName node) {
        final Node.Op simpleNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(simpleNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(SimpleType node) {
        final Node.Op eccoNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(eccoNode);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SingleMemberAnnotation node) {
        throw new IllegalStateException("Not needed in current implementation");
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        final JavaTreeArtifactData singleVariableDeclaration = artifactFromSimpleNode(node);
        final Node.Op variableDeclarationNode = newNode.apply(singleVariableDeclaration);
        referenceCheckingConsumer.accept(variableDeclarationNode.getArtifact(), node);
        return super.visit(node);
    }

    @Override
    public boolean visit(StringLiteral node) {
        final JavaTreeArtifactData stringLiteralArtifact = artifactFromSimpleNode(node);
        final Node.Op stringLiteralNode = newNode.apply(stringLiteralArtifact);
        parentEccoNode.addChild(stringLiteralNode);
        // No recursion needed
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        final JavaTreeArtifactData superConstructorData = artifactFromSimpleNode(node);
        final Node.Op superConstructorNode = newNode.apply(superConstructorData);
        parentEccoNode.addChild(superConstructorNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperFieldAccess node) {
        final JavaTreeArtifactData superFieldAccessData = artifactFromSimpleNode(node);
        final Node.Op superFieldAccessNode = newNode.apply(superFieldAccessData);
        parentEccoNode.addChild(superFieldAccessNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        JavaTreeArtifactData methodInvocationData = new JavaTreeArtifactData();
        methodInvocationData.setDataAsString("super." + node.getName());
        methodInvocationData.setOrdered(true);
        methodInvocationData.setType(METHOD_INVOCATION);
        final Node.Op methodInvocationNode = newNode.apply(methodInvocationData);
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
        final JavaTreeArtifactData thisData = artifactFromSimpleNode(node);
        final Node.Op thisNode = newNode.apply(thisData);
        parentEccoNode.addChild(thisNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ThrowStatement node) {
        JavaTreeArtifactData throwData = new JavaTreeArtifactData();
        throwData.setType(THROW_STATEMENT);
        final Node.Op throwNode = newNode.apply(throwData);
        parentEccoNode.addChild(throwNode);
        recursiveReadAst.accept(node.getExpression(), throwNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(TryStatement node) {
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
        TODO(node); // need example of this
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeLiteral node) {
        final JavaTreeArtifactData simpleNode = artifactFromSimpleNode(node);
        final Node.Op eccoNode = newNode.apply(simpleNode);
        parentEccoNode.addChild(eccoNode);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), node);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), node.getType());
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
        final Node.Op variableDeclarationNode = newNode.apply(variableDeclaration);
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
        variableDeclaration.setType(STATEMENT_VARIABLE_DECLARATION);
        variableDeclaration.setOrdered(true);
        final Node.Op variableDeclarationNode = newNode.apply(variableDeclaration);
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
        parentEccoNode.addChild(variableDeclarationFragmentNode);

        recursiveReadAst.accept(fragment, variableDeclarationFragmentNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(WhileStatement node) {
        JavaTreeArtifactData whileData = new JavaTreeArtifactData(), condition = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        whileData.setType(LOOP_WHILE);
        whileData.setDataAsString("while");
        condition.setType(CONDITION);
        body.setType(AFTER);

        final Node.Op whileNode = newNode.apply(whileData), conditionNode = newNode.apply(condition), bodyNode = newNode.apply(body);
        parentEccoNode.addChild(whileNode);
        whileNode.addChildren(conditionNode, bodyNode);

        recursiveReadAst.accept(node.getExpression(), conditionNode);
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
}
