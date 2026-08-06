package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder;


import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.parserGenerator.AntlrParserWrapperServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.Tokenizer;
import at.jku.isse.ecco.tree.Node;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserInterpreter;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Michael Jahn
 */
public class EccoModelBuilderImpl implements EccoModelBuilder {


	private static final String END_LABEL = "END";

	private final AntlrParserWrapperServiceImpl antlrParserWrapperServiceImpl;
	private final EntityFactory entityFactory;

	public EccoModelBuilderImpl(EntityFactory entityFactory, AntlrParserWrapperServiceImpl antlrParserWrapperServiceImpl) {
		this.antlrParserWrapperServiceImpl = antlrParserWrapperServiceImpl;
		this.entityFactory = entityFactory;
	}


	@Override
	public Node buildEccoModel(EccoModelBuilderStrategy strategy, NonTerminal rootSymbol, String filePath, boolean tryWrittenParser) throws IOException, AmbiguousTokenDefinitionsException, ParserErrorException {

		// tokenize file contents
		TokenizerResult tokenizerResult = tokenizeFile(strategy, filePath);
		String tokenizedFile = tokenizerResult.tokenizedFile;

		// generate and run parser
		String antlrGrammar = antlrParserWrapperServiceImpl.convertToAntlrGrammar(strategy.getStrategyName(), rootSymbol, true);
		ParseTree parseTree;
		Parser parser;
		if (tryWrittenParser) {
			try {
				Parser writtenParser = antlrParserWrapperServiceImpl.getWrittenParser(strategy.getStrategyName(), tokenizedFile);
				parseTree = (ParseTree) antlrParserWrapperServiceImpl.runWrittenParser(writtenParser, rootSymbol.getName());
				parser = writtenParser;
			} catch (Throwable e) {
				System.err.println("No written parser could be found... Using implicitly generated parser.");
				ParserInterpreter implicitParser = antlrParserWrapperServiceImpl.generateImplicitParser(antlrGrammar, tokenizedFile);
				parseTree = antlrParserWrapperServiceImpl.runGeneratedParser(implicitParser, antlrGrammar, rootSymbol.getName());
				parser = implicitParser;
			}
		} else {
			ParserInterpreter implicitParser = antlrParserWrapperServiceImpl.generateImplicitParser(antlrGrammar, tokenizedFile);
			parseTree = antlrParserWrapperServiceImpl.runGeneratedParser(implicitParser, antlrGrammar, rootSymbol.getName());
			parser = implicitParser;
		}

		return buildEccoModelFromParseTree(strategy, tokenizerResult.getParsedTokenValues(), parseTree, parser);

	}


	@Override
	public Node buildEccoModel(EccoModelBuilderStrategy strategy, String antlrGrammarFile, String filePath, boolean tryWrittenParser) throws IOException, AmbiguousTokenDefinitionsException, ParserErrorException {
		// tokenize file contents
		TokenizerResult tokenizerResult = tokenizeFile(strategy, filePath);

		// generate and run parser
		ParserInterpreter parser = antlrParserWrapperServiceImpl.generateParserFromFile(new File(antlrGrammarFile), tokenizerResult.getTokenizedFile());
		ParseTree parseTree = antlrParserWrapperServiceImpl.parseImplicitly(new File(antlrGrammarFile), parser);

		// generate base ecco model from parseTree
		return buildEccoModelFromParseTree(strategy, tokenizerResult.getParsedTokenValues(), parseTree, parser);
	}

	/**
	 * PRIVATE METHODS
	 */

	private Node buildEccoModelFromParseTree(EccoModelBuilderStrategy strategy, List<TokenValue> parsedTokenValues, ParseTree parseTree, Parser parser) throws ParserErrorException {

		// generate base ecco model from parseTree
		Map<Object, Artifact<BuilderArtifactData>> referencedArtifacts = new HashMap<>();
		List<Artifact<BuilderArtifactData>> referencingArtifacts = new ArrayList<>();
		Node eccoModelNode = buildArtifactStructureNode(strategy, parseTree, parsedTokenValues, parser.getRuleNames(), referencedArtifacts, referencingArtifacts);

		// post processes artifacts with references
		for (Artifact<BuilderArtifactData> referencingArtifact : referencingArtifacts) {
			for (Object reference : strategy.getArtifactReferencingIds(referencingArtifact.getData())) {
				referencingArtifact.getData().invalidateParsedTokenValues();
				if (referencedArtifacts.containsKey(reference)) {
					referencingArtifact.addUses(entityFactory.createArtifactReference(referencingArtifact, referencedArtifacts.get(reference)));
					referencedArtifacts.get(reference).addUsedBy(entityFactory.createArtifactReference(referencedArtifacts.get(reference), referencingArtifact));
				} else {
					System.err.println("Artifact reference: " + reference + " from: " + referencingArtifact.getData().getIdentifier() + " not found!");
				}
			}
		}

		// clean duplicates when nodes are unordered
		cleanDuplicatesInUnordered(eccoModelNode);
		eccoModelNode.updateArtifactReferences();

		// execute post processing step from strategy
		Node postProcessedRoot = strategy.postProcessing(eccoModelNode);
		if (postProcessedRoot != null) {
			eccoModelNode = postProcessedRoot;
		}

		return eccoModelNode;
	}

	private void cleanDuplicatesInUnordered(Node eccoModelNode) {

		Map<Integer, List<Artifact>> uniqueChildNodes = new HashMap<>();
		Iterator<Node> childIterator = eccoModelNode.getChildren().iterator();
		Artifact childArtifact;
		Node childNode;
		while (childIterator.hasNext()) {
			childNode = childIterator.next();
			childArtifact = childNode.getArtifact();
			if (!eccoModelNode.getArtifact().isOrdered()) {
				if (uniqueChildNodes.containsKey(childArtifact.hashCode()) && uniqueChildNodes.get(childArtifact.hashCode()).contains(childArtifact)) {
					childArtifact.putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, uniqueChildNodes.get(childArtifact.hashCode()).stream().filter(childArtifact::equals).findFirst().get());
					childIterator.remove();
				} else {
					if (!uniqueChildNodes.containsKey(childArtifact.hashCode())) {
						uniqueChildNodes.put(childArtifact.hashCode(), new ArrayList<>());
					}
					uniqueChildNodes.get(childArtifact.hashCode()).add(childArtifact);
				}
			}
			cleanDuplicatesInUnordered(childNode);
		}
	}

	private TokenizerResult tokenizeFile(EccoModelBuilderStrategy strategy, String filePath) throws IOException, AmbiguousTokenDefinitionsException {

		// combine token and block definitions
		Map<String, TokenDefinition> tokenDefinitionMap = new HashMap<>();
		for (BlockDefinition blockDefinition : strategy.getBlockDefinitions()) {
			if (tokenDefinitionMap.containsKey(blockDefinition.getName().toUpperCase())) {
				TokenDefinition toAdd = new TokenDefinition(blockDefinition.getName().toUpperCase(),
						"(" + tokenDefinitionMap.get(blockDefinition.getName().toUpperCase()).getRegexString() + ")|(" + blockDefinition.getStartRegexString() + ")", blockDefinition.getPriority());
				tokenDefinitionMap.put(blockDefinition.getName().toUpperCase(), toAdd);
			} else {
				tokenDefinitionMap.put(blockDefinition.getName().toUpperCase(), new TokenDefinition(blockDefinition.getName().toUpperCase(), blockDefinition.getStartRegexString(), blockDefinition.getPriority()));
			}
			if (blockDefinition.getEndRegexString() != null) {
				if (tokenDefinitionMap.containsKey(END_LABEL)
						&& !tokenDefinitionMap.get(END_LABEL).getRegexString().contains(blockDefinition.getEndRegexString())) {
					TokenDefinition toAdd = new TokenDefinition(END_LABEL,
							"(" + tokenDefinitionMap.get(END_LABEL).getRegexString() + ")|(" + blockDefinition.getEndRegexString() + ")", blockDefinition.getPriority());
					tokenDefinitionMap.put(END_LABEL, toAdd);
				} else {
					tokenDefinitionMap.put(END_LABEL, new TokenDefinition(END_LABEL, blockDefinition.getEndRegexString(), blockDefinition.getPriority()));
				}
			}
		}

		List<TokenDefinition> tokenDefinitions = new ArrayList<>();
		tokenDefinitions.addAll(tokenDefinitionMap.values());
		tokenDefinitions.addAll(strategy.getTokenDefinitions());

		// tokenize file
		Tokenizer tokenizer = new Tokenizer(tokenDefinitions);

		// filter out comments
		StringBuilder file = new StringBuilder(readFileToString(filePath));
		List<Pattern> commentPatterns = strategy.getCommentBlockDefinitions().stream().map(s -> Pattern.compile(s, Pattern.DOTALL | Pattern.MULTILINE)).collect(Collectors.toList());
		for (Pattern commentPattern : commentPatterns) {
			Matcher matcher = commentPattern.matcher(file);
			if (matcher.find()) {
				file = new StringBuilder(matcher.replaceAll(""));
			}
		}
		String tokenizedFile = tokenizer.tokenizeToString(file.toString());
		List<TokenValue> parsedTokenValues = tokenizer.getTokenValues();
		return new TokenizerResult(tokenizedFile, parsedTokenValues);
	}

	private Node buildArtifactStructureNode(EccoModelBuilderStrategy strategy, ParseTree parseTree, List<TokenValue> parsedTokenValues, String[] ruleNames,
											Map<Object, Artifact<BuilderArtifactData>> referencedArtifacts, List<Artifact<BuilderArtifactData>> referencingArtifacts) throws ParserErrorException {

		if (parseTree instanceof RuleNode) {
			int ruleIndex = ((RuleNode) parseTree).getRuleContext().getRuleIndex();
			String ruleName = ruleNames[ruleIndex];
			if (NonTerminal.isStructureSymbolName(ruleName)) {

				BuilderArtifactData artifactData = strategy.createArtifactData(Arrays.asList(ruleName.toUpperCase()), Arrays.asList(new TokenValue(null, "", false)));
				Artifact<BuilderArtifactData> artifact = entityFactory.createArtifact(artifactData);
				updateArtifactReferences(strategy, referencedArtifacts, referencingArtifacts, artifact);

				Node eccoNode;
				if (strategy.useOrderedNode(artifactData)) {
					eccoNode = entityFactory.createOrderedNode(artifact);
				} else {
					eccoNode = createUnorderedNode(artifact, strategy);
				}

				// process children
				for (int i = 0; i < parseTree.getChildCount(); i++) {
					eccoNode.addChild(buildArtifactStructureNode(strategy, parseTree.getChild(i), parsedTokenValues, ruleNames, referencedArtifacts, referencingArtifacts));
				}
				return eccoNode;
			} else {
				return buildArtifactNode(strategy, parseTree, parsedTokenValues, ruleNames, referencedArtifacts, referencingArtifacts);
			}
		} else if (parseTree instanceof ErrorNode) {
			System.err.println("Error node found: " + parseTree.toString());
			if (ParameterSettings.EXCEPTION_ON_PARSE_ERROR) {
				throw new ParserErrorException(parseTree.getText());
			}
		} else if (parseTree instanceof TerminalNode) {
			TokenValue curTokenValue = null;
			while (curTokenValue == null || StringUtils.isWhitespace(curTokenValue.getValue())) {
				curTokenValue = parsedTokenValues.get(0);
				parsedTokenValues.remove(0);
			}
			if (!parseTree.getText().toLowerCase().equals(curTokenValue.getTokenDefinition().getName().toLowerCase())) {
				System.err.println("Mismatch in processing paredTokens lists: " + parseTree.getText() + " is not equal: " + curTokenValue.getTokenDefinition().getName());
			}

			Node eccoNode;
			BuilderArtifactData artifactData = strategy.createArtifactData(new ArrayList<>(), Arrays.asList(curTokenValue));
			Artifact<BuilderArtifactData> artifact = entityFactory.createArtifact(artifactData);
			// handle optional artifact references
			updateArtifactReferences(strategy, referencedArtifacts, referencingArtifacts, artifact);
			if (strategy.useOrderedNode(artifact.getData())) {
				eccoNode = entityFactory.createOrderedNode(artifact);
			} else {
				eccoNode = createUnorderedNode(artifact, strategy);
			}

			return eccoNode;
		}
		return null;
	}

	private Node createUnorderedNode(Artifact<BuilderArtifactData> artifact, EccoModelBuilderStrategy strategy) {
		Node eccoNode = entityFactory.createNode(artifact);
		if (strategy.useReferencesInEquals()) {
			eccoNode.getArtifact().setUseReferencesInEquals(true);
		}
		return eccoNode;
	}

	private void updateArtifactReferences(EccoModelBuilderStrategy strategy, Map<Object, Artifact<BuilderArtifactData>> referencedArtifacts, List<Artifact<BuilderArtifactData>> referencingArtifacts, Artifact<BuilderArtifactData> artifact) {
		Object artifactReferenceId = strategy.getArtifactReferenceId(artifact.getData());
		List<Object> references = strategy.getArtifactReferencingIds(artifact.getData());
		if (artifactReferenceId != null) {
			referencedArtifacts.put(artifactReferenceId, artifact);
		}
		if (references != null && !references.isEmpty()) {
			referencingArtifacts.add(artifact);
		} else {
			artifact.getData().invalidateParsedTokenValues();
		}
	}

	private Node buildArtifactNode(EccoModelBuilderStrategy strategy, ParseTree parseTree, List<TokenValue> parsedTokenValues, String[] ruleNames,
								   Map<Object, Artifact<BuilderArtifactData>> referencedArtifacts, List<Artifact<BuilderArtifactData>> referencingArtifacts) throws ParserErrorException {
		if (parseTree instanceof RuleNode) {
			List<TerminalNode> terminalNodes = new ArrayList<>();
			List<String> parsedRuleNames = new ArrayList<>();
			collectTerminalNodes(parseTree, terminalNodes, parsedRuleNames, ruleNames);

			// map terminalNodes to parsedTokens
			List<TokenValue> nodeTokenValues = new ArrayList<>();
			for (TerminalNode terminalNode : terminalNodes) {
				TokenValue curTokenValue = null;
				while (curTokenValue == null || StringUtils.isWhitespace(curTokenValue.getValue())) {
					curTokenValue = parsedTokenValues.get(0);
					parsedTokenValues.remove(0);
				}
				if (!terminalNode.getText().toLowerCase().equals(curTokenValue.getTokenDefinition().getName().toLowerCase())) {
					System.err.println("Mismatch in processing paredTokens lists: " + terminalNode.getText() + " is not equal: " + curTokenValue.getTokenDefinition().getName());
				}
				nodeTokenValues.add(curTokenValue);
			}

			BuilderArtifactData artifactData = strategy.createArtifactData(parsedRuleNames, nodeTokenValues);
			Artifact<BuilderArtifactData> artifact = entityFactory.createArtifact(artifactData);
			updateArtifactReferences(strategy, referencedArtifacts, referencingArtifacts, artifact);

			Node eccoNode;
			if (strategy.useOrderedNode(artifactData)) {
				eccoNode = entityFactory.createOrderedNode(artifact);
			} else {
				eccoNode = createUnorderedNode(artifact, strategy);
			}

			return eccoNode;
		}
		return null;
	}

	private List<TerminalNode> collectTerminalNodes(ParseTree parseTree, List<TerminalNode> curTerminalNodes, List<String> parsedRules, String[] ruleNames) throws ParserErrorException {
		if (parseTree instanceof RuleNode) {
			for (int i = 0; i < parseTree.getChildCount(); i++) {
				collectTerminalNodes(parseTree.getChild(i), curTerminalNodes, parsedRules, ruleNames);
			}
			parsedRules.add(ruleNames[((RuleNode) parseTree).getRuleContext().getRuleIndex()]);
		} else if (parseTree instanceof TerminalNode) {
			curTerminalNodes.add((TerminalNode) parseTree);
			return curTerminalNodes;
		} else if (parseTree instanceof ErrorNode) {
			System.err.println("Error node found: " + parseTree.toString());
			if (ParameterSettings.EXCEPTION_ON_PARSE_ERROR) {
				throw new ParserErrorException(parseTree.getText());
			}
		}

		return curTerminalNodes;
	}

	private String readFileToString(String fileName) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
		String line;
		StringBuilder content = new StringBuilder();
		while ((line = br.readLine()) != null) {
			content.append(line + "\n");
		}
		return content.toString();
	}

	private class TokenizerResult {
		private final String tokenizedFile;
		private final List<TokenValue> parsedTokenValues;

		private TokenizerResult(String tokenizedFile, List<TokenValue> parsedTokenValues) {
			this.tokenizedFile = tokenizedFile;
			this.parsedTokenValues = parsedTokenValues;
		}

		public String getTokenizedFile() {
			return tokenizedFile;
		}

		public List<TokenValue> getParsedTokenValues() {
			return parsedTokenValues;
		}
	}
}
