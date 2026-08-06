package at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * Class representing a token description
 */
public class TokenDefinition implements Serializable {

	private static int UniqueId = 0;

	private final int id;
	private final String name;
	private final Pattern regex;
	private final String regexString;

	// to resolve ambiguous token definitions, the one with the highest priority will be used
	private final int priority;

	public TokenDefinition(String name, String regex, int priority) {
		this.priority = priority;
		this.id = TokenDefinition.UniqueId++;
		this.name = name;
		this.regex = Pattern.compile(regex);
		this.regexString = regex;
	}

	public TokenDefinition(String name, int priority) {
		this.priority = priority;
		this.id = TokenDefinition.UniqueId++;
		this.name = name;
		this.regex = null;
		this.regexString = name;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Pattern getRegex() {
		return regex;
	}

	public String getRegexString() {
		return regexString;
	}

	public int getPriority() {
		return priority;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		TokenDefinition that = (TokenDefinition) o;

		if (priority != that.priority) return false;
		if (name != null ? !name.equals(that.name) : that.name != null) return false;
		if (regexString != null ? !regexString.equals(that.regexString) : that.regexString != null) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = name != null ? name.hashCode() : 0;
		result = 31 * result + (regexString != null ? regexString.hashCode() : 0);
		result = 31 * result + priority;
		return result;
	}
}
