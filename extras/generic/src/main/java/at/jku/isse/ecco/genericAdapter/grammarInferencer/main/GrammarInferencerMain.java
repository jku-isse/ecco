package at.jku.isse.ecco.genericAdapter.grammarInferencer.main;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder.EccoModelBuilderImpl;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.JavaEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.StpEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacade;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacadeImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.parserGenerator.AntlrParserWrapperServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import org.antlr.v4.runtime.ParserInterpreter;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Main class for inferring grammar
 *
 * @author Michael Jahn
 */
public class GrammarInferencerMain {

    public static final String DEFAULT_GRAMMAR_PATH_ROOT = System.getProperty("java.io.tmpdir") + File.separator + "grammars";
    public static final String GRAMMAR_DATA_FILE_EXTIONS = "grDat";

    private static final List<EccoModelBuilderStrategy> strategies = Arrays.asList(new JavaEccoModelBuilderStrategy(),
            new StpEccoModelBuilderStrategy());

    public static void main(String[] args) throws IOException, AmbiguousTokenDefinitionsException {

        String outputPath = "";
        int fileStartIdx = 0;
        if(args.length < 2) {
            printUsageAndReturn();
        } else if(args[0].equals("-input")) {
            outputPath = DEFAULT_GRAMMAR_PATH_ROOT;
            fileStartIdx = 1;
            try {
                Files.createDirectory(new File(DEFAULT_GRAMMAR_PATH_ROOT).toPath());
            }catch (FileAlreadyExistsException ignore) {

            }
        } else if(args.length >= 4) {
            if(!args[0].equals("-output") || !args[2].equals("-input")) {
                printUsageAndReturn();
            }
            outputPath = args[1];
            fileStartIdx = 4;
        }

        // find strategy to use
        final int idx = fileStartIdx;
        Optional<EccoModelBuilderStrategy> optStrategy = strategies.stream().filter(st -> args[idx].endsWith(st.getFileExtension())).findFirst();
        if(!optStrategy.isPresent()) {
            System.err.println("No strategy found for file type: " + args[fileStartIdx].substring(args[fileStartIdx].lastIndexOf('.')));
            System.err.println("Supported file types: ");
            strategies.stream().map(EccoModelBuilderStrategy::getStrategyName).collect(Collectors.toList()).forEach(System.err::print);
            System.exit(1);
        }
        EccoModelBuilderStrategy strategy = optStrategy.get();

        List<String> filePaths = new ArrayList<>();
        for (int i = fileStartIdx; i < args.length; i++) {
            filePaths.add(args[i]);
        }

        GrammarInferenceFacade grammarInferenceFacade = new GrammarInferenceFacadeImpl();
        AntlrParserWrapperServiceImpl antlrParserService = new AntlrParserWrapperServiceImpl();

        NonTerminal rootSymbol = grammarInferenceFacade.inferGrammar(filePaths, strategy, getGrammarDataFilePath(outputPath, strategy));
        String outputFilePath = antlrParserService.writeAntlrGrammarToFile(new File(outputPath).toPath(), strategy.getStrategyName(), rootSymbol, true);

        if(outputFilePath != null) {
            System.out.println("----------------------------\nInferred Grammar g4 file successfully written to: " + outputFilePath);
            System.out.println("----------------------------\nInferred Grammar Data File successfully written to: " + getGrammarDataFilePath(outputPath, strategy));
        } else {
            System.err.println("An error occured while trying to write to the output path!");
        }

    }

    private static void printUsageAndReturn() {
        System.err.println("Usage: " + GrammarInferencerMain.class.getName() + " [-output <grammarFileOutputPath>] -input <filePath> ...");
        System.err.println("Available strategies: ");
        for (EccoModelBuilderStrategy strategy : strategies) {
            System.err.println("    -" + strategy.getStrategyName());
        }
        System.exit(1);
    }

    public static String getGrammarDataFilePath(String outputPath, EccoModelBuilderStrategy strategy) {
        return outputPath  + File.separator + strategy.getStrategyName() + "." + GRAMMAR_DATA_FILE_EXTIONS;
    }

}
