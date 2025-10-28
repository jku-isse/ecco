package at.jku.isse.ecco.adapter.rust.translator;


import at.jku.isse.ecco.EccoException;
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
import org.antlr.v4.runtime.Token;
import org.logicng.formulas.Formula;

import java.nio.file.Path;
import java.util.*;

public class RustEccoVisitor extends RustParserBaseVisitor<Node.Op> {
    private final Deque<Node.Op> nodeStack = new ArrayDeque<>();
    private final Node.Op pluginNode;
    private final String[] codeLines;
    private final EntityFactory entityFactory;
    private final Path path;
    private final String configuration;

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
        //does not create LineNodes because writing pub is handled by RustWriter
        Artifact.Op<VisibilityArtifactData> line = this.entityFactory.createArtifact(new VisibilityArtifactData());
        createArtifactOrderedNodeAndAddToParent(line, nodeStack.peek());
        return null;
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
            // module is just a declaration
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
    public Node.Op visitItem(RustParser.ItemContext ctx) {
        Artifact.Op<ItemArtifactData> item = this.entityFactory.createArtifact(new ItemArtifactData());
        Node.Op itemNode = createArtifactOrderedNodeAndAddToParent(item, nodeStack.peek());
        nodeStack.push(itemNode);

        // process outer attributes to get condition for feature trace
        // Item can have multiple outer attributes, so we look for conditions in all of them
        List<String> conditions = ctx.outerAttribute().stream()
                .map(attrCtx -> attrCtx.accept(this))
                .map(node -> node.getProperty("condition"))
                .flatMap(Optional::stream)
                .map(Object::toString)
                .toList();
        String condition = conditions.isEmpty() ? "" : String.join(" & ", conditions); // to handle multiple conditions on a item

        Token stop = ctx.stop;
        // stop can be null in some cases, e.g., for macro items without a proper ending. So attempts to get stop from children
        if (stop == null) {
            if (ctx.macroItem() != null) {
                stop = ctx.macroItem().stop;
            } else if (ctx.visItem() != null) {
                stop = ctx.visItem().stop;
            }
        }
        // if still null throw exception
        if (stop == null) {
            throw new EccoException("Cannot determine end of ItemContext at " + ctx.start.getLine() + " in " + this.path + "\n with text: " + ctx.getText());
        }

        Location location = new Location(ctx.start.getLine(), stop.getLine(), this.path, this.configuration);
        itemNode.putProperty("Location", location);
        FeatureTrace nodeTrace = itemNode.getFeatureTrace();
        nodeTrace.buildProactiveConditionConjunction(condition);

        // visit rest of the children of RustParser.ItemContext if they are present
        if (ctx.macroItem() != null) ctx.macroItem().accept(this);
        if (ctx.visItem() != null) ctx.visItem().accept(this);
        nodeStack.pop();
        return itemNode;
    }

    @Override
    public Node.Op visitConstantItem(RustParser.ConstantItemContext ctx) {
        Artifact.Op<ConstantArtifactData> item = this.entityFactory.createArtifact(new ConstantArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }


    @Override
    public Node.Op visitDocComment(RustParser.DocCommentContext ctx) {
        Artifact.Op<DocArtifactData> doc = this.entityFactory.createArtifact(new DocArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(doc, nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);
        return node;
    }

    @Override
    public Node.Op visitStatements(RustParser.StatementsContext ctx) {
        this.addLineNodesFromContext(nodeStack.peek(), ctx);

        // when super.visitStatements comes to a macro the tree looks like statement -> item -> macroItem(thus adding a item artifact)
        // which means going deeper in parseTree not needed here
        return nodeStack.peek();
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
        this.addLineNodesFromContext(node, ctx);
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

    @Override
    public Node.Op visitOuterAttribute(RustParser.OuterAttributeContext ctx) {
        //if the outerAttribute is a comment only visit the comment
        if (ctx.docComment() != null) return visitDocComment(ctx.docComment());

        // Visit cfg attribute and convert to condition(formula)
        Formula condition = null;
        RustParser.CfgAttributeContext attrCtx = ctx.attr().cfgAttribute();
        if (attrCtx != null) {
            ConfigurationPredicateVisitor configVisitor = new ConfigurationPredicateVisitor();
            condition = configVisitor.visitCfgAttribute(attrCtx);
        }

        Artifact.Op<AttributeArtifactData> item = this.entityFactory.createArtifact(new AttributeArtifactData(getString(ctx)));
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        // Store condition in node to use it in parent item
        if (attrCtx != null) node.putProperty("condition", condition.toString());
        return node;
    }

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

        if (ctx.enumItems() == null) {
            // No enum items, add all lines of the enum
            this.addLineNodes(node, enumStartLine, enumEndLine);
            return node;
        }
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
    public Node.Op visitEnumItem(RustParser.EnumItemContext ctx) {
        // content of enumArtifact is not used, it is only used as an identifier for the artifact, so the ecco hashcode and equals work properly
        Artifact.Op<EnumItemArtifactData> item = this.entityFactory.createArtifact(new EnumItemArtifactData(getString(ctx.identifier())));
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());

        this.addLineNodesFromContext(node, ctx);

        return node;
    }

    @Override
    public Node.Op visitMacroInvocationSemi(RustParser.MacroInvocationSemiContext ctx) {
        Artifact.Op<MacroInvocationArtifactData> item = this.entityFactory.createArtifact(new MacroInvocationArtifactData());
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        int startLine = ctx.getStart().getLine();
        int endLine = ctx.getStop().getLine();

        // @TODO takes the whole line, could be improved to only take the macro invocation part
        this.addLineNodes(node, startLine, endLine);
        return node;
    }

    // Content of a MacroItems is TokenTrees which are not further parsed, so we just add the whole macro as line artifacts
    // Which means conditionals inside macros are not handled
    @Override
    public Node.Op visitMacroRulesDefinition(RustParser.MacroRulesDefinitionContext ctx) {
        String identifier = ctx.identifier().getText();
        Artifact.Op<MacroRulesArtifactData> item = this.entityFactory.createArtifact(new MacroRulesArtifactData(identifier));
        Node.Op node = createArtifactOrderedNodeAndAddToParent(item, this.nodeStack.peek());
        this.addLineNodesFromContext(node, ctx);

        return node;

    }

    /** Get the function signature as a string from the given Function_Context
     * @param ctx the Function_Context to extract the signature from
     * @return String representing the function signature
     */
    private String getFunctionSignature(RustParser.Function_Context ctx) {
        StringBuilder sig = new StringBuilder();

        // optional qualifiers
        if (ctx.functionQualifiers() != null) {
            // getString(ctx.functionQualifiers()) does not always work, since the stop variable can be null
            RustParser.FunctionQualifiersContext qualifiers = ctx.functionQualifiers();
            if (qualifiers.KW_CONST() != null) sig.append("const ");
            if (qualifiers.KW_ASYNC() != null) sig.append("async ");
            if (qualifiers.KW_UNSAFE() != null) sig.append("unsafe ");
            if (qualifiers.KW_EXTERN() != null) sig.append("extern ");
            if (qualifiers.abi() != null) sig.append(getString(qualifiers.abi())).append(" ");
        }
        // “fn” and name
        sig.append("fn").append(" ").append(getString(ctx.identifier()));

        // optional generics
        if (ctx.genericParams() != null) {
            sig.append(getString(ctx.genericParams()));
        }
        // parameters
        sig.append("(");
        if (ctx.functionParameters() != null) {
            sig.append(getString(ctx.functionParameters()));
        }
        sig.append(")");
        // optional return type
        if (ctx.functionReturnType() != null) {
            sig.append(" ").append(getString(ctx.functionReturnType()));
        }
        // optional where‐clause
        if (ctx.whereClause() != null) {
            sig.append(" ").append(getString(ctx.whereClause()));
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
        if (startLine == endLine) {
            // Single line
            String codeLine = this.codeLines[startLine - 1];
            if (!codeLine.isEmpty()) {
                String extractedLine = codeLine.substring(startPosition, Math.min(endPosition, codeLine.length()));
                Artifact.Op<LineArtifactData> lineArtifactData = this.entityFactory.createArtifact(new LineArtifactData(extractedLine));
                createArtifactOrderedNodeAndAddToParent(lineArtifactData, parentNode);
            }
            return;
        }

        for (int i = startLine; i <= endLine; i++) {
            // -1 for 0 based index
            String codeLine = this.codeLines[i - 1];
            if (codeLine.isEmpty()) {
                continue;
            }
            String extractedLine;
            if (i == startLine) {
                // First line
                extractedLine = codeLine.substring(startPosition);
            } else if (i == endLine) {
                // Last line
                extractedLine = codeLine.substring(0, Math.min(endPosition, codeLine.length()));
            } else {
                // Middle lines
                extractedLine = codeLine;
            }
            Artifact.Op<LineArtifactData> lineArtifactData = this.entityFactory.createArtifact(new LineArtifactData(extractedLine));
            createArtifactOrderedNodeAndAddToParent(lineArtifactData, parentNode);
        }
    }

    /**
     * helper function to add all lines from a context as line artifacts to a parent node
     * @param parentNode
     * @param ctx
     */
    private void addLineNodesFromContext(Node.Op parentNode, ParserRuleContext ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();
        int startPos = ctx.start.getCharPositionInLine();
        int stopPos = ctx.stop.getCharPositionInLine() + ctx.stop.getText().length();

        this.addLineNodes(parentNode, startLine, stopLine, startPos, stopPos);
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

        if (startLine == stopLine) {
            return this.codeLines[startLine - 1].substring(startPosition, endPosition);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= stopLine; i++) {
            String codeLine;
            // Handle 1 based line numbers and 0 based array index
            codeLine = this.codeLines[i - 1];
            if (codeLine.isEmpty()) {
                continue;
            }
            if (i == startLine) {
                // first Line
                sb.append(codeLine.substring(startPosition)).append("\n");
            } else if (i == stopLine) {
                // last Line
                sb.append(codeLine, 0, Math.min(endPosition, codeLine.length()));
            } else {
                // Middle line
                sb.append(codeLine);
            }
        }
        return sb.toString();
    }



}
