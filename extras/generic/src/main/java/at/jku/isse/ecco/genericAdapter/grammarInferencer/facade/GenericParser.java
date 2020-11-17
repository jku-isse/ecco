package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate.GenericInternalParser;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.Tokenizer;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author Michael Jahn
 */
public class GenericParser implements IGenericParser {


    private final Tokenizer tokenizer;
    private final GenericInternalParser genericInternalParser;

    public GenericParser(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.genericInternalParser = new GenericInternalParser();
    }


    @Override
    public List<Rule> parseSample(NonTerminal rootSymbol, String sample) {

        List<String> tokenizedSample;
        try {
            tokenizedSample = tokenizer.tokenize(sample);
            tokenizedSample = tokenizedSample.stream().filter(s -> !StringUtils.isWhitespace(s)).collect(toList());
        } catch (AmbiguousTokenDefinitionsException e) {
            e.printStackTrace();
            return null;
        }

        return genericInternalParser.parseTokenizedSample(rootSymbol, tokenizedSample);

    }

    @Override
    public List<TokenValue> getTokenValueList(NonTerminal rootSymbol, String sample) {

        try {
            tokenizer.tokenize(sample);
        } catch (AmbiguousTokenDefinitionsException e) {
            e.printStackTrace();
            return null;
        }

        return tokenizer.getTokenValues();
    }

    /**
     * uses the given grammar to parse the file
     *
     * @param rootSymbol
     * @param filePath
     * @return true, if the file could be parsed successfully, false otherwise
     */
    @Override
    public boolean parseFile(NonTerminal rootSymbol, String filePath) throws IOException, AmbiguousTokenDefinitionsException {

        StringBuilder fileContent = new StringBuilder();

        BufferedReader br = new BufferedReader(new FileReader(new File(filePath)));
        String line;
        while ((line = br.readLine()) != null) {
            fileContent.append(line);
        }

        List<String> tokenizedSample;
        tokenizedSample = tokenizer.tokenize(fileContent.toString());

        return genericInternalParser.parseTokenizedSample(rootSymbol, tokenizedSample) != null;
    }
}
