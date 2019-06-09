package at.jku.isse.ecco.genericAdapter.grammarInferencer.parserGenerator;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import org.antlr.v4.Tool;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.LexerGrammar;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper service that provides methods to easy generate and run an antlr parser on any input data
 *
 * @author Michael Jahn
 */
public class AntlrParserWrapperServiceImpl {
    public static final String GRAMMAR_FILE_EXTIONS = "g4";

    private static Logger LOGGER = Logger.getLogger(AntlrParserWrapperServiceImpl.class.getName());

    private static final String GRAMMAR_START = "grammar ";
    private static final String LINE_END = ";";
    private static final String IGNORE_WHITESPACE = "WS: [ \\n\\t\\r]+ -> skip;";


    /**
     * Converts the given grammar into an antlr representation, using {@link #convertToAntlrGrammar(String, NonTerminal, boolean)}
     * and writes the resulting string to the given {@link Path}
     *
     * @return the written filePath, if the grammar was successfully written, null otherwise
     */
    public String writeAntlrGrammarToFile(Path targetPath, String grammarName, NonTerminal rootSymbol, boolean ignoreWhitespaces) throws IOException {

        if(!targetPath.toFile().canWrite()) {
            System.err.println("Can not write to given path:" + targetPath.toFile().getPath());
            return null;
        }

        String antlrGrammar = convertToAntlrGrammar(grammarName, rootSymbol, ignoreWhitespaces);

        File targetFile = new File(targetPath.toFile(), grammarName + "." + GRAMMAR_FILE_EXTIONS);

        Files.write(targetFile.toPath(), antlrGrammar.getBytes("utf-8"));
        System.err.println("Antlr grammar file successfully written to: " + targetFile.toString());

        return targetFile.toString();
    }

    public ParseTree parseImplicitly(File grammarFile, ParserInterpreter parser) throws IOException {
        StringBuilder grammarText = new StringBuilder();
        List<String> lines = Files.readAllLines(grammarFile.toPath());
        lines.forEach(line -> grammarText.append(line + "\n"));

        String startRuleName = lines.get(1).substring(0, lines.get(1).indexOf(':'));

        return runGeneratedParser(parser, grammarText.toString(), startRuleName.trim());
    }

    public ParserInterpreter generateParserFromFile(File grammarFile, String testText) throws IOException {
        StringBuilder grammarText = new StringBuilder();
        List<String> lines = Files.readAllLines(grammarFile.toPath());
        lines.forEach(line -> grammarText.append(line + "\n"));

        return generateImplicitParser(grammarText.toString(), testText);
    }

    public String convertToAntlrGrammar(String grammarName, NonTerminal rootSymbol, boolean ignoreWhitespaces) {
        Set<NonTerminal> nonTerminals = rootSymbol.getAllNonTerminalsRecursive();

        StringBuilder antlrGrammar = new StringBuilder();

        antlrGrammar.append(GRAMMAR_START + grammarName + LINE_END + "\n");

        // to ensure that the rootSymbol is at the beginning of the string
        appendNonTerminal(antlrGrammar, rootSymbol);

        nonTerminals.remove(rootSymbol);

        for (NonTerminal nonTerminal : nonTerminals) {
            appendNonTerminal(antlrGrammar, nonTerminal);
        }

        if(ignoreWhitespaces) {
            antlrGrammar.append("\n" + IGNORE_WHITESPACE);
        }

        return antlrGrammar.toString();
    }

    private void appendNonTerminal(StringBuilder antlrGrammar, NonTerminal nonTerminal) {
        if(!nonTerminal.isRecursionNonTerminal()) {
            antlrGrammar.append(nonTerminal.getName().toLowerCase() + " : ");
            if(nonTerminal.isImplicitRecursionNonTerminal()) {
                antlrGrammar.append(nonTerminal.getImplicitRecursionAntlrRule());
            } else {
                List<Rule> rules = nonTerminal.getRules();
                for (int i = 0; i < rules.size(); i++) {
                    if (rules.get(i).getSymbols().size() > 0) {
                        if (i == 0) {
                            antlrGrammar.append(rules.get(i).toAntlrString());
                        } else {
                            antlrGrammar.append("\n| " + rules.get(i).toAntlrString());
                        }
                    }
                }
            }
            antlrGrammar.append(LINE_END + "\n");
        }
    }

    public void writeAntlrParser(String grammarName, NonTerminal rootSymbol, boolean ignoreWhitespaces) throws IllegalAccessException, InvocationTargetException, IOException, InstantiationException, NoSuchMethodException, ClassNotFoundException {
        writeAntlrParser(grammarName, convertToAntlrGrammar(grammarName, rootSymbol, ignoreWhitespaces));
    }

    public void writeAntlrParser(String grammarName, String grammarText) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, RecognitionException {

        // Write grammar file
        Writer out = new BufferedWriter(new FileWriter(new File(grammarName + ".g")));
        out.write(grammarText);
        out.close();

        Tool tool = new Tool();
        Grammar grammar = tool.createGrammar(tool.parseGrammar(grammarName + ".g"));
        grammar.fileName = grammarName + ".g";
        // Generate sources of lexer and parser
        try {
            tool.process(grammar, true);
        } catch (Throwable e) {
            e.printStackTrace();
            return;
        }
    }

    public Parser getWrittenParser(String grammarName, String input) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, IOException {

        // compile lexer and parser
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try {
            compiler.run(null, System.out, System.err, "-sourcepath", "", grammarName + "BaseListener.java", grammarName + "Listener.java", grammarName + "Lexer.java", grammarName + "Parser.java");
        } catch (Throwable e) {
            System.err.println("Lexer and/or Parser could not be found, most likely they were not crated by the antlr generator!: " + e.getMessage());
            return null;
        }

        // get lexer and parser from compiled classes
        Lexer lexer;
        Class<?> parserClass;
        try {
            lexer = (Lexer) Class.forName(grammarName + "Lexer").getConstructor(CharStream.class).newInstance(new ANTLRInputStream(input));
            parserClass = Class.forName(grammarName + "Parser");
        } catch (ClassNotFoundException e) {
            System.err.println("Lexer and/or Parser could not be compiled!: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        // run parser
        Constructor parserCTor = parserClass.getConstructor(TokenStream.class);
        Parser parser = (Parser) parserCTor.newInstance(new CommonTokenStream(lexer));

        return parser;
    }

    public Object runWrittenParser(Parser parser, String rootSymbol) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        Method rootParseMethod = parser.getClass().getMethod(rootSymbol.toLowerCase());
        Object returnObject = rootParseMethod.invoke(parser);

        return returnObject;
    }

    public ParseTree parseImplicitly(String grammarText, String startRuleName, String testText)  {
        ParserInterpreter parser = generateImplicitParser(grammarText, testText);
        return runGeneratedParser(parser, grammarText, startRuleName);
    }

    public ParserInterpreter generateImplicitParser(String grammarText, String testText) {
        LOGGER.info("generating implicit parser");

        Grammar grammar = buildGrammar(grammarText);
        if (grammar == null) return null;

        LexerGrammar lexerGrammar = grammar.getImplicitLexer();
        if(lexerGrammar == null) {
            LOGGER.log(Level.SEVERE, "Error in grammar definition: \n" + grammarText);
            return null;
        }
        ANTLRInputStream antlrInputStream = null;
        try {
            antlrInputStream = new ANTLRInputStream(new ByteArrayInputStream(testText.getBytes()));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open input stream for: " + testText);
            e.printStackTrace();
            return null;
        }
        LexerInterpreter lexerInterpreter = lexerGrammar.createLexerInterpreter(antlrInputStream);

        ParserInterpreter parser = grammar.createParserInterpreter(new org.antlr.v4.runtime.CommonTokenStream(lexerInterpreter));

        return parser;
    }

    public ParseTree runGeneratedParser(ParserInterpreter parser, String grammarText, String startRuleName) {
        org.antlr.v4.tool.Rule startRule = buildGrammar(grammarText).getRule(startRuleName.toLowerCase());

        LOGGER.info("running implicit parser");
        ParseTree parseTree = parser.parse(startRule.index);
        LOGGER.info("parsed rules: " + parseTree.toStringTree(parser));

        return parseTree;
    }

    private Grammar buildGrammar(String grammarText) {
        Grammar grammar = null;
        try {
            grammar = new Grammar(grammarText);
        } catch (org.antlr.runtime.RecognitionException e) {
            e.printStackTrace();
            return null;
        }
        return grammar;
    }


}
