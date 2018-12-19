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
        parentEccoNode.addChild(methodNode);
        List<SingleVariableDeclaration> singleVariableDeclarationList = node.parameters();

        handleParameters(singleVariableDeclarationList, methodNode);

        handleModifiers(node.modifiers(), methodNode);
        final Type returnType = node.getReturnType2();

        handleGenericParameters(node.typeParameters(), methodNode);

        String beforeChildren = (returnType == null ? "" : returnType.toString() + " ") + methodName.getIdentifier();

        JavaTreeArtifactData before = new JavaTreeArtifactData();
        before.setType(BEFORE);
        Node.Op beforeNode = newNode.apply(before);
        methodNode.addChild(beforeNode);

        JavaTreeArtifactData data = new JavaTreeArtifactData();
        data.setType(SIMPLE_JUST_A_STRING);
        data.setDataAsString(beforeChildren);

        beforeNode.addChild(newNode.apply(data));

        StringBuilder id = new StringBuilder();
        id.append((returnType == null ? "" : returnType.toString() + " "))
                .append(methodName.getIdentifier()).append('(');
        boolean tmp = false;
        for (SingleVariableDeclaration d : singleVariableDeclarationList) {
            if (tmp) {
                id.append(',');
            }
            id.append(d.getType());
            if (d.isVarargs())
                id.append("...");
            tmp = true;

        }
        id.append(')');

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
        methodDeclarationData.setDataAsString(id.toString());

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
        if (needsExpansion(node))
            return super.visit(node);
        //Does not need an ID
        JavaTreeArtifactData typeDeclarationData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        typeDeclarationData.setType(ANNOTATION_TYPE_DECLARATION);
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
        if (needsExpansion(node))
            return super.visit(node);
        //Does not need an ID
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
        //Does not need an ID
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
        if (needsExpansion(node))
            return super.visit(node);
        //Does not need an ID
        final JavaTreeArtifactData arrayAccessData = artifactFromSimpleNode(node);
        final Node.Op arrayAccessNode = newNode.apply(arrayAccessData);
        referenceCheckingConsumer.accept(arrayAccessNode.getArtifact(), node);
        parentEccoNode.addChildren(arrayAccessNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayCreation node) {
        if (needsExpansion(node))
            return super.visit(node);
        //Does not need an ID
        final JavaTreeArtifactData arrayCreationData = artifactFromSimpleNode(node);
        final Node.Op arrayCreationNode = newNode.apply(arrayCreationData);
        referenceCheckingConsumer.accept(arrayCreationNode.getArtifact(), node);
        parentEccoNode.addChild(arrayCreationNode);
        //No recursion
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayInitializer node) {
        if (needsExpansion(node))
            return super.visit(node);
        //Does not need an ID
        final JavaTreeArtifactData data = artifactFromSimpleNode(node);
        final Node.Op eccoNode = newNode.apply(data);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), node);
        parentEccoNode.addChild(eccoNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayType node) {
        if (needsExpansion(node))
            return super.visit(node);
        //Does not need an ID
        final Node.Op typeNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(typeNode);
        referenceCheckingConsumer.accept(typeNode.getArtifact(), node);
        return super.visit(node);
    }

    @Override
    public boolean visit(AssertStatement node) {
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData assertData = new JavaTreeArtifactData(), assertMessageData = new JavaTreeArtifactData();
        assertData.setType(STATEMENT_ASSERT);


        assertMessageData.setType(STATEMENT_ASSERT_MESSAGE);

        final Node.Op assertNode = newNode.apply(assertData), assertMessageNode = newNode.apply(assertMessageData);
        parentEccoNode.addChild(assertNode);
        assertNode.addChild(assertMessageNode);
        referenceCheckingConsumer.accept(assertNode.getArtifact(), node);

        recursiveReadAst.accept(node.getMessage(), assertMessageNode);
        //ID of root node should be the assertion condition
        assertData.setDataAsString(node.getExpression().toString().trim());
        return super.visit(node);
    }

    @Override
    public boolean visit(Assignment node) {
        if (needsExpansion(node))
            return super.visit(node);
        final JavaTreeArtifactData assignmentData = new JavaTreeArtifactData();
        //ID of assignment should be the node on the left
        assignmentData.setDataAsString(node.getLeftHandSide() + node.getOperator().toString());
        assignmentData.setType(ASSIGNMENT);
        final JavaTreeArtifactData before = new JavaTreeArtifactData(), after = new JavaTreeArtifactData();
        before.setType(BEFORE);
        after.setType(AFTER);

        Node.Op assignmentNode = newNode.apply(assignmentData), beforeNode = newNode.apply(before), afterNode = newNode.apply(after);
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
        parentEccoNode.addChild(blockNode);
        referenceCheckingConsumer.accept(blockNode.getArtifact(), node);

        ((List<Statement>) node.statements()).forEach(child -> recursiveReadAst.accept(child, blockNode));
        return super.visit(node);
    }

    @Override
    public boolean visit(BlockComment node) {
        // No handling of comments
        throw new TODO("Not implemented");
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
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData newData = new JavaTreeArtifactData();
        newData.setType(CLASS_INSTANCE_CREATION);
        newData.setDataAsString(node.getType().toString());
        final Node.Op newClassNode = newNode.apply(newData);
        parentEccoNode.addChild(newClassNode);
        referenceCheckingConsumer.accept(newClassNode.getArtifact(), node);
        Type type = node.getType();
        referenceCheckingConsumer.accept(newClassNode.getArtifact(), type);
        recursiveReadAst.accept(node.getAnonymousClassDeclaration(), newClassNode);

        handleParameters(((List<Expression>) node.arguments()), newClassNode);

        return super.visit(node);
    }

    private void handleParameters(List<? extends ASTNode> expressionList, Node.Op parentEccoNode) {
        if (expressionList.isEmpty()) return;
        JavaTreeArtifactData parameters = new JavaTreeArtifactData();
        parameters.setType(PARAMETERS);
        Node.Op parameterNode = newNode.apply(parameters);
        parentEccoNode.addChild(parameterNode);

        for (int i = 0; i < expressionList.size(); i++) {
            ASTNode next = expressionList.get(i);
            JavaTreeArtifactData cPar = new JavaTreeArtifactData();
            cPar.setType(PARAMETER_POSITION);
            cPar.setDataAsString(Integer.toString(i));
            Node.Op cParNode = newNode.apply(cPar);
            parameterNode.addChild(cParNode);
            recursiveReadAst.accept(next, cParNode);
        }
    }


    @Override
    public boolean visit(final CompilationUnit node) {
        final List<AbstractTypeDeclaration> types = (List<AbstractTypeDeclaration>) node.types();
        //Check if errors are detected
        IProblem[] problems = node.getProblems();
        List<IProblem> notCompiling = Arrays.stream(problems).filter(p -> p.getMessage().startsWith("Syntax error")).collect(Collectors.toList());
        if (!notCompiling.isEmpty()) {
            System.out.println("Problems:");
            notCompiling.stream().map(problem -> "Line " + problem.getSourceLineNumber() + " => " + problem.getMessage()).forEach(System.out::println);

            throw new Error("Compilation problem detected at" + types.toString(), new Error(Arrays.toString(problems)));
        }
        //No new node needs to be generated
        final PackageDeclaration packageDeclaration = node.getPackage();
        if (packageDeclaration != null)
            visit(packageDeclaration);
        List<ImportDeclaration> imports = (List<ImportDeclaration>) node.imports();
        if (imports != null)
            imports.forEach(this::visit);

        types.forEach(abstractTypeDeclaration -> recursiveReadAst.accept(abstractTypeDeclaration, parentEccoNode));
        referenceCheckingConsumer.accept(parentEccoNode.getArtifact(), node);

        return super.visit(node);
    }

    @Override
    public boolean visit(ConditionalExpression node) {
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
        final JavaTreeArtifactData constructorData = artifactFromSimpleNode(node);
        final Node.Op constructorNode = newNode.apply(constructorData);
        parentEccoNode.addChild(constructorNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
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
        JavaTreeArtifactData whileData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        whileData.setType(LOOP_DO_WHILE);
        body.setType(AFTER);

        final Node.Op whileNode = newNode.apply(whileData), bodyNode = newNode.apply(body);
        parentEccoNode.addChild(whileNode);
        whileNode.addChild(bodyNode);

        Statement doBody = node.getBody();
        Node.Op bodyParent = ensureBlockNode(doBody, bodyNode);

        // Condition of the do-while is the ID
        whileData.setDataAsString(node.getExpression().toString());
        recursiveReadAst.accept(doBody, bodyParent);
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
        JavaTreeArtifactData enhancedForData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        enhancedForData.setType(LOOP_ENHANCED_FOR);
        body.setType(AFTER);
        final SingleVariableDeclaration parameter = node.getParameter();

        //The ID of the enhanced for should be the element, which it iterates over
        enhancedForData.setDataAsString(node.getParameter() + ":" + node.getExpression());

        Node.Op enhancedForNode = newNode.apply(enhancedForData), bodyNode = newNode.apply(body);
        parentEccoNode.addChild(enhancedForNode);
        enhancedForNode.addChild(bodyNode);
        referenceCheckingConsumer.accept(enhancedForNode.getArtifact(), parameter);
        referenceCheckingConsumer.accept(enhancedForNode.getArtifact(), parameter.getType());

        Statement forBody = node.getBody();
        Node.Op bodyNodeParent = ensureBlockNode(forBody, bodyNode);

        recursiveReadAst.accept(forBody, bodyNodeParent);

        return super.visit(node);
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData expressionStatementData = new JavaTreeArtifactData();
        expressionStatementData.setType(STATEMENT_EXPRESSION);
        final Node.Op expressionStatementNode = newNode.apply(expressionStatementData);
        parentEccoNode.addChild(expressionStatementNode);
        recursiveReadAst.accept(node.getExpression(), expressionStatementNode);
        //Create ID for parent node
        final JavaTreeArtifactData childData = (JavaTreeArtifactData) expressionStatementNode.getChildren().get(0).getArtifact().getData();

        String id = childData.getType().ordinal() + "-" + childData.getDataAsString();
        expressionStatementData.setDataAsString(id);
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldAccess node) {
        if (needsExpansion(node))
            return super.visit(node);
        final Node.Op eccoNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(eccoNode);
        referenceCheckingConsumer.accept(eccoNode.getArtifact(), node);
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.fragments();
        for (VariableDeclarationFragment fragment : fragments) {
            JavaTreeArtifactData field = new JavaTreeArtifactData();
            field.setType(FIELD_DECLARATION);
            field.setDataAsString(fragment.getName().toString());
            JavaTreeArtifactData type = new JavaTreeArtifactData();
            type.setType(FIELD_TYPE);
            type.setDataAsString(node.getType().toString());
            JavaTreeArtifactData modifiers = new JavaTreeArtifactData();
            modifiers.setType(MODIFIER);
            Expression e = fragment.getInitializer();


            Node.Op fieldNode = newNode.apply(field), typeNode = newNode.apply(type), modifyerNode = newNode.apply(modifiers);
            parentEccoNode.addChild(fieldNode);
            fieldNode.addChildren(typeNode, modifyerNode);
            if (e != null) {
                JavaTreeArtifactData init = new JavaTreeArtifactData();
                init.setType(FIELD_INIT);
                Node.Op op = newNode.apply(init);
                fieldNode.addChild(op);
                recursiveReadAst.accept(e, op);
            }

            List<IExtendedModifier> modifierList = (List<IExtendedModifier>) node.modifiers();
            for (IExtendedModifier mod :
                    modifierList) {
                JavaTreeArtifactData jtad = new JavaTreeArtifactData();
                jtad.setType(SIMPLE_JUST_A_STRING);
                jtad.setDataAsString(mod.toString().trim());
                modifyerNode.addChild(newNode.apply(jtad));
            }
        }

        return super.visit(node);
    }

    @Override
    public boolean visit(ForStatement node) {
        JavaTreeArtifactData forData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        forData.setType(LOOP_FOR);
        body.setType(AFTER);

        final Node.Op forNode = newNode.apply(forData);
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

        Statement forBody = node.getBody();
        Node.Op bodyNodeParent = ensureBlockNode(forBody, bodyNode);

        recursiveReadAst.accept(forBody, bodyNodeParent);

        return super.visit(node);
    }

    @Override
    public boolean visit(IfStatement node) {
        JavaTreeArtifactData ifData = new JavaTreeArtifactData();

        ifData.setDataAsString(node.getExpression().toString());
        ifData.setType(JavaTreeArtifactData.NodeType.STATEMENT_IF);
        JavaTreeArtifactData thenData = new JavaTreeArtifactData();
        thenData.setType(AFTER);

        Node.Op ifNode = newNode.apply(ifData), thenNode = newNode.apply(thenData);

        parentEccoNode.addChild(ifNode);
        ifNode.addChild(thenNode);

        Statement thenStatement = node.getThenStatement();

        Node.Op thenParent = ensureBlockNode(thenStatement, thenNode);

        recursiveReadAst.accept(thenStatement, thenParent);

        Statement optionalElseBranch = node.getElseStatement();
        if (optionalElseBranch != null) {
            JavaTreeArtifactData elseData = new JavaTreeArtifactData();
            elseData.setType(JavaTreeArtifactData.NodeType.STATEMENT_ELSE);
            Node.Op elseNode = newNode.apply(elseData);
            parentEccoNode.addChild(elseNode);
            if (optionalElseBranch instanceof IfStatement) {
                IfStatement elseIfStaterment = ((IfStatement) optionalElseBranch);
                visit(elseIfStaterment);
            } else {
                JavaTreeArtifactData data = new JavaTreeArtifactData();
                data.setType(AFTER);
                Node.Op dataNode = newNode.apply(data);
                elseNode.addChild(dataNode);
                recursiveReadAst.accept(optionalElseBranch, dataNode);
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
        if (needsExpansion(node))
            return super.visit(node);
        final JavaTreeArtifactData javaTreeArtifactData = artifactFromSimpleNode(node);
        final Node.Op expressionNode = newNode.apply(javaTreeArtifactData);
        parentEccoNode.addChild(expressionNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(Initializer node) {
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
        final Node.Op labeledStatementNode = newNode.apply(artifactFromSimpleNode(node));
        parentEccoNode.addChild(labeledStatementNode);
        TODO(node); // needs testing
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
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData methodInvocationData = new JavaTreeArtifactData();
        methodInvocationData.setOrdered(true);
        methodInvocationData.setType(METHOD_INVOCATION);

        final Node.Op methodInvocationNode = newNode.apply(methodInvocationData);
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

        final List<Expression> arguments = ((List<Expression>) node.arguments());
        handleParameters(arguments, methodInvocationNode);
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
        final JavaTreeArtifactData packageData = new JavaTreeArtifactData();
        packageData.setType(SIMPLE_JUST_A_STRING);
        packageData.setDataAsString("package " + node.getName().toString() + ";");
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
        if (needsExpansion(node))
            return super.visit(node);
        //Does not really need an ID
        JavaTreeArtifactData expressionData = new JavaTreeArtifactData();
        expressionData.setType(EXPRESSION_PARENTHESIS);
        final Node.Op expressionNode = newNode.apply(expressionData);
        parentEccoNode.addChild(expressionNode);
        recursiveReadAst.accept(node.getExpression(), expressionNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(PostfixExpression node) {
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData postfixData = artifactFromSimpleNode(node);
        final Node.Op postfixNode = newNode.apply(postfixData);
        parentEccoNode.addChild(postfixNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(PrefixExpression node) {
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData prefixData = artifactFromSimpleNode(node);
        final Node.Op prefixNode = newNode.apply(prefixData);
        parentEccoNode.addChild(prefixNode);
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
        if (needsExpansion(node))
            return super.visit(node);
        //Does not need an ID
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
        parentEccoNode.addChild(variableDeclarationNode);
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
        if (needsExpansion(node))
            return super.visit(node);
        final JavaTreeArtifactData superConstructorData = artifactFromSimpleNode(node);
        final Node.Op superConstructorNode = newNode.apply(superConstructorData);
        parentEccoNode.addChild(superConstructorNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperFieldAccess node) {
        if (needsExpansion(node))
            return super.visit(node);
        final JavaTreeArtifactData superFieldAccessData = artifactFromSimpleNode(node);
        final Node.Op superFieldAccessNode = newNode.apply(superFieldAccessData);
        parentEccoNode.addChild(superFieldAccessNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData methodInvocationData = new JavaTreeArtifactData();
        methodInvocationData.setDataAsString("super." + node.getName()); //Might need something more dynamic than super
        methodInvocationData.setOrdered(true);
        methodInvocationData.setType(METHOD_INVOCATION);
        final Node.Op methodInvocationNode = newNode.apply(methodInvocationData);
        parentEccoNode.addChild(methodInvocationNode);

        final List<Expression> arguments = ((List<Expression>) node.arguments());
        handleParameters(arguments, methodInvocationNode);

        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodReference node) {
        if (needsExpansion(node))
            return super.visit(node);
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
        if (needsExpansion(node))
            return super.visit(node);
        final JavaTreeArtifactData thisData = artifactFromSimpleNode(node);
        final Node.Op thisNode = newNode.apply(thisData);
        parentEccoNode.addChild(thisNode);
        return super.visit(node);
    }

    @Override
    public boolean visit(ThrowStatement node) {
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData throwData = new JavaTreeArtifactData();
        throwData.setType(THROW_STATEMENT);
        final Node.Op throwNode = newNode.apply(throwData);
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
        if (needsExpansion(node))
            return super.visit(node);
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
        String type = node.getType().toString();
        List<IExtendedModifier> modifiers = (List<IExtendedModifier>) node.modifiers();
        for (VariableDeclarationFragment fragment : (List<VariableDeclarationFragment>) node.fragments()) {
            JavaTreeArtifactData modifierData = new JavaTreeArtifactData();
            modifierData.setType(MODIFIER);
            Node.Op modifierNode = newNode.apply(modifierData);
            for (IExtendedModifier mod : modifiers) {
                JavaTreeArtifactData data = new JavaTreeArtifactData();
                data.setType(SIMPLE_JUST_A_STRING);
                data.setDataAsString(mod.toString().trim());
                modifierNode.addChild(newNode.apply(data));
            }

            Expression initializer = fragment.getInitializer();
            String name = fragment.getName().toString();

            JavaTreeArtifactData data = new JavaTreeArtifactData();
            data.setType(FIELD_DECLARATION);
            data.setDataAsString(name);

            Node.Op declarationNode = newNode.apply(data);
            parentEccoNode.addChild(declarationNode);

            JavaTreeArtifactData typeData = new JavaTreeArtifactData();
            typeData.setType(FIELD_TYPE);
            typeData.setDataAsString(type);

            Node.Op typeNode = newNode.apply(typeData);
            declarationNode.addChildren(modifierNode, typeNode);

            if (initializer != null) {
                JavaTreeArtifactData init = new JavaTreeArtifactData();
                init.setType(FIELD_INIT);
                Node.Op initNode = newNode.apply(init);
                declarationNode.addChild(initNode);

                recursiveReadAst.accept(initializer, initNode);
            }


        }

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
        if (needsExpansion(node))
            return super.visit(node);
        JavaTreeArtifactData whileData = new JavaTreeArtifactData(), body = new JavaTreeArtifactData();
        whileData.setType(LOOP_WHILE);
        whileData.setDataAsString(node.getExpression().toString());
        body.setType(AFTER);

        final Node.Op whileNode = newNode.apply(whileData), bodyNode = newNode.apply(body);
        parentEccoNode.addChild(whileNode);
        whileNode.addChild(bodyNode);

        Statement whileBody = node.getBody();
        Node.Op whileBodyParent = ensureBlockNode(whileBody, bodyNode);
        recursiveReadAst.accept(whileBody, whileBodyParent);
        return super.visit(node);
    }

    @Override
    public boolean visit(WildcardType node) {
        throw new IllegalStateException("Not used in current implementation!");
    }

    private JavaTreeArtifactData artifactFromSimpleNode(ASTNode astNode) {
        JavaTreeArtifactData javaTreeArtifactData = new JavaTreeArtifactData();
        javaTreeArtifactData.setType(SIMPLE_JUST_A_STRING);

        String data = astNode.toString().trim().replaceAll("\\s\\s+", " ");


        javaTreeArtifactData.setDataAsString(data);
        return javaTreeArtifactData;
    }

    private boolean needsExpansion(ASTNode e) {
        boolean done = !actualCheckForLambda(e);
        if (done) {
            //Treat as one
            parentEccoNode.addChild(newNode.apply(artifactFromSimpleNode(e)));
        }
        return done;
    }

    private boolean actualCheckForLambda(ASTNode n) {
        NeedsMoreDetailASTVisitor astVisitor = new NeedsMoreDetailASTVisitor();
        n.accept(astVisitor);
        return astVisitor.lambdaFound();
    }

    private Node.Op ensureBlockNode(Statement statement, Node.Op curParent) {
        if (statement instanceof Block)
            return curParent;
        else {
            JavaTreeArtifactData fakeBlockData = new JavaTreeArtifactData();
            fakeBlockData.setType(BLOCK);
            fakeBlockData.setOrdered(true);
            Node.Op fakeBlockNode = newNode.apply(fakeBlockData);
            curParent.addChild(fakeBlockNode);
            return fakeBlockNode;
        }
    }
}
