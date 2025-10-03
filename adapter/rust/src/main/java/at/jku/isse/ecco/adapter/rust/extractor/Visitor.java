package at.jku.isse.ecco.adapter.rust.extractor;

import at.jku.isse.ecco.adapter.rust.antlr.RustParser;
import at.jku.isse.ecco.adapter.rust.antlr.RustParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Visitor extends RustParserBaseVisitor<String> {
    private List<Feature> features = new ArrayList<>();
    private final String[] codeLines;

    public Visitor(String[] codeLines) {
        this.codeLines = codeLines;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public String visit(ParseTree tree) {
        return tree.accept(this);
    }


    @Override
    public String visitItem(RustParser.ItemContext ctx) {
        return super.visitItem(ctx);
    }

    @Override
    public String visitOuterAttribute(RustParser.OuterAttributeContext ctx) {
        String attr = ctx.attr().getText();
        if (Objects.equals(ctx.attr().simplePath().getText(), "cfg")) {
            String featureName = attr.substring(attr.indexOf('(') + 1, attr.lastIndexOf(')'));
            List<Integer> codeLines = getCodeLines(ctx.getParent());
            features.add(new Feature(featureName, codeLines));
        }
        return super.visitOuterAttribute(ctx);
    }

    private List<Integer> getCodeLines(ParserRuleContext ctx) {
        int startLine = ctx.start.getLine();
        int stopLine = ctx.stop.getLine();

        List<Integer> codeLines = new ArrayList<>();
        for (int i = startLine; i <= stopLine; i++) {
            codeLines.add(i);
        }
        return codeLines;
    }

}
