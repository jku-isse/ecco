package at.jku.isse.ecco.adapter.rust.extractor;

import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

public class ConfigurationPredicateVisitor extends RustParserBaseVisitor<Formula> {
    private final FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();

    @Override
    public Formula visitCfgAttribute(RustParser.CfgAttributeContext ctx) {
        return visit(ctx.configurationPredicate());
    }

    @Override
    public Formula visitConfigurationPredicate(RustParser.ConfigurationPredicateContext ctx) {
        if (ctx.configurationOption() != null) {
            return visit(ctx.configurationOption());
        } else if (ctx.configurationAll() != null) {
            return visit(ctx.configurationAll());
        } else if (ctx.configurationAny() != null) {
            return visit(ctx.configurationAny());
        } else if (ctx.configurationNot() != null) {
            return visit(ctx.configurationNot());
        }
        return f.falsum(); // Default to false if no predicate is matched
    }

    @Override
    public Formula visitConfigurationOption(RustParser.ConfigurationOptionContext ctx) {
        String featureName = ctx.identifier().getText();
        String value = null;

        if (ctx.EQ() != null) {
            // Handle feature = "value" case
            value = ctx.STRING_LITERAL() != null ?
                    ctx.STRING_LITERAL().getText() :
                    ctx.RAW_STRING_LITERAL().getText();
            // Remove quotes
            value = value.substring(1, value.length() - 1);
            return f.variable(featureName + "=" + value);
        } else {
            // Handle simple feature case
            return f.variable(featureName);
        }
    }

    @Override
    public Formula visitConfigurationAll(RustParser.ConfigurationAllContext ctx) {
        if (ctx.configurationPredicateList() == null) {
            return f.verum(); // Empty all() is true
        }
        Formula result = f.verum();

        for (RustParser.ConfigurationPredicateContext predCtx :
                ctx.configurationPredicateList().configurationPredicate()) {
            result = f.and(result, visit(predCtx));
        }

        return result;
    }

    @Override
    public Formula visitConfigurationAny(RustParser.ConfigurationAnyContext ctx) {
        if (ctx.configurationPredicateList() == null) {
            return f.falsum(); // Empty any() is false
        }
        Formula result = f.falsum();

        for (RustParser.ConfigurationPredicateContext predCtx :
                ctx.configurationPredicateList().configurationPredicate()) {
            result = f.or(result, visit(predCtx));
        }

        return result;
    }

    @Override
    public Formula visitConfigurationNot(RustParser.ConfigurationNotContext ctx) {
        return f.not(visit(ctx.configurationPredicate()));
    }
}
