package at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.*;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.diff.Diff;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.diff.DiffUtils;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.diff.SampleDiff;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.SampleReaderService;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.Statistics;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.parserGenerator.AntlrParserWrapperServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.ruleInductionSequitur.Sequitur;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.Tokenizer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;

/**
 * @author Michael Jahn
 */
public class GrammarMutatorServiceImpl implements GrammarMutatorService {

    private final GrammarMutator grammarMutator;
    private final Tokenizer tokenizer;
    private final Sequitur sequitur;
    private final SampleDiff sampleDiff;
    private final DiffOptimization diffOptimization;
    private final GenericInternalParser genericInternalParser;
    private final SampleReaderService sampleReaderService;

    public GrammarMutatorServiceImpl() {
        grammarMutator = new GrammarMutator();
        tokenizer = new Tokenizer();
        sequitur = new Sequitur();
        sampleDiff = new SampleDiff();
        diffOptimization = new DiffOptimization();
        genericInternalParser = new GenericInternalParser();
        sampleReaderService = new SampleReaderService();

        NonTerminalFactory.resetAllNonTerminals();
    }

    @Override
    public NonTerminal inferGrammar(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, String filePath, boolean sampleStopOnLineBreak) throws IOException, AmbiguousTokenDefinitionsException {

        List<String> samples = sampleReaderService.readSamplesFromFile(filePath, sampleSeparator, sampleStopOnLineBreak);

        return inferGrammar(tokenDefinitions, sampleSeparator, samples, 0);
    }

    @Override
    public NonTerminal inferGrammarFromString(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, String inputString, boolean sampleStopOnLineBreak) throws IOException, AmbiguousTokenDefinitionsException {

        List<String> samples = sampleReaderService.readSamplesFromString(inputString, sampleSeparator, new ArrayList<>(), false);

        return inferGrammar(tokenDefinitions, sampleSeparator, samples, 0);
    }

    @Override
    public NonTerminal inferGrammar(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, List<String> samples, int rootSampleNr) throws AmbiguousTokenDefinitionsException {
        NonTerminalFactory.resetAllNonTerminals();

        return inferGrammarFromString(tokenDefinitions, sampleSeparator, samples, new HashSet<>(), null);

    }

    /**
     * Infers a grammar from the given samples, using the given rootSymbol as initial grammar for the mutation algorithm
     * the alreadyAcceptedSamples are option, but will highly increase the accuracy and performance of the algorithm
     *
     * @param tokenDefinitions
     * @param sampleSeparator
     * @param samples
     * @param alreadyAcceptedSamples
     * @param rootSymbol
     * @return
     */
    @Override
    public NonTerminal inferGrammarFromString(List<TokenDefinition> tokenDefinitions, List<String> sampleSeparator, List<String> samples, Set<String> alreadyAcceptedSamples, NonTerminal rootSymbol) throws AmbiguousTokenDefinitionsException {

        if(samples.size() <= 0 ) {
            return rootSymbol;
        }

        tokenizer.setTokenDefinitions(tokenDefinitions);
        int alreadyProcessed = 0;
        int newSamples = 0;

        HashSet<List<String>> tokenizedSampleSet = new HashSet<>();
        List<List<TokenValue>> sampleTokenList = new ArrayList<>();

        // init grammar
        if(rootSymbol == null) {
            rootSymbol = generateInitialGrammar(samples.get(0));
            if(ParameterSettings.IGNORE_WHITESPACES) {
                tokenizedSampleSet.add(tokenizer.tokenize(samples.get(0)).stream().filter(s -> !StringUtils.isWhitespace(s)).collect(toList()));
            }
        }

        // add already processed samples
        if(alreadyAcceptedSamples != null) {
            for (String alreadyAcceptedSample : alreadyAcceptedSamples) {
                if(ParameterSettings.IGNORE_WHITESPACES) {
                    tokenizedSampleSet.add(tokenizer.tokenize(alreadyAcceptedSample).stream().filter(s -> !StringUtils.isWhitespace(s)).collect(toList()));
                }
            }
        }

        // sample mutation loop
        for (String sample : samples) {
            List<String> sampleTokenized = tokenizer.tokenize(sample);
            if (ParameterSettings.IGNORE_WHITESPACES) {
                sampleTokenized = sampleTokenized.stream().filter(s -> !StringUtils.isWhitespace(s)).collect(toList());
            }
            List<TokenValue> sampleTokenValues = tokenizer.getTokenValues();

            if(!tokenizedSampleSet.contains(sampleTokenized)) {
                newSamples++;
                // get sample from set with the longest common beginning characters
                List<String> closestTokenizedSample = null;
                int minLevenshtein;
                int maxBeginningCharacters = 0;
                Set<List<String>> equalCloseTokenizedSamples = new HashSet<>();
                for (List<String> processedSample : tokenizedSampleSet) {
                    int curCommonChars = sampleDiff.computeCommongBeginningCharacters(processedSample, sampleTokenized);
                    if (closestTokenizedSample == null || curCommonChars > maxBeginningCharacters) {
                        maxBeginningCharacters = curCommonChars;
                        closestTokenizedSample = processedSample;
                        equalCloseTokenizedSamples.clear();
                    } else if (curCommonChars == maxBeginningCharacters) {
                        equalCloseTokenizedSamples.add(closestTokenizedSample);
                        equalCloseTokenizedSamples.add(processedSample);
                    }
                }

                // if some share the same longest common beginning characters, use the one with the shortest levenshtein distance
                if (equalCloseTokenizedSamples.size() > 1) {
                    minLevenshtein = -1;
                    for (List<String> processedSample : equalCloseTokenizedSamples) {
                        int curLevenshtein = sampleDiff.computeLevenshteinDistance(processedSample, sampleTokenized, false);
                        if (closestTokenizedSample == null || minLevenshtein == -1 || curLevenshtein < minLevenshtein) {
                            minLevenshtein = curLevenshtein;
                            closestTokenizedSample = processedSample;
                        }
                    }
                } else {
                    minLevenshtein = sampleDiff.computeLevenshteinDistance(closestTokenizedSample, sampleTokenized, false);
                }

                if (minLevenshtein > 0) {

                    if (ParameterSettings.DEBUG_OUTPUT) {
                        System.out.println("----------------------------------------------------------------------");
                        System.out.println(sample);
                        System.out.println("----------------------------------------------------------------------");
                    }
                    List<Diff> sampleDiff = this.sampleDiff.diffSamplesList(closestTokenizedSample, sampleTokenized);

                    if (ParameterSettings.DEBUG_OUTPUT) {
                        StringBuilder closestTokenizedSampleString = new StringBuilder();
                        closestTokenizedSample.forEach(closestTokenizedSampleString::append);
                        System.out.println("Closest already processed sample is: " + closestTokenizedSampleString.toString() + "  (Distance: " + minLevenshtein + ")");
                        DiffUtils.printDiff(sampleDiff);
                        diffOptimization.optimizeDiffList(new ArrayList<>(sampleDiff));
                        System.out.println("Mutate grammar to be able to inferGrammar: " + sample);
                    }

                    // Use a fallback diff, if samples are too different and if the closestSample does not have the same beginning
                    if (minLevenshtein > ParameterSettings.MAX_DISTANCE_NORMAL_DIFF
                            && this.sampleDiff.computeLevenshteinDistance(sampleTokenized, closestTokenizedSample, true) > ParameterSettings.MAX_DISTANCE_NORMAL_DIFF
                            || ParameterSettings.DONT_MERGE_DIFFERENT_BEGININGS_IN_ROOT && !sampleDiff.get(0).getOperation().equals(Diff.Operation.EQUAL)) {
                        if (ParameterSettings.DEBUG_OUTPUT) {
                            System.out.println("Distance is higher than ParameterSettings.MAX_DISTANCE_NORMAL_DIFF (" + ParameterSettings.MAX_DISTANCE_NORMAL_DIFF + ") " +
                                    " So fallback diff will be used for mutation");
                        }
                        sampleDiff = diffOptimization.generateFallbackDiff(sampleDiff);
                        if (!grammarMutator.mutateFallbackGrammar(sampleDiff, rootSymbol, sampleTokenized, sampleSeparator)) {
                            if (ParameterSettings.DEBUG_OUTPUT)
                                System.out.println("MUTATION FAILED while using fallback diff");
                            return null;
                        }
                    } else {
                        rootSymbol = grammarMutator.mutateGrammar(sampleDiff, rootSymbol, sampleTokenized, sampleSeparator);

                        if (rootSymbol == null) {
                            if (ParameterSettings.DEBUG_OUTPUT)
                                System.out.println("Error during mutation occured: ");
                            return null;
                        }
                    }


                    if (ParameterSettings.DEBUG_OUTPUT) {
                        System.out.println("New grammar after mutation: " + rootSymbol.subTreeToString());
                    }
//                    System.out.println(new AntlrParserWrapperServiceImpl().convertToAntlrGrammar("", rootSymbol, true));

                    tokenizedSampleSet.add(sampleTokenized);

                    // Postprocessing step to optimize mutated grammar
                    // Generalization techniques ?
                    // TODO [OPTIMIZATION - PRIO] implement it!

                    sampleTokenList.add(sampleTokenValues);
                }
            } else {

                alreadyProcessed++;
            }
        }

        if(ParameterSettings.DEBUG_OUTPUT) {
            System.out.println("-----------------------------------------------------------------------------------");
            System.out.println("Final grammar defintion:\n");
            System.out.println(rootSymbol.subTreeToString());
            System.out.println(new AntlrParserWrapperServiceImpl().convertToAntlrGrammar("", rootSymbol, true));

            System.out.println("-----------------------------------------------------------------------------------");
            System.out.println("Parsed sample token values:\n");
            sampleTokenList.forEach((tList) -> {
                tList.forEach((t) -> System.out.print(" | " + t.getTokenDefinition().getName() + (t.isUndefinedToken() ? "" : " ( " + t.getValue() + " )")));
                System.out.println();
            });
        }

        if (ParameterSettings.PERFORM_FINAL_GRAMMAR_SANITY_CHECK) {
            // asssert here that all samples can be parsed by the parser
            for (List<String> tokenizedSample : tokenizedSampleSet) {
                if (!genericInternalParser.parsesTokenizedSample(rootSymbol, tokenizedSample)) {
                    Statistics.nrFailedSanityCheck++;
                    StringBuilder strBuilder = new StringBuilder();
                    tokenizedSample.forEach(strBuilder::append);
                    System.err.println("Assertion Error: sample: " + strBuilder.toString() + " cannnot be parsed by final grammar!!");
                    if(ParameterSettings.FAIL_ON_INVALID_GRAMMAR_SANITY_CHECK) {
                        return null;
                    }
                }
            }
        }
        if(ParameterSettings.STATISTICS_OUTPUT)
            System.out.println("Added " + newSamples + " new sample (already processed: " + alreadyProcessed + ")");
        return rootSymbol;


    }

    /**
     * Private Methods
     */
    private NonTerminal generateInitialGrammar(String initSample) throws AmbiguousTokenDefinitionsException {

        List<String> initSampleTokenized = tokenizer.tokenize(initSample);
        if (ParameterSettings.IGNORE_WHITESPACES)
            initSampleTokenized = initSampleTokenized.stream().filter(s -> !StringUtils.isWhitespace(s)).collect(toList());

        if (ParameterSettings.DEBUG_OUTPUT) {
            System.out.println("String after tokenization: ");
            for (String s : initSampleTokenized) {
                System.out.print(s + " | ");
            }
            System.out.println();
        }

        NonTerminal rootSymbol;

        if (ParameterSettings.USE_SEQUITUR_INITIAL_GRAMMAR) {
            rootSymbol = (NonTerminal) sequitur.runAlgorithm(initSampleTokenized);
        } else {
            List<Symbol> symbolList = initSampleTokenized.stream().map(s -> new Terminal(s, s)).collect(toList());
            Rule rootRule = new Rule(symbolList);
            rootSymbol = NonTerminalFactory.createNewNonTerminal(rootRule);
        }

        if (ParameterSettings.DEBUG_OUTPUT) {
            System.out.println("Inferred initial grammar: ");
            System.out.println(rootSymbol.subTreeToString());
        }

        return rootSymbol;
    }
}
