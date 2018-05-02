package at.jku.isse.ecco.genericAdapter.grammarInferencer.data;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;

/**
 * @author Michael Jahn
 */
public class StructureNonTerminal extends NonTerminal {

    private String label;
    private BlockDefinition blockDefinition;
    private Rule labelRule;

    public StructureNonTerminal(BlockDefinition blockDefinition) {
        super();
        this.label = blockDefinition.getName().toUpperCase();
        this.blockDefinition = blockDefinition;
    }

    public StructureNonTerminal(String prefix) {
        super();
        this.name = prefix + this.getId();
        this.label = "";
        this.blockDefinition = null;
    }

    public StructureNonTerminal(){
        super();
        this.label = "";
        this.blockDefinition = null;
    }

    @Override
    public boolean isStructureSymbol() {
        return true;
    }

    public boolean containsLabel() {
        return !label.isEmpty() && blockDefinition != null;
    }

    public String getLabel() {
        return label;
    }

    public BlockDefinition getBlockDefinition() {
        return blockDefinition;
    }

    public void setLabelRule(Rule labelRule) {
        this.addRule(labelRule);
        this.labelRule = labelRule;
    }

    public Rule getLabelRule() {
        return labelRule;
    }
}
