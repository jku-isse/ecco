package at.jku.isse.ecco.adapter.rust.translator;


import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import at.jku.isse.ecco.adapter.rust.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.checkerframework.checker.units.qual.A;

import java.nio.file.Path;
import java.util.*;

public class RustEccoVisitor extends RustParserBaseVisitor<Node.Op> {
    private final Deque<Node.Op> nodeStack = new ArrayDeque<>();
    private final Node.Op pluginNode;
    private final String[] codeLines;
    private final EntityFactory entityFactory;
    private final Path path;

    public RustEccoVisitor(Node.Op pluginNode, String[] codeLines, EntityFactory entityFactory, Path path) {
        this.pluginNode = pluginNode;
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
        this.path = path;
        nodeStack.push(pluginNode);
    }

    public Node.Op translate(ParseTree tree) {
        return tree.accept(this);
    }

    /**
     * Visit a parse tree produced by {@link RustParser#crate}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Node.Op visitCrate(RustParser.CrateContext ctx) {
        for (ParseTree parseTree : ctx.item()) {
            parseTree.accept(this);
        }
        return this.pluginNode;
    }


    @Override
    public Node.Op visitVisibility(RustParser.VisibilityContext ctx) {
        //create line artifacts for visibility
        Artifact.Op<VisibilityArtifactData> line = this.entityFactory.createArtifact(new VisibilityArtifactData());
        return createArtifactOrderedNodeAndAddToParent(line, nodeStack.peek());
    }

    @Override
    public Node.Op visitModule(RustParser.ModuleContext ctx) {
        StringBuilder sig = new StringBuilder();
        if (ctx.KW_UNSAFE() != null) {
            sig.append("unsafe ");
        }
        sig.append("mod ")
                .append(ctx.identifier().getText())
                .append(" ");
        if (ctx.SEMI() != null) {
            sig.append(";");
        } else {
            //module contains something
            sig.append("{");
        }
        Artifact.Op<ModuleArtifactData> moduleArtifact = this.entityFactory.createArtifact(new ModuleArtifactData(sig.toString()));
        Node.Op moduleNode = createArtifactOrderedNodeAndAddToParent(moduleArtifact, nodeStack.peek());

        // no semicolon means module contains items
        if (ctx.SEMI() == null) {
            nodeStack.push(moduleNode);
            ctx.item().forEach(item -> item.accept(this));
            nodeStack.pop();
            Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData("}"));
            moduleNode.addChild(this.entityFactory.createOrderedNode(line));
        }
        return moduleNode;
    }

    @Override
    public Node.Op visitVisItem(RustParser.VisItemContext ctx) {
        return super.visitVisItem(ctx);
    }

    @Override
    public Node.Op visitItem(RustParser.ItemContext ctx) {
        Artifact.Op<ItemArtifactData> item = this.entityFactory.createArtifact(new ItemArtifactData());
        Node.Op itemNode = createArtifactOrderedNodeAndAddToParent(item, nodeStack.peek());

        nodeStack.push(itemNode);
        Node.Op visit = super.visitItem(ctx);
        nodeStack.pop();
        return visit;
    }

    @Override
    public Node.Op visitDocComment(RustParser.DocCommentContext ctx) {
        Artifact.Op<DocArtifactData> doc = this.entityFactory.createArtifact(new DocArtifactData());
        Node.Op docNode = createArtifactOrderedNodeAndAddToParent(doc, nodeStack.peek());
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        this.addLineNodes(docNode, startLine, stopLine);
        return docNode;
    }

//    @Override
//    public Node.Op visitComment(RustParser.CommentContext ctx) {
//        Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData(ctx.getText()));
//        return createArtifactOrderedNodeAndAddToParent(line, nodeStack.peek());
//    }

    @Override
    public Node.Op visitStatements(RustParser.StatementsContext ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        this.addLineNodes(nodeStack.peek(), startLine, stopLine);

        // when super.visitStatements comes to a macro the tree looks like statement -> item -> macroItem(thus adding a item artifact)
        // which means going deeper in parseTree not needed here
        return nodeStack.peek();
        // return super.visitStatements(ctx);
    }

    @Override
    public Node.Op visitBlockExpression(RustParser.BlockExpressionContext ctx) {
        Artifact.Op<BlockArtifactData> item = this.entityFactory.createArtifact(new BlockArtifactData());
        // node is ordered so the nodes are in sequence
        Node.Op blockNode = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        if (ctx.LCURLYBRACE() != null) {
            Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData("{"));
            blockNode.addChild(this.entityFactory.createOrderedNode(line));
        }
        this.nodeStack.push(blockNode);
        Node.Op visited = super.visitBlockExpression(ctx);
        nodeStack.pop();
        if (ctx.RCURLYBRACE() != null) {
            Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData("}"));
            blockNode.addChild(this.entityFactory.createOrderedNode(line));
        }
        return visited;
    }


    @Override
    public Node.Op visitFunction_(RustParser.Function_Context ctx) {
        Artifact.Op<FunctionArtifactData> item = this.entityFactory.createArtifact(new FunctionArtifactData(this.getFunctionSignature(ctx)));
        Node.Op functionNode = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());

        this.nodeStack.push(functionNode);
        Node.Op visited = super.visitFunction_(ctx);
        this.nodeStack.pop();
        return visited;
    }

    //TODO saw an case where } was missing, cant reproduce
    @Override
    public Node.Op visitStruct_(RustParser.Struct_Context ctx) {
        Artifact.Op<StructArtifactData> item = this.entityFactory.createArtifact(new StructArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        int startPosition = ctx.start.getCharPositionInLine();
        int endPosition = ctx.stop.getCharPositionInLine() + ctx.stop.getText().length();
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        this.addLineNodes(node, startLine, stopLine, startPosition,  endPosition);

        return node;
    }

    @Override
    public Node.Op visitTypeAlias(RustParser.TypeAliasContext ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        Artifact.Op<TypeAliasArtifactData> item = this.entityFactory.createArtifact(new TypeAliasArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodes(node, startLine, stopLine);
        return node;
    }

    @Override
    public Node.Op visitTrait_(RustParser.Trait_Context ctx) {
        Artifact.Op<TraitArtifactData> item = this.entityFactory.createArtifact(new TraitArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());

        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        this.addLineNodes(node, startLine, stopLine);

        //Stopping here, no need to go deeper
        return node;
    }

    //TODO .getText does not respect spaces
    @Override
    public Node.Op visitOuterAttribute(RustParser.OuterAttributeContext ctx) {
        //if the outerAttribute is a comment only visit the comment
        if (ctx.docComment() != null) return visitDocComment(ctx.docComment());

        Artifact.Op<AttributeArtifactData> item = this.entityFactory.createArtifact(new AttributeArtifactData(getString(ctx)));
        return createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
    }

    //TODO .getText does not respect spaces
    @Override
    public Node.Op visitInnerAttribute(RustParser.InnerAttributeContext ctx) {
        Artifact.Op<InnerAttributeArtifactData> item = this.entityFactory.createArtifact(new InnerAttributeArtifactData(getString(ctx)));
        return createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
    }

    @Override
    public Node.Op visitImplementation(RustParser.ImplementationContext ctx) {
        int startLine = ctx.start.getLine();
        int endLine = ctx.stop.getLine();
        Artifact.Op<ImplementationArtifactData> item = this.entityFactory.createArtifact(new ImplementationArtifactData());
        Node.Op node = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());

        this.addLineNodes(node, startLine, endLine);

        return node;
    }

    @Override
    public Node.Op visitUnion_(RustParser.Union_Context ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        Artifact.Op<UnionArtifactData> item = this.entityFactory.createArtifact(new UnionArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodes(node, startLine, stopLine);
        return node;
    }

    @Override
    public Node.Op visitUseDeclaration(RustParser.UseDeclarationContext ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        Artifact.Op<UseDeclarationArtifactData> item = this.entityFactory.createArtifact(new UseDeclarationArtifactData());
        Node.Op node = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodes(node, startLine, stopLine);
        return node;
    }

    @Override
    public Node.Op visitEnumeration(RustParser.EnumerationContext ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        Artifact.Op<EnumArtifactData> item = this.entityFactory.createArtifact(new EnumArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodes(node, startLine, stopLine);

        return node;
    }

    private String getFunctionSignature(RustParser.Function_Context ctx) {
        StringBuilder sig = new StringBuilder();

        // optional qualifiers
        if (ctx.functionQualifiers() != null) {
            String qualifiers = (ctx.functionQualifiers().getText());
            if (!qualifiers.isEmpty()) {
                sig.append(qualifiers).append(" ");
            }
        }
        // “fn” and name
        sig.append("fn").append(" ").append(ctx.identifier().getText());

        // optional generics
        if (ctx.genericParams() != null) {
            sig.append(ctx.genericParams().getText());
        }
        // parameters
        sig.append("(");
        if (ctx.functionParameters() != null) {
            // TODO .getText() ignores spaces and thus not writing source code properly
            sig.append(ctx.functionParameters().getText());
        }
        sig.append(")");
        // optional return type
        if (ctx.functionReturnType() != null) {
            sig.append(" ").append(ctx.functionReturnType().getText());
        }
        // optional where‐clause
        if (ctx.whereClause() != null) {
            sig.append(" ").append(ctx.whereClause().getText());
        }
        return sig.toString();
    }

    private void addLineNodes(Node.Op parentNode, int startLine, int endLine) {
        for (int i = startLine; i <= endLine; i++) {
            // -1 for 0 based index
            String codeLine = this.codeLines[i - 1];
            if (codeLine.isEmpty()) {
                continue;
            }
            Artifact.Op<LineArtifactData> lineArtifactData = this.entityFactory.createArtifact(new LineArtifactData(codeLine));
            createArtifactOrderedNodeAndAddToParent(lineArtifactData, parentNode);
        }
    }

    private void addLineNodes(Node.Op parentNode, int startLine, int endLine, int startPosition, int endPosition) {
        for (int i = startLine; i <= endLine; i++) {
            // -1 for 0 based index
            String codeLine = this.codeLines[i - 1];
            if (codeLine.isEmpty()) {
                continue;
            }
            if (i == startLine && i == endLine) {
                codeLine = codeLine.substring(startPosition, endPosition);
            } else if (i == startLine) {
                codeLine = codeLine.substring(startPosition);
            } else if (i == endLine) {
                codeLine = codeLine.substring(0, endPosition);
            }
            Artifact.Op<LineArtifactData> lineArtifactData = this.entityFactory.createArtifact(new LineArtifactData(codeLine));
            createArtifactOrderedNodeAndAddToParent(lineArtifactData, parentNode);
        }
    }

    /**
     * unordered artifact must be uniquely identifiable just by their contained data object, as it is the only means of identification aside from their sequence number.
     *  No two child artifacts can contain equal data objects.
     * @param artifact
     * @param parentNode
     * @return <T extends ArtifactData>
     * @param <T>
     */
    private <T extends ArtifactData> Node.Op createArtifactNodeAndAddToParent(Artifact.Op<T> artifact, Node.Op parentNode) {
        Node.Op node = this.entityFactory.createNode(artifact);
        assert parentNode != null;
        parentNode.addChild(node);
        return node;
    }

    /**
     * Ordered artifact are assigned sequence numbers based on their order of occurrence.
     * This assigned sequence number is used as an additional means of identifying the child artifacts
     * @param artifact
     * @param parentNode
     * @return <T extends ArtifactData>
     * @param <T>
     */
    private <T extends ArtifactData> Node.Op createArtifactOrderedNodeAndAddToParent(Artifact.Op<T> artifact, Node.Op parentNode) {
        Node.Op node = this.entityFactory.createOrderedNode(artifact);
        assert parentNode != null;
        parentNode.addChild(node);
        return node;
    }

    /**
     * Get the original source code string represented by the given parse tree context.
     * @param ctx the parse tree context
     * @return String of the original source code represented by the context
     * @param <T> some type of ParserRuleContext
     */
    public <T extends ParserRuleContext> String getString(T ctx) {
        int startPosition = ctx.start.getCharPositionInLine();
        int endPosition = ctx.stop.getCharPositionInLine() + ctx.stop.getText().length();
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();

        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= stopLine; i++) {
            // -1 for 0 based index
            String codeLine = this.codeLines[i - 1];
            if (codeLine.isEmpty()) {
                continue;
            }
            if (i == startLine && i == stopLine) {
                sb.append(codeLine, startPosition, endPosition);
            } else if (i == startLine) {
                sb.append(codeLine.substring(startPosition)).append("\n");
            } else if (i == stopLine) {
                sb.append(codeLine, 0, endPosition);
            } else {
                sb.append(codeLine);
            }
        }
        return sb.toString();
    }



}
