package at.jku.isse.ecco.adapter.rust.translator;


import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import at.jku.isse.ecco.adapter.rust.data.*;
import at.jku.isse.ecco.adapter.rust.extractor.ConfigurationPredicateVisitor;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.logicng.formulas.Formula;

import java.nio.file.Path;
import java.util.*;

public class RustEccoVisitor extends RustParserBaseVisitor<Node.Op> {
    private final Deque<Node.Op> nodeStack = new ArrayDeque<>();
    private final Node.Op pluginNode;
    private final String[] codeLines;
    private final EntityFactory entityFactory;
    private final Path path;
    private String configuration;

    public RustEccoVisitor(Node.Op pluginNode, String[] codeLines, EntityFactory entityFactory, Path path, String configuration) {
        this.pluginNode = pluginNode;
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
        this.path = path;
        this.configuration = configuration;
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

    // Comments in module not tracked since they are not parsed
    @Override
    public Node.Op visitModule(RustParser.ModuleContext ctx) {
        StringBuilder sig = new StringBuilder();
        if (ctx.KW_UNSAFE() != null) {
            sig.append("unsafe ");
        }
        sig.append("mod ")
                .append(getString(ctx.identifier()))
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

        // process outer attributes to get condition for feature trace
        // Item can have multiple outer attributes, so we look for conditions in all of them
        String condition = ctx.outerAttribute().stream()
                .map(attrCtx -> attrCtx.accept(this))
                .map(node -> node.getProperty("condition"))
                .flatMap(Optional::stream)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse("");

        Location location = new Location(ctx.start.getLine(), ctx.stop.getLine(), this.path, this.configuration);
        itemNode.putProperty("Location", location);
        FeatureTrace nodeTrace = itemNode.getFeatureTrace();
        nodeTrace.buildProactiveConditionConjunction(condition);

        // visit rest of the children of RustParser.ItemContext if they are present
        nodeStack.push(itemNode);
        if (ctx.macroItem() != null) ctx.macroItem().accept(this);
        if (ctx.visItem() != null) ctx.visItem().accept(this);
        nodeStack.pop();
        return itemNode;
    }

    @Override
    public Node.Op visitDocComment(RustParser.DocCommentContext ctx) {
        Artifact.Op<DocArtifactData> doc = this.entityFactory.createArtifact(new DocArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(doc, nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }

//    @Override
//    public Node.Op visitComment(RustParser.CommentContext ctx) {
//        Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData(ctx.getText()));
//        return createArtifactOrderedNodeAndAddToParent(line, nodeStack.peek());
//    }

    @Override
    public Node.Op visitStatements(RustParser.StatementsContext ctx) {
        this.addLineNodesFromContext(nodeStack.peek(), ctx);

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
        Artifact.Op<TypeAliasArtifactData> item = this.entityFactory.createArtifact(new TypeAliasArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }

    @Override
    public Node.Op visitTrait_(RustParser.Trait_Context ctx) {
        Artifact.Op<TraitArtifactData> item = this.entityFactory.createArtifact(new TraitArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }

    //TODO .getText does not respect spaces
    @Override
    public Node.Op visitOuterAttribute(RustParser.OuterAttributeContext ctx) {
        //if the outerAttribute is a comment only visit the comment
        if (ctx.docComment() != null) return visitDocComment(ctx.docComment());
        Formula condition = null;
        RustParser.CfgAttributeContext attrCtx = ctx.attr().cfgAttribute();
        if (attrCtx != null) {
            ConfigurationPredicateVisitor configVisitor = new ConfigurationPredicateVisitor();
            condition = configVisitor.visitCfgAttribute(attrCtx);
        }
        Artifact.Op<AttributeArtifactData> item = this.entityFactory.createArtifact(new AttributeArtifactData(getString(ctx)));
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());

        if (attrCtx != null) node.putProperty("condition", condition.toString());
        return node;
    }

    //TODO .getText does not respect spaces
    @Override
    public Node.Op visitInnerAttribute(RustParser.InnerAttributeContext ctx) {
        Artifact.Op<InnerAttributeArtifactData> item = this.entityFactory.createArtifact(new InnerAttributeArtifactData(getString(ctx)));
        return createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
    }

    @Override
    public Node.Op visitImplementation(RustParser.ImplementationContext ctx) {
        Artifact.Op<ImplementationArtifactData> item = this.entityFactory.createArtifact(new ImplementationArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }

    @Override
    public Node.Op visitUnion_(RustParser.Union_Context ctx) {
        Artifact.Op<UnionArtifactData> item = this.entityFactory.createArtifact(new UnionArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }

    @Override
    public Node.Op visitUseDeclaration(RustParser.UseDeclarationContext ctx) {
        Artifact.Op<UseDeclarationArtifactData> item = this.entityFactory.createArtifact(new UseDeclarationArtifactData());
        Node.Op node = createArtifactNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }

    @Override
    public Node.Op visitEnumeration(RustParser.EnumerationContext ctx) {
        Artifact.Op<EnumArtifactData> item = this.entityFactory.createArtifact(new EnumArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());

        // Inside visitEnumeration
        int enumStartLine = ctx.getStart().getLine();
        int enumEndLine = ctx.getStop().getLine();
        int itemsStartLine = ctx.enumItems().getStart().getLine();
        int itemsEndLine = ctx.enumItems().getStop().getLine();

        // Add lines before enumItems
        this.addLineNodes(node, enumStartLine, itemsStartLine-1);

        // Visit enumItems to add them as child nodes
        this.nodeStack.push(node);
        ctx.enumItems().enumItem().forEach(enumItemContext -> enumItemContext.accept(this));
        this.nodeStack.pop();

        // Add lines after enumItems
        this.addLineNodes(node, itemsEndLine+1, enumEndLine);

        return node;
    }

    @Override
    public Node.Op visitEnumItems(RustParser.EnumItemsContext ctx) {
        return super.visitEnumItems(ctx);
    }

    // TODO does not support merging two enums if they differ in something like struct fields of a item since they are only line nodes
    @Override
    public Node.Op visitEnumItem(RustParser.EnumItemContext ctx) {
        // content of enumArtifact is not used, it is only used as an identifier for the artifact, so the ecco hashcode and equals work properly
        Artifact.Op<EnumItemArtifactData> item = this.entityFactory.createArtifact(new EnumItemArtifactData(ctx.identifier().getText()));
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());

        this.addLineNodesFromContext(node, ctx);

        return node;
    }

    private String getFunctionSignature(RustParser.Function_Context ctx) {
        StringBuilder sig = new StringBuilder();

        // optional qualifiers
        if (ctx.functionQualifiers() != null) {
            String qualifiers = (getString(ctx.functionQualifiers()));
            if (!qualifiers.isEmpty()) {
                sig.append(qualifiers).append(" ");
            }
        }
        // “fn” and name
        sig.append("fn").append(" ").append(ctx.identifier().getText());

        // optional generics
        if (ctx.genericParams() != null) {
            sig.append(getString(ctx.genericParams()));
        }
        // parameters
        sig.append("(");
        if (ctx.functionParameters() != null) {
            // TODO .getText() ignores spaces and thus not writing source code properly
            sig.append(getString(ctx.functionParameters()));
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
        // Add space before the function body
        sig.append(" ");
        return sig.toString();
    }

    /**
     * Add line artifacts for each line from startLine to endLine (inclusive) to the given parent node.
     * @param parentNode
     * @param startLine
     * @param endLine
     */
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

    /**
     * Add line artifacts for each line from startLine to endLine (inclusive) to the given parent node.
     * Only the part of the first and last line between startPosition and endPosition is added.
     * @param parentNode
     * @param startLine
     * @param endLine
     * @param startPosition
     * @param endPosition
     */
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
     * helper function to add all lines from a context as line artifacts to a parent node
     * @param parentNode
     * @param ctx
     */
    private void addLineNodesFromContext(Node.Op parentNode, ParserRuleContext ctx) {
        addLineNodes(parentNode, ctx.start.getLine(), ctx.stop.getLine());
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
     * @return Some ArtifactData
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
     */
    public String getString(ParserRuleContext ctx) {
        if (ctx == null) {
            return "";
        }

        int startPosition = ctx.start.getCharPositionInLine();
        int endPosition = ctx.stop.getCharPositionInLine() + ctx.stop.getText().length();
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();


        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= stopLine; i++) {
            String codeLine;
            // Handle 1 based line numbers and 0 based array index
            if (i == 0) {
                codeLine = this.codeLines[0];
            } else {
                codeLine = this.codeLines[i - 1];
            }
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
