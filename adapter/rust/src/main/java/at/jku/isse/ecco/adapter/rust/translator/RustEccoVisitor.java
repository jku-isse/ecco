package at.jku.isse.ecco.adapter.rust.translator;


import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import at.jku.isse.ecco.adapter.rust.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.antlr.v4.runtime.tree.ParseTree;

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
    public Node.Op visitVisItem(RustParser.VisItemContext ctx) {
        return super.visitVisItem(ctx);
    }

    @Override
    public Node.Op visitItem(RustParser.ItemContext ctx) {
        Artifact.Op<ItemArtifactData> item = this.entityFactory.createArtifact(new ItemArtifactData());
        Node.Op itemNode = createArtifactNodeAndAddToParent(item, nodeStack.peek());

        nodeStack.push(itemNode);
        Node.Op visit = super.visitItem(ctx);
        nodeStack.pop();
        return visit;
    }

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
        Node.Op blockNode = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());
        // TODO this curly brace is on a separate line, and not respecting source code
        if (ctx.LCURLYBRACE() != null) {
            Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData(ctx.LCURLYBRACE().getText()));
            blockNode.addChild(this.entityFactory.createNode(line));
        }
        if (this.nodeStack.peek() == null) {
            throw new IllegalStateException("Node stack is empty, which should never happen");
        }
        this.nodeStack.push(blockNode);
        Node.Op visited = super.visitBlockExpression(ctx);
        if (ctx.RCURLYBRACE() != null) {
            Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData(ctx.RCURLYBRACE().getText()));
            this.nodeStack.peek().addChild(this.entityFactory.createNode(line));
        }
        nodeStack.pop();
        return visited;
    }


    @Override
    public Node.Op visitFunction_(RustParser.Function_Context ctx) {
        Artifact.Op<FunctionArtifactData> item = this.entityFactory.createArtifact(new FunctionArtifactData(this.getFunctionSignature(ctx)));
        Node.Op functionNode = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());

        // now we decend into the function node
        this.nodeStack.push(functionNode);
        Node.Op visited = super.visitFunction_(ctx);
        this.nodeStack.pop();
        return visited;
    }

    @Override
    public Node.Op visitStruct_(RustParser.Struct_Context ctx) {
        Artifact.Op<StructArtifactData> item = this.entityFactory.createArtifact(new StructArtifactData());
        Node.Op node = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());

        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        this.addLineNodes(node, startLine, stopLine);

        //stopping here, no need to go deeper
        return node;
    }

    // TODO handle traits
    @Override
    public Node.Op visitTrait_(RustParser.Trait_Context ctx) {
        Artifact.Op<TraitArtifactData> item = this.entityFactory.createArtifact(new TraitArtifactData());
        Node.Op node = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());

        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        this.addLineNodes(node, startLine, stopLine);

        //Stopping here, no need to go deeper
        return node;
    }

    @Override
    public Node.Op visitOuterAttribute(RustParser.OuterAttributeContext ctx) {
        Artifact.Op<AttributeArtifactData> item = this.entityFactory.createArtifact(new AttributeArtifactData(ctx.getText()));
        return createArtifactNodeAndAddToParent(item, this.nodeStack.peek());
    }

    //TODO implementation simply adds lines.
    @Override
    public Node.Op visitImplementation(RustParser.ImplementationContext ctx) {
        int startLine = ctx.start.getLine();
        int endLine = ctx.stop.getLine();
        Artifact.Op<ImplementationArtifactData> item = this.entityFactory.createArtifact(new ImplementationArtifactData());
        Node.Op node = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());

        this.addLineNodes(node, startLine, endLine);

        return node;
    }

    // TODO only adds lines
    @Override
    public Node.Op visitInnerAttribute(RustParser.InnerAttributeContext ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        this.addLineNodes(nodeStack.peek(), startLine, stopLine);
        return super.visitInnerAttribute(ctx);
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

    private String getFunctionSignature(RustParser.Function_Context ctx) {
        StringBuilder sig = new StringBuilder();

        // optional qualifiers
        if (ctx.functionQualifiers() != null) {
            sig.append(ctx.functionQualifiers().getText()).append(" ");
        }
        // “fn” and name
        sig.append(ctx.KW_FN().getText()).append(" ")
                .append(ctx.identifier().getText());

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
            createArtifactNodeAndAddToParent(lineArtifactData, parentNode);
        }
    }

    private <T extends ArtifactData> Node.Op createArtifactNodeAndAddToParent(Artifact.Op<T> artifact, Node.Op parentNode) {
        Node.Op node = this.entityFactory.createNode(artifact);
        assert parentNode != null;
        parentNode.addChild(node);
        return node;
    }


}
