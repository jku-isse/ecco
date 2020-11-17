package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.JavaTreeArtifactData.NodeType;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.jku.isse.ecco.adapter.java.JavaTreeArtifactData.NodeType.*;

public class JavaWriter implements ArtifactWriter<Set<Node>, Path> {
    @Override
    public String getPluginId() {
        return JavaPlugin.getPluginIdStatic();
    }


    @Override
    public Path[] write(Set<Node> input) {
        return write(Paths.get("."), input);
    }

    Collection<WriteListener> writeListeners = new ArrayList<>();

    @Override
    public void addListener(WriteListener listener) {
        writeListeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        writeListeners.remove(listener);
    }

    //Out: Array of changed files
    @Override
    public Path[] write(Path base, Set<Node> input) {
        Path[] toreturn = input.parallelStream().map(node -> processNode(node, base)).filter(Objects::nonNull).toArray(Path[]::new);
        if (toreturn.length != input.size())
            throw new IllegalStateException("Not all files could be written!");
        return toreturn;
    }


    /**
     * @param baseNode The base node which should be processed
     * @param basePath The base path (need to parse package hierarchy
     * @return The path were the file got placed
     */
    private Path processNode(Node baseNode, Path basePath) {
        if (!(baseNode.getArtifact().getData() instanceof PluginArtifactData)) return null;
        PluginArtifactData rootData = (PluginArtifactData) baseNode.getArtifact().getData();
        final List<? extends Node> children = baseNode.getChildren();
        if (children.size() != 1)
            return null;

        //These  are import / package and class nodes
        final List<? extends Node> javaFileNodes = children.get(0).getChildren();

        Path returnPath = basePath.resolve(rootData.getPath());
        StringBuilder stringBuilder = new StringBuilder();

        //Rebuild Java file
        javaFileNodes.forEach(childNode -> {
            if (childNode.getArtifact().getData() instanceof JavaTreeArtifactData) {
                final JavaTreeArtifactData artifactData = (JavaTreeArtifactData) childNode.getArtifact().getData();
                processJavaAst(stringBuilder, childNode, artifactData);
            }
        });

        try {
            formatCodeAndWriteFile(returnPath, stringBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return returnPath;
    }

    private void formatCodeAndWriteFile(Path path, String contents) throws Exception {
        // format code string
        CodeFormatter cf = new DefaultCodeFormatter();
        TextEdit te = cf.format(CodeFormatter.K_COMPILATION_UNIT, contents, 0, contents.length(), 0, null);
        IDocument dc = new Document(contents);
        path = path.toAbsolutePath();
        final Path parentFolder = path.getParent();
        Files.createDirectories(parentFolder);
        try (BufferedWriter fileWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            te.apply(dc);
            String formattedContent = dc.get();
            fileWriter.write(formattedContent);
        }
    }

    private void processJavaAst(StringBuilder stringBuilder, NodeArtifactEntry nodeArtifactEntry) {
        processJavaAst(stringBuilder, nodeArtifactEntry.getNode(), nodeArtifactEntry.getArtifact());
    }

    private void processJavaAst(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        final NodeType curNodeType = artifactData.getType();
        if (curNodeType == null) // e.g. "EmptyStatement"
            stringBuilder.append(artifactData.getDataAsString());
        else {
            switch (curNodeType) {
                case TYPE_DECLARATION:
                case ANNOTATION_TYPE_DECLARATION:
                case FIELD_DECLARATION:
                case METHOD_DECLARATION:
                case ENUM_DECLARATION:
                case ANNOTATIONMEMBER:
                case STATEMENT_VARIABLE_DECLARATION:
                case BLOCK:
                case DIMENSION:
                    String modifiers = findChildren(curNode, MODIFIER)
                            .map(e -> e.getNode().getChildren())
                            .flatMap(Collection::stream)
                            .map(NodeArtifactEntry::fromNode)
                            .filter(Objects::nonNull)
                            .map(e -> e.getArtifact().getDataAsString())
                            .collect(Collectors.joining(" ", " ", " "));
                    stringBuilder.append(modifiers);
                    break;
            }
            // Handle rest
            switch (curNodeType) {
                case EXPRESSION_PREFIX:
                case EXPRESION_POSTFIX:
                case SIMPLE_JUST_A_STRING:
                    stringBuilder.append(artifactData.getDataAsString());
                    break;
                case EXPRESSION_VARIABLE_DECLARATION:
                    handleVariableDeclarationExpression(stringBuilder, curNode, artifactData);
                    break;
                case ASSIGNMENT:
                    handleAssignment(stringBuilder, curNode, artifactData);
                    break;
                case LAMBDA:
                    handleLambda(stringBuilder, curNode, artifactData);
                    break;
                case PARAMETERS:
                    handleParameters(stringBuilder, curNode, artifactData);
                    break;
                case METHOD_INVOCATION:
                    handleMethodInvokation(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_EXPRESSION:
                    handleStatementExpression(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_VARIABLE_DECLARATION:
                    handleVariableDeclarationStatement(stringBuilder, curNode, artifactData);
                    break;
                case VARIABLE_DECLARATION_FRAGMENT:
                    handleVariableDeclarationFragment(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_FOR:
                    handleVariableForLoop(stringBuilder, curNode, artifactData);
                    break;
                case METHOD_DECLARATION:
                    handleMethodDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case ANONYMOUS_CLASS_DECLARATION:
                case BLOCK:
                    handleBlockDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case CLASS_INSTANCE_CREATION:
                    handleClassInstanceCreation(stringBuilder, curNode, artifactData);
                    break;
                case SYNCHRONIZED_STATEMENT:
                    handleSynchronizedStatement(stringBuilder, curNode, artifactData);
                    break;
                case TYPE_DECLARATION:
                    handleTypeDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case FIELD_INIT:
                case BEFORE:
                    processChildrenOnly(stringBuilder, curNode, artifactData);
                    break;
                case SWITCH_SWITCH:
                    handleSwitchCase(stringBuilder, curNode, artifactData);
                    break;
                case SWITCH_CASE:
                    handleSwitchCaseCase(stringBuilder, curNode, artifactData);
                    break;
                case THROW_STATEMENT:
                    handleThrowStatement(stringBuilder, curNode, artifactData);
                    break;
                case EXPRESSION_PARENTHESIS:
                    handleExpressionParenthesis(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_WHILE:
                    handleWhileLoop(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_DO_WHILE:
                    handleDoWhileLoop(stringBuilder, curNode, artifactData);
                    break;
                case ENUM_DECLARATION:
                    handleEnumDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case EXPRESSION_CAST:
                    handleCast(stringBuilder, curNode, artifactData);
                    break;
                case EXPRESSION_TRENARY:
                    handleTrenaryExpression(stringBuilder, curNode, artifactData);
                    break;
                case ANNOTATION_TYPE_DECLARATION:
                    handleAnnotationTypeDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case ANNOTATIONMEMBER:
                    handleAnnotationmember(stringBuilder, curNode, artifactData);
                    break;
                case ANNOTATIONMEMBER_DEFAULT:
                    handleAnnotationmemberDefault(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_RETURN:
                    handleReturnStatement(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_ENHANCED_FOR:
                    handleAdvancedForLoop(stringBuilder, curNode, artifactData);
                    break;
                case TRY_META:
                    handleTry(stringBuilder, curNode, artifactData);
                    break;
                case CATCH:
                    handleCatch(stringBuilder, curNode, artifactData);
                    break;
                case FIELD_DECLARATION:
                    handleFieldDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_ASSERT:
                    handleAssert(stringBuilder, curNode, artifactData);
                    break;
                case LAMBDA_PARAMETERS:
                    handleLambdaParameters(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_IF:
                    handleIfStatement(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_ELSE:
                    handleElseStatement(stringBuilder, curNode, artifactData);
                    break;
                default:
                    throw new IllegalStateException(curNodeType + " is not supported");
            }
        }
    }

    private void handleParameters(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        //Group elements by parameter position
        List<Map.Entry<Integer, List<? extends Node>>> orderedParameters = curNode.getChildren().stream().map(e -> {
            JavaTreeArtifactData data = ((JavaTreeArtifactData) e.getArtifact().getData());
            return new AbstractMap.SimpleImmutableEntry<Integer, List<? extends Node>>(Integer.parseInt(data.getDataAsString()), e.getChildren());
        }).sequential().sorted(Comparator.comparing(AbstractMap.SimpleImmutableEntry::getKey)).collect(Collectors.toList());

        stringBuilder.append('(');
        StringJoiner paramPositionJoiner = new StringJoiner(",");

        for (Map.Entry<?, List<? extends Node>> cur : orderedParameters) {
            StringJoiner sj = new StringJoiner(" ");
            StringBuilder tmp = new StringBuilder();

            for (Node n : cur.getValue()) {
                processJavaAst(tmp, n, mapToJavaArtifact(n));
                sj.add(tmp);
                tmp.setLength(0);
            }

            paramPositionJoiner.add(sj.toString());
        }

        stringBuilder.append(paramPositionJoiner);
        stringBuilder.append(')');
    }

    private void handleElseStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" else ");
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void processChildrenOnly(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        if (!curNode.getChildren().isEmpty())
            stringBuilder.append("=");
        for (Node c : curNode.getChildren()) {
            JavaTreeArtifactData data = mapToJavaArtifact(c);
            processJavaAst(stringBuilder, c, data);
        }
    }

    private void handleLambdaParameters(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        final List<? extends Node> children = curNode.getChildren();
        handleCommaSeperatedExpressions(children, stringBuilder);
    }

    private void handleAssert(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("assert ").append(artifactData.getDataAsString());

        final boolean hasMessage = findChildren(curNode, STATEMENT_ASSERT_MESSAGE).map(e -> e.getNode().getChildren()).mapToInt(List::size).sum() > 0;
        if (hasMessage) {
            stringBuilder.append(':');
            processJavaAstForChildsChildren(curNode, STATEMENT_ASSERT_MESSAGE, stringBuilder);
        }

        stringBuilder.append(';');
    }

    private void handleIfStatement(final StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" if(").append(artifactData.getDataAsString()).append(')');

        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleFieldDeclaration(final StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        // Find type and modifiers, then print name. If initializer is there, print it last
        String fieldName = artifactData.getDataAsString();
        String type = findChildren(curNode, FIELD_TYPE).map(NodeArtifactEntry::getArtifact).filter(Objects::nonNull).map(JavaTreeArtifactData::getDataAsString).collect(Collectors.joining(" "));
        stringBuilder.append(type).append(" ").append(fieldName);
        curNode.getChildren().stream().filter(e -> {
            JavaTreeArtifactData data = mapToJavaArtifact(e);
            assert data != null;
            NodeType nType = data.getType();
            return nType != MODIFIER && nType != FIELD_TYPE;
        }).forEach(e -> processJavaAst(stringBuilder, e, ((JavaTreeArtifactData) e.getArtifact().getData())));

        stringBuilder.append(";");

    }

    private void handleTry(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("try");
        final boolean tryRessources = findChildren(curNode, TRY_RESSOURCES).mapToInt(e -> e.getNode().getChildren().size()).sum() > 0;
        if (tryRessources) {
            stringBuilder.append('(');

            processJavaAstForChildsChildren(curNode, TRY_RESSOURCES, stringBuilder);

            stringBuilder.append(')');
        }

        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
        processJavaAstForChildsChildren(curNode, CATCH_META, stringBuilder);

        final boolean finallyStatement = findChildren(curNode, FINALLY).mapToInt(e -> e.getNode().getChildren().size()).sum() > 0;
        if (finallyStatement) {
            stringBuilder.append("finally");
            processJavaAstForChildsChildren(curNode, FINALLY, stringBuilder);
        }
    }

    private void handleCatch(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" catch(").append(artifactData.getDataAsString()).append(')');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleAdvancedForLoop(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("for(").append(artifactData.getDataAsString());

        stringBuilder.append(')');

        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleReturnStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("return ");
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
        stringBuilder.append(';');
    }

    private void handleAnnotationmemberDefault(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" default ");
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleAnnotationmember(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
        stringBuilder.append(';');
    }

    private void handleAnnotationTypeDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        findChildren(curNode, BLOCK).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleTrenaryExpression(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        processJavaAstForChildsChildren(curNode, CONDITION, stringBuilder);
        stringBuilder.append('?');
        processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);
        stringBuilder.append(':');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleCast(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));

    }

    private void handleEnumDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        processJavaAstForChildsChildren(curNode, DECLARATION_IMPLEMENTS, stringBuilder);
        stringBuilder.append('{');
        String enumConstants = findChildren(curNode, ENUM_CONSTANTS).map(e -> e.getNode()).map(this::subelementsCommaSeperated).collect(Collectors.joining(","));
        stringBuilder.append(enumConstants).append(';');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
        stringBuilder.append('}');
    }

    private void handleWhileLoop(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("while(").append(artifactData.getDataAsString()).append(')');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleDoWhileLoop(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("do ");
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
        stringBuilder.append(" while(").append(artifactData.getDataAsString()).append(");");
    }


    private void handleExpressionParenthesis(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append('(');

        getChildrenAsStream(curNode).forEach(e -> processJavaAst(stringBuilder, e));

        stringBuilder.append(')');
    }

    private void handleThrowStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("throw ");
        getChildrenAsStream(curNode).forEach(c -> processJavaAst(stringBuilder, c));
        stringBuilder.append(';');
    }

    private void handleSwitchCaseCase(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        getChildrenAsStream(curNode).forEach(e -> processJavaAst(stringBuilder, e));
    }

    private void handleSwitchCase(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString()).append('{');

        getChildrenAsStream(curNode).forEach(e -> processJavaAst(stringBuilder, e));

        stringBuilder.append('}');
    }

    private void handleMethodInvokation(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        boolean isPresent = processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);
        if (isPresent)
            stringBuilder.append('.');
        stringBuilder.append(artifactData.getDataAsString());
        if (hasChild(curNode, PARAMETERS))
            findChildren(curNode, PARAMETERS).forEach(nae -> processJavaAst(stringBuilder, nae));
        else stringBuilder.append("()");
    }


    private void handleClassInstanceCreation(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" new ").append(artifactData.getDataAsString());

        if (hasChild(curNode, PARAMETERS))
            findChildren(curNode, PARAMETERS).forEach(it -> processJavaAst(stringBuilder, it));
        else stringBuilder.append("()");

        getChildrenAsStream(curNode).filter(it -> !PARAMETERS.equals(it.getArtifact().getType())).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleStatementExpression(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
        stringBuilder.append(';');
    }

    private void handleAssignment(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);
        stringBuilder.append(artifactData.getDataAsString());
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleVariableForLoop(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("for(").append(artifactData.getDataAsString()).append(')');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }


    private void handleLambda(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        final boolean hasParanthesis = Boolean.parseBoolean(artifactData.getDataAsString());

        if (hasParanthesis)
            stringBuilder.append('(');

        findChildren(curNode, LAMBDA_PARAMETERS).forEach(it -> processJavaAst(stringBuilder, it));

        if (hasParanthesis)
            stringBuilder.append(')');

        stringBuilder.append("->");

        getChildrenAsStream(curNode).filter(e -> !LAMBDA_PARAMETERS.equals(e.getArtifact().getType())).forEach(nae -> processJavaAst(stringBuilder, nae));
    }

    private void handleSynchronizedStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("synchronized(").append(artifactData.getDataAsString()).append(')');
        curNode.getChildren().stream().map(NodeArtifactEntry::fromNode).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleVariableDeclarationExpression(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString()).append(' ');
        final List<? extends Node> children = curNode.getChildren();
        //Ignore index [0] --> mofifier
        final List<? extends Node> variabledeclarationFragments = children.subList(1, children.size());
        int maxComma = variabledeclarationFragments.size() - 1;

        for (int i = 0; i < variabledeclarationFragments.size(); i++) {
            final Node node = variabledeclarationFragments.get(i);
            processJavaAst(stringBuilder, NodeArtifactEntry.fromNode(node));
            if (i < maxComma)
                stringBuilder.append(',');
        }
    }

    private void handleVariableDeclarationStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        handleVariableDeclarationExpression(stringBuilder, curNode, artifactData);
        stringBuilder.append(';');
    }

    private boolean processJavaAstForChildsChildren(Node node, NodeType nodeType, StringBuilder stringBuilder) {
        return findChildren(node, nodeType).map(NodeArtifactEntry::getNode).map(Node::getChildren).flatMap(Collection::stream)
                .map(NodeArtifactEntry::fromNode).peek(it -> processJavaAst(stringBuilder, it)).count() > 0;
    }

    private void handleVariableDeclarationFragment(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());

        curNode.getChildren().stream().map(NodeArtifactEntry::fromNode).forEach(nae -> processJavaAst(stringBuilder, nae));
    }

    private void handleBlockDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append('{');
        curNode.getChildren().stream().map(NodeArtifactEntry::fromNode).filter(Objects::nonNull)
                .filter(e -> e.getArtifact().getType() != MODIFIER).forEach(nae -> processJavaAst(stringBuilder, nae));
        stringBuilder.append('}');
    }

    private void handleMethodDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        handleGenerics(curNode, stringBuilder);

        processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);
        if (hasChild(curNode, PARAMETERS))
            findChildren(curNode, PARAMETERS).forEach(child -> processJavaAst(stringBuilder, child));
        else
            stringBuilder.append("()");

        findChildren(curNode, THROWS_LIST).forEach(throwsNode -> {
            stringBuilder.append("throws ");
            final String throwsElements = subelementsCommaSeperated(throwsNode);
            stringBuilder.append(throwsElements);
        });

        if (hasChild(curNode, BLOCK))
            findChildren(curNode, BLOCK).forEach(nae -> processJavaAst(stringBuilder, nae));
        else
            stringBuilder.append(';');
    }

    private void handleGenerics(Node curNode, StringBuilder stringBuilder) {
        final List<? extends Node> genericArgs = findChildren(curNode, GENERIC_TYPE_INFO).map(nae -> nae.getNode().getChildren()).flatMap(Collection::stream).collect(Collectors.toList());
        if (genericArgs.size() == 0)
            return;
        stringBuilder.append('<');

        StringJoiner sj = new StringJoiner(",");
        for (Node n : genericArgs) {
            final JavaTreeArtifactData data = (JavaTreeArtifactData) n.getArtifact().getData();
            assert SIMPLE_JUST_A_STRING.equals(data.getType());

            sj.add(data.getDataAsString());
        }

        stringBuilder.append(sj.toString()).append('>');
    }

    private void handleTypeDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());

        handleGenerics(curNode, stringBuilder);

        stringBuilder.append(' ');
        final String extendsPart = findChildren(curNode, DECLARATION_EXTENDS).map(this::subelementsCommaSeperated).collect(Collectors.joining(" "));
        final String implementsPart = findChildren(curNode, DECLARATION_IMPLEMENTS).map(this::subelementsCommaSeperated).collect(Collectors.joining(" "));
        if (!extendsPart.isEmpty())
            stringBuilder.append(" extends ").append(extendsPart);
        if (!implementsPart.isEmpty())
            stringBuilder.append(" implements ").append(implementsPart);
        stringBuilder.append('{');
        //Recursion -> body elements
        findChildren(curNode, AFTER).findFirst().ifPresent(afterNode -> getChildrenAsStream(afterNode).forEach(nae -> processJavaAst(stringBuilder, nae)));
        stringBuilder.append('}');
    }

    private Stream<NodeArtifactEntry> getChildrenAsStream(NodeArtifactEntry nae) {
        return getChildrenAsStream(nae.getNode());
    }

    private Stream<NodeArtifactEntry> getChildrenAsStream(Node node) {
        return node.getChildren().stream()
                .map(NodeArtifactEntry::fromNode)
                .filter(Objects::nonNull);
    }

    private String subelementsCommaSeperated(NodeArtifactEntry entry) {
        return subelementsCommaSeperated(entry.getNode());
    }

    private String subelementsCommaSeperated(Node node) {
        return node.getChildren().stream().map(this::mapToJavaArtifact)
                .map(JavaTreeArtifactData::getDataAsString).collect(Collectors.joining(","));

    }

    private JavaTreeArtifactData mapToJavaArtifact(Node node) {
        final ArtifactData data = node.getArtifact().getData();
        if (data instanceof JavaTreeArtifactData)
            return (JavaTreeArtifactData) data;
        return null;
    }

    private Stream<NodeArtifactEntry> findChildren(Node node, NodeType nodeType) {
        return node.getChildren().stream().sequential().
                filter(e -> nodeType.equals(((JavaTreeArtifactData) e.getArtifact().getData()).getType())).map(NodeArtifactEntry::fromNode);
    }

    private boolean hasChild(Node node, NodeType nodeType) {
        return node.getChildren().stream().
                anyMatch(e -> nodeType.equals(((JavaTreeArtifactData) e.getArtifact().getData()).getType()));
    }

    private void handleCommaSeperatedExpressions(List<? extends Node> children, StringBuilder stringBuilder) {
        final int maxComma = children.size() - 1;
        int i = 0;
        for (Node fragment : children) {
            JavaTreeArtifactData data = (JavaTreeArtifactData) fragment.getArtifact().getData();
            processJavaAst(stringBuilder, fragment, data);
            if (i < maxComma)
                stringBuilder.append(',');
            i++;
        }
    }
}