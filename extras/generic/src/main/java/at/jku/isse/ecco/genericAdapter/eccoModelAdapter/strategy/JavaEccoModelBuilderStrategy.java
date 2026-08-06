package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder.BuilderArtifactData;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.TokenValue;
import at.jku.isse.ecco.tree.Node;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Michael Jahn
 */
public class JavaEccoModelBuilderStrategy implements EccoModelBuilderStrategy {

	// Token definition name constants
	private static final String TYPE_TOKEN = "TYPE";
	private static final String STRING_TOKEN = "STRING";
	private static final String RETURN_TOKEN = "RETURN";
	private static final String VIS_CLASSIFIER_TOKEN = "VISIBLITY_CLASSIFIER";
	private static final String IDENTIFIER_TOKEN = "IDENTIFIER";
	private static final String PAKCAGE_DECL_TOKEN = "PACKAGE_DECL";

	private static final String CLASS_BLOCK_TOKEN = "CLASS";
	private static final String METHOD_BLOCK_TOKEN = "METHOD";
	private static final String IF_BLOCK_TOKEN = "IF";
	private static final String ELSE_BLOCK_TOKEN = "ELSE";
	private static final String FOR_BLOCK_TOKEN = "FOR";
	private static final String OPERATOR_TOKEN = "OPERATOR";
	private static final String ASSIGN_TOKEN = "ASSIGNMENT";

	/**
	 * @return a string describing this strategy
	 */
	@Override
	public String getStrategyName() {
		return "java";
	}

	@Override
	public String getFileExtension() {
		return ".java";
	}

	/**
	 * Helper method to store the tokenDefinitions that correspond to this strategy
	 *
	 * @return
	 */
	@Override
	public List<TokenDefinition> getTokenDefinitions() {
		return Arrays.asList(
				new TokenDefinition(TYPE_TOKEN, "(int|String|double|void)", 5),
				new TokenDefinition(STRING_TOKEN, "\"\"", 11),
				new TokenDefinition(RETURN_TOKEN, "return", 10),
				new TokenDefinition(VIS_CLASSIFIER_TOKEN, "(private|public)", 8),
				new TokenDefinition(PAKCAGE_DECL_TOKEN, "package \\w+(\\.\\w+)*", 8),
				new TokenDefinition(IDENTIFIER_TOKEN, "\\w+", 3),
				new TokenDefinition(OPERATOR_TOKEN, "\\+|-|\\*|\\/|%", 5),
				new TokenDefinition(ASSIGN_TOKEN, "=", 5)
		);
	}

	/**
	 * Helper method to store the sampleSeperator that correpsond to this strategy
	 *
	 * @return
	 */
	@Override
	public List<String> getSampleSeparator() {
		return Arrays.asList(";", "}");
	}

	/**
	 * @return true if, all samples must stop on a linebreak
	 */
	@Override
	public boolean samplesStopOnLineBreak() {
		return false;
	}

	/**
	 * Helper method to store the blockDefinitions that correspond to this strategy
	 *
	 * @return
	 */
	@Override
	public List<BlockDefinition> getBlockDefinitions() {
		return Arrays.asList(
				new BlockDefinition(CLASS_BLOCK_TOKEN, 10, "(public |private)?class (\\w+)(.*)\\{", "\\}", false),
				new BlockDefinition(METHOD_BLOCK_TOKEN, 10, "(public |private )?(\\w+)? (\\w*)\\((\\w* \\w*)?(, \\w* \\w*)*\\) \\{", "\\}", false),
				new BlockDefinition(IF_BLOCK_TOKEN, 10, "if(.*)\\{", "\\}", false),
				new BlockDefinition(ELSE_BLOCK_TOKEN, 10, "else\\s*\\{", "\\}", false),
				new BlockDefinition(FOR_BLOCK_TOKEN, 10, "for\\s*\\(.*\\)\\s*\\{", "\\}", false)
		);
	}

	/**
	 * Helper method to store the comment definitions
	 *
	 * @return
	 */
	@Override
	public List<String> getCommentBlockDefinitions() {
		return Arrays.asList("//.*$");
	}

	/**
	 * @param parsedNonTerminals
	 * @param parsedTokenValues
	 * @return the built artifact, with custom identifier and type
	 */
	@Override
	public BuilderArtifactData createArtifactData(List<String> parsedNonTerminals, List<TokenValue> parsedTokenValues) {
		List<String> valueBuilder = new ArrayList<>();
		StringBuilder typeBuilder = new StringBuilder();
		List<String> printValues = new ArrayList<>();

		for (TokenValue tokenValue : parsedTokenValues) {
			printValues.add(tokenValue.getValue());
			if (tokenValue != null && tokenValue.getTokenDefinition() != null) {
				String value;
				String[] splitValues;
				switch (tokenValue.getTokenDefinition().getName()) {
					case IDENTIFIER_TOKEN:
						valueBuilder.add(tokenValue.getValue());
						break;
					case CLASS_BLOCK_TOKEN:
						// (public |private)?class (\w+)\s*\{
						splitValues = tokenValue.getValue().split("class");
						valueBuilder.add(splitValues[0].trim());
						valueBuilder.add(splitValues[1].replaceAll("\\{", "").trim());
						typeBuilder.append(CLASS_BLOCK_TOKEN);
						break;
					case FOR_BLOCK_TOKEN:
						// for\s*\(.*\)\s*\{
						splitValues = tokenValue.getValue().replaceAll("\\{|\\(|\\)|for", "").split(";");
						for (int i = 0; i < splitValues.length; i++) {
							valueBuilder.add(StringUtils.deleteWhitespace(splitValues[i]));
						}
						typeBuilder.append(FOR_BLOCK_TOKEN);
						break;
					case METHOD_BLOCK_TOKEN:
						// (public |private )?(\w+)? (\w*)\((\w* \w*)?(, \w* \w*)*\) \{
						value = tokenValue.getValue().replaceAll("\\{", "");
						splitValues = value.substring(0, value.indexOf("(")).split("\\s+");
						for (int i = 0; i < splitValues.length; i++) {
							valueBuilder.add(splitValues[i]);
						}
						valueBuilder.add(value.substring(value.indexOf("(")));
						typeBuilder.append(METHOD_BLOCK_TOKEN);
						break;
					case IF_BLOCK_TOKEN:
						// if(.*)\{"
						value = StringUtils.deleteWhitespace(tokenValue.getValue());
						valueBuilder.add(value.replaceAll("\\{", ""));
						typeBuilder.append(IF_BLOCK_TOKEN);
						break;
					case ELSE_BLOCK_TOKEN:
						// else\s*\{
						typeBuilder.append(ELSE_BLOCK_TOKEN);
						break;
					case PAKCAGE_DECL_TOKEN:
						// package \w+(\.\w+)*
						value = StringUtils.deleteWhitespace(tokenValue.getValue().split("package")[1].trim());
						valueBuilder.add(value.replaceAll("\\{", ""));
						typeBuilder.append(PAKCAGE_DECL_TOKEN);
						break;
					case TYPE_TOKEN:
						// (int|String|double|void)
						valueBuilder.add(tokenValue.getValue());
						typeBuilder.append(TYPE_TOKEN);
						break;
					default:
						if (!tokenValue.isUndefinedToken()) {
							typeBuilder.append(tokenValue.getValue());
							valueBuilder.add(tokenValue.getValue());
						} else if (StringUtils.isAlphanumeric(tokenValue.getValue())) {
							valueBuilder.add(tokenValue.getValue());
						}
						break;
				}
			}
		}

		String type = typeBuilder.toString();
		if (type.isEmpty() && parsedNonTerminals.size() > 0) {
			type = parsedNonTerminals.get(0).toUpperCase();
		}

		if (valueBuilder.isEmpty() && parsedNonTerminals.size() > 0) {
			valueBuilder.add(parsedNonTerminals.get(0).toUpperCase());
		}

		return new BuilderArtifactData(valueBuilder, type, parsedTokenValues, printValues);
	}

	/**
	 * Returns an object that identifies the given artifact to be used in references
	 * Can return null, if no references across artifacts shall be supported
	 *
	 * @param artifactData
	 * @return
	 */
	@Override
	public Object getArtifactReferenceId(BuilderArtifactData artifactData) {

		if (METHOD_BLOCK_TOKEN.equals(artifactData.getType())) {
			List<String> identifiers = artifactData.getIdentifierList();
			MethodCallRef refId = new MethodCallRef(identifiers.get(identifiers.size() - 2), Arrays.asList(identifiers.get(identifiers.size() - 1).split(",")));
			artifactData.setRefId(refId);
			return refId;
		}

		return null;
	}

	/**
	 * Returns a list of object with ids that are references by this artifact
	 * Can return null, or an empty list no references across artifacts shall be supported
	 *
	 * @param artifact
	 * @return
	 */
	@Override
	public List<Object> getArtifactReferencingIds(BuilderArtifactData artifact) {
		return null;
	}

	/**
	 * @return a newly generated artifact reference Id, may return null if no references
	 * are supported, or all references are already set in the {@link EccoModelBuilderStrategy#getArtifactReferenceId(BuilderArtifactData)}
	 */
	@Override
	public Object getNextArtifactReferenceId() {
		return null;
	}

	/**
	 * resets the artifact reference id
	 */
	@Override
	public void resetNextArtifactReferenceId() {

	}

	/**
	 * Include the possibility to post process the whole ecco model.
	 * May return null, or the same nodeSet as input if no post processing is necessary
	 *
	 * @param inputRootNode
	 * @return
	 */
	@Override
	public Node postProcessing(Node inputRootNode) {
		return inputRootNode;
	}

	/**
	 * @param artifact
	 * @return true, if all ecco child nodes should be ordered nodes, i.e. that the order of the artifacts is important
	 */
	@Override
	public boolean useOrderedNode(BuilderArtifactData artifact) {
		return true;
	}

	/**
	 * @return if, the "uses" references should be taken into account when comparing two artifacts with the equals method
	 */
	@Override
	public boolean useReferencesInEquals() {
		return false;
	}


	private class MethodCallRef implements Serializable {
		private final String methodName;
		private final List<String> argumentTypes;

		private MethodCallRef(String methodName, List<String> argumentTypes) {
			this.methodName = methodName;
			this.argumentTypes = new ArrayList<>(argumentTypes != null ? argumentTypes : new ArrayList<>());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			MethodCallRef that = (MethodCallRef) o;

			if (argumentTypes.size() != that.argumentTypes.size()) return false;
			if (methodName != null ? !methodName.equals(that.methodName) : that.methodName != null) return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = methodName != null ? methodName.hashCode() : 0;
			result = 31 * result + argumentTypes.size();
			return result;
		}
	}
}
