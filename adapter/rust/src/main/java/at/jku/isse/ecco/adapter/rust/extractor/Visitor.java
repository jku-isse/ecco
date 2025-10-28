package at.jku.isse.ecco.adapter.rust.extractor;

import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Variable;
import org.logicng.io.graphical.GraphicalMermaidWriter;
import org.logicng.io.graphical.GraphicalRepresentation;
import org.logicng.io.graphical.generators.FormulaDagGraphicalGenerator;

import java.util.*;

public class Visitor extends RustParserBaseVisitor<Object> {
    private final String[] codeLines;
    private final ConfigurationPredicateVisitor configVisitor = new ConfigurationPredicateVisitor();
    private final FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();
    private final Assignment assignment = new Assignment();

    public String[] getNonNullCodeLines(){
        return Arrays.stream(codeLines).filter(Objects::nonNull).toArray(String[]::new);
    }

    public Visitor(String[] codeLines, Set<String> features) {
        this.codeLines = codeLines;
        // Initialize assignment with provided features
        for (String feature : features) {
            String featureVar = "#feature_" + feature;
            Variable variable = this.formulaFactory.variable(featureVar);
            this.assignment.addLiteral(variable);
        }
    }

    // Generate and print the graph of a formula in Mermaid format
    public void generateGraph(Formula formula) {
        FormulaDagGraphicalGenerator generator = FormulaDagGraphicalGenerator.builder().build();
        GraphicalRepresentation representation = generator.translate(formula);
        String mermaidString = representation.writeString(GraphicalMermaidWriter.get());
        System.out.println(mermaidString);
    }

    @Override
    public Object visitCfgAttribute(RustParser.CfgAttributeContext ctx) {
        Formula formula = this.configVisitor.visitCfgAttribute(ctx);
        boolean isFeatureUsed = formula.evaluate(this.assignment);
        // If the feature is not used, remove the corresponding lines from codeLines
        if (!isFeatureUsed) {
            // check if we are inside a macro
            ParserRuleContext parent = ctx.getParent();
            if (parent instanceof RustParser.ItemContext itemCtx) {
                if (itemCtx.macroItem() != null) {
                    return null; // Do not remove anything inside macros
                }
            }
            removeLinesForUnusedFeature(ctx);
        }
        // Dont need deeper traversal
        return null;
    }

    // Find the parent item or statement to determine the range of lines to remove
    private void removeLinesForUnusedFeature(ParseTree ctx) {
        // Find the parent item or statement to determine the range of lines to remove
        ParseTree targetParent = findRemovalTarget(ctx);
        if (targetParent instanceof ParserRuleContext itemCtx) {
            removeLines(itemCtx);
        }
    }

    private ParseTree findRemovalTarget(ParseTree ctx) {
        ParseTree current = ctx;
        // skip attributes
        while ((current instanceof RustParser.OuterAttributeContext ||
                current instanceof RustParser.InnerAttributeContext)) {
            current = current.getParent();
        }

        // now find the next item or statement or expression
        while (current != null &&
                !(current instanceof RustParser.ItemContext) &&
                !(current instanceof RustParser.StatementContext) &&
                !(current instanceof RustParser.ExpressionContext)) {
            current = current.getParent();
        }
        return current;
    }

    private void removeLines(ParserRuleContext ctx) {
        int startLine = ctx.getStart().getLine() - 1;
        int endLine = ctx.getStop().getLine() - 1;
        for (int i = startLine; i <= endLine; i++) {
            this.codeLines[i] = null;
        }

        //@TODO hack to remove trailling }
        for (int i = endLine + 1; i < this.codeLines.length; i++) {
            if (this.codeLines[i] != null) {
                String trimmed = this.codeLines[i].trim();
                if (trimmed.equals("}") || trimmed.equals("};")) {
                    this.codeLines[i] = null;
                }
                break; // Stop after first non-null line
            }
        }
    }
}
