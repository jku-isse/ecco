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
        // System.out.println("CFG Attribute: " + ctx.getText() + " -> " + formula + " -> " + isFeatureUsed);
        // If the feature is not used, remove the corresponding lines from codeLines
        if (!isFeatureUsed) {
            removeLinesForUnusedFeature(ctx);
        }
        // Dont need deeper traversal
        return null;
    }

    // Find the parent item or statement to determine the range of lines to remove
    private void removeLinesForUnusedFeature(ParseTree ctx) {
        // Find the parent item or statement to determine the range of lines to remove
        ParseTree parent = ctx.getParent();
        while (parent != null
                && !(parent instanceof RustParser.ItemContext)
                && !(parent instanceof RustParser.StatementContext)) {
            parent = parent.getParent();
        }
        ParserRuleContext itemCtx;
        if (parent instanceof RustParser.ItemContext) {
            itemCtx = (RustParser.ItemContext) parent;
        } else if (parent instanceof RustParser.StatementContext) {
            itemCtx = (RustParser.StatementContext) parent;
        } else {
            throw new IllegalStateException(ctx.getText() + " has no parent item or statement.");
        }
        int startLine = itemCtx.getStart().getLine()-1;
        int endLine = itemCtx.getStop().getLine()-1;
        for (int i = startLine; i <= endLine; i++) {
            this.codeLines[i] = null;
        }
    }

}
