package at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization;

import java.io.Serializable;

/**
 * @author Michael Jahn
 */
public class TokenValue implements Serializable {

	private final TokenDefinition tokenDefinition;
	private final String value;
	private final boolean isUndefinedToken;


	public TokenValue(TokenDefinition tokenDefinition, String value, boolean isUndefinedToken) {
		this.tokenDefinition = tokenDefinition;
		this.value = value;
		this.isUndefinedToken = isUndefinedToken;
	}

	public String getValue() {
		return value;
	}

	public TokenDefinition getTokenDefinition() {
		return tokenDefinition;
	}

	public boolean isUndefinedToken() {
		return isUndefinedToken;
	}

}
