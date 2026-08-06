package at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * reads from the given inputStream and returns the nextToken as {@link String}
 * of EOF if the end of the stream is reached
 *
 * @author Michael Jahn
 */
public class Tokenizer {

    private final static int EOF = -1;
    private final static String ALPHANUMERIC_REGEX = "(?=\\W)|(?<=\\W)";
    final static TokenDefinition KEYWORD_TOKEN = new TokenDefinition("KEYWORD_TOKEN", "", 1);

    // currently used token definitions
    private Collection<TokenDefinition> tokenDefinitions;

    // token values of the last tokenized input string
    private List<TokenValue> tokenValues;

    public Tokenizer() {
        tokenDefinitions = new ArrayList<>();
        tokenValues = new ArrayList<>();
    }

    public Tokenizer(Collection<TokenDefinition> tokenDefinitions) {
        this.tokenDefinitions = tokenDefinitions;
        tokenValues = new ArrayList<>();
    }

    public void addTokenDescription(String name, String regex, int priority) {
        tokenDefinitions.add(new TokenDefinition(name, regex, priority));
    }

    public void addTokenDescriptions(List<TokenDefinition> tokenDefinitions) {
        this.tokenDefinitions.addAll(tokenDefinitions);
    }

    public void setTokenDefinitions(List<TokenDefinition> tokenDefinitions) {
        this.tokenDefinitions = tokenDefinitions;
    }

    public List<String> tokenize(String inputString) throws AmbiguousTokenDefinitionsException {
        boolean printLog = false;
        if(inputString.length() > 500) {
            System.err.print("Started tokenization of string");
            printLog = true;
        }
        StringBuilder curString = new StringBuilder(inputString);
        List<String> tokenStringList = new ArrayList<>();

        tokenValues = new ArrayList<>();

        while (!curString.toString().equals("")) {


            Map<Integer, TokenDefinition> foundTokenMap = new HashMap<>();
            // find first fitting tokenDefinition
            for (TokenDefinition tokenDefinition : tokenDefinitions) {
                Matcher matcher = tokenDefinition.getRegex().matcher(curString);
                if (matcher.find()) {
                    if (foundTokenMap.containsKey(matcher.start())) {
                        if (foundTokenMap.get(matcher.start()).getPriority() < tokenDefinition.getPriority()) {
                            foundTokenMap.put(matcher.start(), tokenDefinition);
                        } else if (foundTokenMap.get(matcher.start()).getPriority() == tokenDefinition.getPriority()) {
                            throw new AmbiguousTokenDefinitionsException(foundTokenMap.get(matcher.start()), tokenDefinition);
                        }
                    } else {
                        foundTokenMap.put(matcher.start(), tokenDefinition);
                    }
                }
            }
            if (foundTokenMap.size() >= 1) {
                int minIdx = Collections.min(foundTokenMap.keySet());
                TokenDefinition minTokenDefinition = foundTokenMap.get(minIdx);

                Matcher matcher = minTokenDefinition.getRegex().matcher(curString);
                if (matcher.find()) {
                    String tokenVal = matcher.group().trim();
                    String nonTokenVal = curString.substring(0, curString.indexOf(tokenVal));

                    // Match alphanumeric values in the non token string
                    if (nonTokenVal.length() > 0) {
                        List<String> tmp = Arrays.asList(nonTokenVal.split(ALPHANUMERIC_REGEX));

                        tokenValues.addAll(tmp.stream().map(s -> new TokenValue(new TokenDefinition(s, 0), s, true)).collect(Collectors.toList()));
                        tokenStringList.addAll(tmp);
                    }
                    tokenValues.add(new TokenValue(minTokenDefinition, tokenVal, false));
                    tokenStringList.add(minTokenDefinition.getName());

                    curString.delete(0, curString.indexOf(tokenVal) + tokenVal.length());
                }
            } else {
                List<String> tmp = Arrays.asList(curString.toString().split(ALPHANUMERIC_REGEX));

                for (String s : tmp) {
                    tokenValues.add(new TokenValue(new TokenDefinition(s, 0), s, true));
                    curString.delete(0, s.length());
                }
                tokenStringList.addAll(tmp);
            }
        }
        if(printLog) {
            System.err.println("... finished.");
        }
        return tokenStringList;
    }

    public String tokenizeToString(String inputString) throws AmbiguousTokenDefinitionsException {
        List<String> list = tokenize(inputString);
        StringBuilder stringBuilder = new StringBuilder();
        list.forEach(stringBuilder::append);
        return stringBuilder.toString();
    }

    public List<TokenValue> tokenizeToTokenValueList(String inputString) throws AmbiguousTokenDefinitionsException {
        tokenize(inputString);
        return tokenValues;
    }

    public List<TokenValue> getTokenValues() {
        return tokenValues;
    }


}
