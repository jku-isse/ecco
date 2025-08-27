package at.jku.isse.ecco.adapter.rust.translator;


import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.antlr.v4.runtime.tree.ParseTree;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;

public class RustEccoVisitor extends RustParserBaseVisitor<Node.Op> {
    private final Node.Op pluginNode;
    private final String[] codeLines;
    private final EntityFactory entityFactory;
    private final Collection<RustParser.Function_Context> functionContexts;
    private final Path path;

    public RustEccoVisitor(Node.Op pluginNode, String[] codeLines, EntityFactory entityFactory, Path path) {
        this.pluginNode = pluginNode;
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
        this.functionContexts = new LinkedList<>();
        this.path = path;
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
        RustEccoTranslator translator = new RustEccoTranslator(
                this.codeLines, this.entityFactory, this.path);
        this.collectFunctions(translator);
        translator.addChildrenToPluginNode(this.pluginNode);
        return this.pluginNode;

    }

    @Override
    public Node.Op visitItem(RustParser.ItemContext ctx) {
        return super.visitItem(ctx);
    }

    @Override
    public Node.Op visitFunction_(RustParser.Function_Context ctx) {

        this.functionContexts.add(ctx);
        return super.visitFunction_(ctx);
    }

    @Override
    public Node.Op visitFunctionQualifiers(RustParser.FunctionQualifiersContext ctx) {
        return super.visitFunctionQualifiers(ctx);
    }

    @Override
    public Node.Op visitFunctionParam(RustParser.FunctionParamContext ctx) {
        return super.visitFunctionParam(ctx);
    }

    @Override
    public Node.Op visitFunctionParamPattern(RustParser.FunctionParamPatternContext ctx) {
        return super.visitFunctionParamPattern(ctx);
    }

    @Override
    public Node.Op visitFunctionReturnType(RustParser.FunctionReturnTypeContext ctx) {
        return super.visitFunctionReturnType(ctx);
    }

    private boolean checkFunction(RustParser.Function_Context ctx) {
        // Check if the function has a valid signature
        return ctx.identifier() != null &&
                ctx.KW_FN() != null; // Ensure it has a function name and the "fn" keyword
    }

    // TODO handle visibility properly
    private Optional<String> functionVisibility(RustParser.Function_Context ctx) {
        var parent = ctx.getParent();
        if (parent instanceof RustParser.VisItemContext) {
            RustParser.VisItemContext visItemContext = (RustParser.VisItemContext) parent;
            if (visItemContext.visibility() != null){
                return Optional.of(visItemContext.visibility().getText());
            }
        }
        return Optional.empty();
    }

    private String getFunctionSignature(RustParser.Function_Context ctx) {
        StringBuilder sig = new StringBuilder();
        // visibility
        this.functionVisibility(ctx).ifPresent(visibility -> sig.append(visibility).append(" "));

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

    private void collectFunctions(RustEccoTranslator programStructure) {
        for (RustParser.Function_Context ctx : this.functionContexts) {
            if (this.checkFunction(ctx)) {
                String functionSignature = this.getFunctionSignature(ctx);
                programStructure.addFunctionStructure(ctx.start.getLine(), ctx.stop.getLine(), functionSignature);
            }
        }
    }


}
