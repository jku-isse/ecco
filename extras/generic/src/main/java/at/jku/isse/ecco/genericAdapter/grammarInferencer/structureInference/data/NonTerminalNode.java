package at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Michael Jahn
 */
public class NonTerminalNode extends Node {

    private BlockDefinition blockDefinition;

    public void setBlockDefinition(BlockDefinition blockDefinition) {
        this.blockDefinition = blockDefinition;
    }

    public void setIdentifiers(List<String> identifiers) {
        this.identifiers = identifiers;
    }

    private List<String> identifiers;
    private int maxOccurences = 1;




    public NonTerminalNode(String label, BlockDefinition blockDefinition) {
        super(label);
        this.blockDefinition = blockDefinition;
        this.identifiers = new ArrayList<>();
    }

    public NonTerminalNode(String label, BlockDefinition blockDefinition, List<String> identifiers) {
        super(label);
        this.blockDefinition = blockDefinition;
        this.identifiers = identifiers;
    }

    @Override
    public boolean isTerminalNode() {
        return false;
    }


    public List<String> getIdentifiers() {
        return identifiers;
    }

    public BlockDefinition getBlockDefinition() {
        return blockDefinition;
    }

    @Override
    protected String subTreeToString(String indention, Set<String> printedLabels) {
        StringBuilder childrenString = new StringBuilder();
        if(!printedLabels.contains(getLabel())) {
            printedLabels.add(getLabel());
            for (ChildRelation childRelation : getChildren()) {
                childrenString.append(childRelation.subTreeToString(indention + "  ", new HashSet<>()));
            }

        } else {
            for (ChildRelation childRelation : getChildren()) {
                childrenString.append(indention + "   -" + childRelation.getLabel() + "\n");
            }
        }
        return indention + "- " + getLabel() +
                (identifiers.size() > 0 ? " (\"" + identifiers.get(0) + "\")" : "") +
                (maxOccurences > 1 ? "(max occurences: " + maxOccurences + ")" : "") +
                "\n" + childrenString;
    }

    public int getMaxOccurences() {
        return maxOccurences;
    }

    public void setMaxOccurences(int maxOccurences) {
        this.maxOccurences = maxOccurences;
    }


}
