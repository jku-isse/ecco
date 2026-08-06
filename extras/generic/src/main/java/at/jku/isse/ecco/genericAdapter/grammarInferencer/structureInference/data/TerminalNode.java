package at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;

/**
 * @author Michael Jahn
 */
public class TerminalNode extends Node {

    private final TokenDefinition tokenDefinition;

    public TerminalNode(TokenDefinition tokenDefinition) {
        super(tokenDefinition.getName());
        this.tokenDefinition = tokenDefinition;
    }


    @Override
    public boolean isTerminalNode() {
        return true;
    }

    @Override
    public String subTreeToString() {
        return null;
    }

}
