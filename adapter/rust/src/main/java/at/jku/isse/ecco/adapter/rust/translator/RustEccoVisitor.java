package at.jku.isse.ecco.adapter.rust.translator;


import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import at.jku.isse.ecco.adapter.rust.translator.structures.Type;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

public class RustEccoVisitor extends RustParserBaseVisitor<Node.Op> {
    private final Node.Op pluginNode;
    private final String[] codeLines;
    private final EntityFactory entityFactory;
    private final Collection<RustParser.OuterAttributeContext> attributeContexts = new ArrayList<>();
    private final Collection<RustParser.Function_Context> functionContexts = new ArrayList<>();
    private final Collection<RustParser.Struct_Context> structContexts = new ArrayList<>();
    private final Collection<RustParser.Trait_Context> traitContexts = new ArrayList<>();
    private final Collection<? extends ParserRuleContext> contexts = new ArrayList<>();
    private final Path path;

    public RustEccoVisitor(Node.Op pluginNode, String[] codeLines, EntityFactory entityFactory, Path path) {
        this.pluginNode = pluginNode;
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
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
        this.collectContexts(translator);
        translator.addChildrenToPluginNode(this.pluginNode);
        return this.pluginNode;
    }

    @Override
    public Node.Op visitVisItem(RustParser.VisItemContext ctx) {
        return super.visitVisItem(ctx);
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
    public Node.Op visitStruct_(RustParser.Struct_Context ctx) {
        this.structContexts.add(ctx);
        return super.visitStruct_(ctx);
    }

    @Override
    public Node.Op visitTrait_(RustParser.Trait_Context ctx) {
        this.traitContexts.add(ctx);
        return super.visitTrait_(ctx);
    }


    @Override
    public Node.Op visitOuterAttribute(RustParser.OuterAttributeContext ctx) {
        this.attributeContexts.add(ctx);
        return super.visitOuterAttribute(ctx);
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
            if (visItemContext.visibility() != null) {
                return Optional.of(visItemContext.visibility().getText());
            }
        }
        return Optional.empty();
    }

    // TODO handle attributes properly
    private Optional<String> attributes(RustParser.Function_Context ctx) {
        var parent = ctx.getParent().getParent();
        RustParser.ItemContext itemContext = (RustParser.ItemContext) parent;
        if (itemContext.outerAttribute() != null) {
            StringBuilder attrs = new StringBuilder();
            for (RustParser.OuterAttributeContext attrCtx : itemContext.outerAttribute()) {
                attrs.append(attrCtx.getText()).append(" ");
            }
            return Optional.of(attrs.toString().trim());
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

    private String getStruct(RustParser.Struct_Context ctx) {
        return ctx.structStruct().identifier().getText();
    }

    private String getTrait(RustParser.Trait_Context ctx) {
        return ctx.getText();
    }

    private void collectContexts(RustEccoTranslator programStructure) {
        this.collectFunctions(programStructure);
        this.collectStructs(programStructure);
        this.collectTraits(programStructure);
    }

    private void collectFunctions(RustEccoTranslator programStructure) {
        for (RustParser.Function_Context ctx : this.functionContexts) {
                String functionSignature = this.getFunctionSignature(ctx);
                programStructure.addStructure(ctx.start.getLine(), ctx.stop.getLine(), functionSignature, Type.FUNCTION);
        }
    }

    private void collectStructs(RustEccoTranslator programStructure) {
        for (RustParser.Struct_Context ctx : this.structContexts) {
            String content = this.getStruct(ctx);
            programStructure.addStructure(ctx.start.getLine(), ctx.stop.getLine(), content, Type.STRUCT);
        }
    }

    private void collectTraits(RustEccoTranslator programStructure) {
        for (RustParser.Trait_Context ctx : this.traitContexts) {
            String content = this.getTrait(ctx);
            programStructure.addStructure(ctx.start.getLine(), ctx.stop.getLine(), content, Type.TRAIT);
        }
    }

}
