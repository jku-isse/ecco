package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminalFactory;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.StructureNonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.main.GrammarSerializationService;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate.GrammarMutatorService;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate.GrammarMutatorServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.StructureInferenceService;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.StructureInferenceServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.Node;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.NonTerminalNode;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.Tokenizer;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Michael Jahn
 */
public class GrammarInferenceFacadeImpl implements GrammarInferenceFacade {

    private static final String ROOT_LABEL = "rootContent";


    private final GrammarMutatorService grammarMutatorService;
    private final StructureInferenceService structureInferenceService;
    private final SampleReaderService sampleReaderService;
    private final Tokenizer tokenizer;
    private final GrammarOptimizationService grammarOptimizationService;
    private final GrammarSerializationService grammarSerializationService;

    public GrammarInferenceFacadeImpl() {
        this.grammarMutatorService = new GrammarMutatorServiceImpl();
        this.structureInferenceService = new StructureInferenceServiceImpl();
        this.sampleReaderService = new SampleReaderService();
        this.tokenizer = new Tokenizer();
        grammarOptimizationService = new GrammarOptimizationService();
        grammarSerializationService = new GrammarSerializationService();
    }

    @Override
    public NonTerminal inferGrammar(List<String> filePaths, EccoModelBuilderStrategy strategy, String grammarDataOutputPath) throws IOException, AmbiguousTokenDefinitionsException {

        NonTerminalFactory.resetAllNonTerminals();

        List<NonTerminalNode> baseStructures = new ArrayList<>();
        Map<String, NonTerminal> nonTerminalMap = new HashMap<>();
        Map<String, Set<String>>  parsedSamples = new HashMap<>();

        NonTerminal rootSymbol = inferGrammar(filePaths, strategy, baseStructures, nonTerminalMap, parsedSamples);

        if(grammarDataOutputPath != null && !grammarDataOutputPath.isEmpty()) {
            String baseStructureString = grammarSerializationService.serializeBaseStructures(baseStructures);
            String nonTerminalMapString = grammarSerializationService.serializeNonTerminalMap(nonTerminalMap);
            String parsedSamplesString = grammarSerializationService.serializeParsedSamples(parsedSamples);

            try {
                Files.write(new File(grammarDataOutputPath).toPath(), (baseStructureString + "\n" + nonTerminalMapString + "\n" + parsedSamplesString).getBytes());
            } catch (Throwable e) {
                System.err.println("ERROR: could not write to provided grammar output file: " + grammarDataOutputPath);
                e.printStackTrace();
            }
            System.err.println("Grammar Data file successfully writen to: " + grammarDataOutputPath.toString());
        }

        return rootSymbol;
    }


    @Override
    public NonTerminal updateGrammar(List<String> filePaths, EccoModelBuilderStrategy strategy, String grammarDataFilePath) throws IOException, AmbiguousTokenDefinitionsException {

        File grammarDataFile = new File(grammarDataFilePath);
        List<String> lines = java.nio.file.Files.readAllLines(grammarDataFile.toPath());
        if(lines.size() != 3) {
            System.err.println("ERROR! The file: " + grammarDataFilePath + " is not a valid grammar data file!");
            return null;
        }

        List<NonTerminalNode> origBaseStructures;
        Map<String, NonTerminal> origNonTerminalContentsPerLabel;
        Map<String, Set<String>>  origParsedSamples;
        try {
            origBaseStructures = grammarSerializationService.deserializeBaseStructures(lines.get(0));
            origNonTerminalContentsPerLabel = grammarSerializationService.deserializeNonTerminalMap(lines.get(1));
            origParsedSamples = grammarSerializationService.deserializeParsedSamples(lines.get(2));
        } catch (Throwable e) {
            System.err.println("ERROR! The file: " + grammarDataFilePath + " is not a valid grammar data file!");
            e.printStackTrace();
            return null;
        }
        NonTerminal rootSymbol = inferGrammar(filePaths, strategy, origBaseStructures, origNonTerminalContentsPerLabel, origParsedSamples);

        String baseStructureString = grammarSerializationService.serializeBaseStructures(origBaseStructures);
        String nonTerminalMapString = grammarSerializationService.serializeNonTerminalMap(origNonTerminalContentsPerLabel);
        String parsedSamplesString = grammarSerializationService.serializeParsedSamples(origParsedSamples);

        try {
            Files.write(new File(grammarDataFilePath).toPath(), (baseStructureString + "\n" + nonTerminalMapString + "\n" + parsedSamplesString).getBytes());
        } catch (Throwable e) {
            System.err.println("ERROR: could not write updated grammar to provided output file: " + grammarDataFilePath);
            e.printStackTrace();
        }

        return rootSymbol;
    }


    /**
     * Infers a grammar based on the input files, if origBaseStructures and origNonTerminalContentsPerLabel are not null, they will be used
     * to generate an updated grammar, and their contents will be updated
     *
     * @param filePaths
     * @param strategy
     * @param origBaseStructures
     * @param origNonTerminalContentsPerLabel
     * @param origParsedSamples
     * @return
     * @throws IOException
     * @throws AmbiguousTokenDefinitionsException
     */
    private NonTerminal inferGrammar(List<String> filePaths, EccoModelBuilderStrategy strategy, List<NonTerminalNode> origBaseStructures,
                                     Map<String, NonTerminal> origNonTerminalContentsPerLabel, Map<String, Set<String>> origParsedSamples) throws IOException, AmbiguousTokenDefinitionsException {

        NonTerminalFactory.resetAllNonTerminals();

        tokenizer.setTokenDefinitions(strategy.getTokenDefinitions());

        // graph grammar

        List<NonTerminalNode> rootNodes;
        if(origBaseStructures != null) {
            rootNodes = origBaseStructures;
        } else {
            rootNodes = new ArrayList<>();
        }

        rootNodes.addAll(structureInferenceService.inferFileStructures(filePaths, strategy.getBlockDefinitions()));

        Node rootGraphNode = structureInferenceService.inferBaseStructure(rootNodes, strategy.getBlockDefinitions());
        NonTerminal rootGraphSymbol = structureInferenceService.inferGraphGrammar(rootGraphNode, strategy.getBlockDefinitions());

        Map<String, Set<String>> alreadyProcessedSamplesPerLabel;
        if(origParsedSamples != null) {
            alreadyProcessedSamplesPerLabel = origParsedSamples;
        } else {
            alreadyProcessedSamplesPerLabel = new HashMap<>();
        }

        Map<String, NonTerminal> nonTerminalContentsPerLabel;
        if(origNonTerminalContentsPerLabel != null) {
            nonTerminalContentsPerLabel = origNonTerminalContentsPerLabel;
        } else {
            nonTerminalContentsPerLabel = new HashMap<>();
        }

        // run grammar mutator on every label for every file
        for (String filePath : filePaths) {
            if(ParameterSettings.INFO_OUTPUT) {
                System.out.println("-------------------------------------------------");
                System.out.println(filePath.substring(filePath.lastIndexOf("\\") + 1));
                System.out.println("-------------------------------------------------");
            }
            NonTerminalNode rootNode = structureInferenceService.parseBaseStructure(filePath, strategy.getBlockDefinitions());

            if (!rootNode.getContent().isEmpty()) {
                NonTerminal rootSymbol = runGrammarMutation(strategy.getTokenDefinitions(), strategy.getSampleSeparator(), strategy.samplesStopOnLineBreak(), strategy.getCommentBlockDefinitions(),
                        alreadyProcessedSamplesPerLabel, nonTerminalContentsPerLabel, ROOT_LABEL, rootNode);
                if(rootSymbol == null) {
                    System.err.println("ERROR: Grammar Mutation failed!");
                    return null;
                } else {
                    nonTerminalContentsPerLabel.put(ROOT_LABEL.toUpperCase(), rootSymbol);
                }
            }
            for (String label : rootGraphNode.getAllLabelsRecurisve()) {
                for (Node labelNode : rootNode.getAllChildNodesPerLabelRecursive(label)) {
                    if (labelNode.hasContent()) {
                        NonTerminal rootSymbol = runGrammarMutation(strategy.getTokenDefinitions(), strategy.getSampleSeparator(), strategy.samplesStopOnLineBreak(), strategy.getCommentBlockDefinitions(),
                                alreadyProcessedSamplesPerLabel, nonTerminalContentsPerLabel, label, labelNode);
                        if(rootSymbol == null) {
                            System.err.println("ERROR: Grammar Mutation failed for: " + label.toUpperCase() + "!");
//                            return null;
                        } else {
                            nonTerminalContentsPerLabel.put(label.toUpperCase(), rootSymbol);
                        }
                    }
                }
            }
        }

        // add recursive rule for mutated nonTerminals
        /*for (Map.Entry<String, NonTerminal> nonTerminalEntry : nonTerminalContentsPerLabel.entrySet()) {
            if(nonTerminalEntry.getValue().getRules().size() == 1
                    && nonTerminalEntry.getValue().getRules().get(0).getSymbols().size() == 1) {
                nonTerminalEntry.getValue().getRules().get(0).appendSymbol(nonTerminalEntry.getValue());
                nonTerminalEntry.getValue().addRule(new Rule(new ArrayList<>()));
            } else {
                NonTerminal recursiveNonTerminal = NonTerminalFactory.createNewNonTerminal(new Rule(new ArrayList<>()));
                recursiveNonTerminal.addRule(new Rule(Arrays.asList(nonTerminalEntry.getValue(), recursiveNonTerminal)));
                nonTerminalEntry.setValue(recursiveNonTerminal);
            }
        }*/


        // run grammar optimizations
        for (Map.Entry<String, NonTerminal> entry : nonTerminalContentsPerLabel.entrySet()) {
            grammarOptimizationService.optimizeGrammar(entry.getValue());
        }

        // combine graph grammar and results from grammar mutator
        for (NonTerminal graphNonTerminal : rootGraphSymbol.getAllNonTerminalsRecursive()) {
            if (graphNonTerminal.isStructureSymbol()) {
                StructureNonTerminal structureNonTerminal = (StructureNonTerminal) graphNonTerminal;
                if (structureNonTerminal.containsLabel() && nonTerminalContentsPerLabel.containsKey(structureNonTerminal.getLabel())) {
                    NonTerminal contentNonTerminal = NonTerminalFactory.createNewRecursionNonTerminal(nonTerminalContentsPerLabel.get(structureNonTerminal.getLabel()));
                    NonTerminal contentWrapperNonTerminal = NonTerminalFactory.createBlockContentStructureNonTerminal();
                    contentWrapperNonTerminal.addRule(new Rule(Arrays.asList(contentNonTerminal)));

                    structureNonTerminal.getLabelRule().insertSymbols(1, Arrays.asList(contentWrapperNonTerminal));
                    structureNonTerminal.getLabelRule().insertSymbols(structureNonTerminal.getLabelRule().getSymbols().size()-1, Arrays.asList(contentWrapperNonTerminal));
                }
            }
        }
        if(nonTerminalContentsPerLabel.containsKey(ROOT_LABEL.toUpperCase())) {
            rootGraphSymbol.getRules().get(0).insertSymbols(0, Arrays.asList(NonTerminalFactory.createNewRecursionNonTerminal(nonTerminalContentsPerLabel.get(ROOT_LABEL.toUpperCase()))));
        }

        System.err.println("INFORMATION: successfully inferred grammar");
        return rootGraphSymbol;
    }

    private NonTerminal runGrammarMutation(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, boolean sampleStopOnLineBreak, List<String> commentBlocks, Map<String, Set<String>> alreadyProcessedSamplesPerLabel, Map<String, NonTerminal> nonTerminalContentsPerLabel, String label, Node labelNode) throws AmbiguousTokenDefinitionsException {
        int origNonTerminalSize;
        origNonTerminalSize = nonTerminalContentsPerLabel.containsKey(label.toUpperCase()) ? nonTerminalContentsPerLabel.get(label.toUpperCase()).getAllNonTerminals().size() : 0;
        if(ParameterSettings.INFO_OUTPUT)
            System.out.println("Running grammar mutator for: " + label.toUpperCase());
        List<String> samples = sampleReaderService.readSamplesFromString(labelNode.getContent(), sampleSeparator, commentBlocks, sampleStopOnLineBreak);

        // clean samples from empty lines
        samples = samples.stream().filter(sample -> !StringUtils.isWhitespace(sample)).collect(Collectors.toList());

        // run grammar mutation
        NonTerminal rootSymbol = null;
        if (samples.size() > 0) {
            rootSymbol = grammarMutatorService.inferGrammarFromString(tokenDefinitions, sampleSeparator, samples,
                    alreadyProcessedSamplesPerLabel.get(label), nonTerminalContentsPerLabel.get(label.toUpperCase()));

            if(rootSymbol != null) {
                int newNonTerminalSize = rootSymbol.getAllNonTerminals().size();
                if(ParameterSettings.INFO_OUTPUT)
                    System.out.println("---- RESULT: " + newNonTerminalSize + " NonTerminals (" + (newNonTerminalSize - origNonTerminalSize) + " new)");
            }

            if (alreadyProcessedSamplesPerLabel.containsKey(label)) {
                alreadyProcessedSamplesPerLabel.get(label).addAll(samples);
            } else {
                alreadyProcessedSamplesPerLabel.put(label, new HashSet<>(samples));
            }
        }
        return rootSymbol;
    }
}
