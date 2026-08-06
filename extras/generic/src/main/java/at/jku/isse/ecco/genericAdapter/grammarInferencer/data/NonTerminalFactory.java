package at.jku.isse.ecco.genericAdapter.grammarInferencer.data;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Michael Jahn
 */
public class NonTerminalFactory {

    public static Map<String, NonTerminal> existingNonTerminals = new HashMap<>();

    private static String snapshotRootNonTerminal;
    public static Map<String, NonTerminal> snapshotNonTerminals;

    public static NonTerminal getNonTerminalWithName(String name) {
        if(existingNonTerminals.containsKey(name)) {
            return existingNonTerminals.get(name);
        } else {
            NonTerminal nonTerminal= new NonTerminal(name);
            existingNonTerminals.put(name, nonTerminal);
            return nonTerminal;
        }
    }

    public static NonTerminal getNonTerminalWithName(String name, Rule rule) {
        NonTerminal nonTerminal = getNonTerminalWithName(name);
        nonTerminal.addRule(rule);
        return nonTerminal;
    }

    public static NonTerminal createNewNonTerminal() {
        NonTerminal nonTerminal= new NonTerminal();
        existingNonTerminals.put(nonTerminal.getName(), nonTerminal);
        return nonTerminal;
    }

    public static NonTerminal createNewNonTerminal(Rule rule) {
        NonTerminal nonTerminal = createNewNonTerminal();
        nonTerminal.addRule(rule);
        return nonTerminal;
    }

    public static StructureNonTerminal createNewStructureNonTerminal() {
        StructureNonTerminal nonTerminal= new StructureNonTerminal();
        existingNonTerminals.put(nonTerminal.getName(), nonTerminal);
        return nonTerminal;
    }

    public static StructureNonTerminal createBlockContentStructureNonTerminal() {
        StructureNonTerminal nonTerminal = new StructureNonTerminal(NonTerminal.BLOCK_CONTENT_STRUCTURE_SYMBOL_NAME_PREFIX);
        existingNonTerminals.put(nonTerminal.getName(), nonTerminal);
        return nonTerminal;
    }

    public static StructureNonTerminal createNewStructureNonTerminal(BlockDefinition blockDefinition) {
        StructureNonTerminal nonTerminal= new StructureNonTerminal(blockDefinition);
        existingNonTerminals.put(nonTerminal.getName(), nonTerminal);
        return nonTerminal;
    }

    public static void resetAllNonTerminals() {
        UniqueIdGenerator.resetId();
        existingNonTerminals = new HashMap<>();
    }

    /**
     * Creates and saves a snapshot of all created NonTerminals
     * Additionally stores the name of the root symbol
     *
     * @param rootNonTerminal
     */
    public static void saveGrammarSnapshot(NonTerminal rootNonTerminal) {
        NonTerminalFactory.snapshotRootNonTerminal = rootNonTerminal.getName();
        snapshotNonTerminals = new HashMap<>();


        List<NonTerminal> nonTerminals = rootNonTerminal.getAllNonTerminals();

        for (NonTerminal nonTerminal : nonTerminals) {
            snapshotNonTerminals.put(nonTerminal.getName(), (NonTerminal) nonTerminal.getDeepCopyForSnapshot());
        }

        /*for (Map.Entry<String, NonTerminal> entry : existingNonTerminals.entrySet()) {
            snapshotNonTerminals.put(entry.getKey(), (NonTerminal) entry.getValue().getDeepCopyForSnapshot());
        }*/

    }

    public static void clearSnapshotGrammar() {
        snapshotNonTerminals = new HashMap<>();
    }

    /**
     * Applies the latest snapshot to the current NonTerminal set and returns the root symbol
     *
     * @return
     */
    public static NonTerminal applyCurrentGrammarSnapshot() {

        existingNonTerminals = snapshotNonTerminals;
        snapshotNonTerminals = new HashMap<>();

        return existingNonTerminals.get(NonTerminalFactory.snapshotRootNonTerminal);
    }

    public static NonTerminal getSnapshotNonTerminal(String name) {
        if(snapshotNonTerminals.containsKey(name)) {
            return snapshotNonTerminals.get(name);
        } else {
            NonTerminal nonTerminal = new NonTerminal(name);
            snapshotNonTerminals.put(name, nonTerminal);
            return nonTerminal;
        }
    }

    public static NonTerminal createNewRecursionNonTerminal(NonTerminal nonTerminal) {
        NonTerminal structuralNonTerminal = createNewStructureNonTerminal();
        structuralNonTerminal.setRecursionNonTerminal(true);
        List<Symbol> symbols = new ArrayList<>();
        symbols.add(nonTerminal);
        structuralNonTerminal.addRule(new Rule(symbols));
        return structuralNonTerminal;
    }
}
