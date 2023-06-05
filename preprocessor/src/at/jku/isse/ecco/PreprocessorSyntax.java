package at.jku.isse.ecco;

public interface PreprocessorSyntax {
	public final static String IF = "#if";
	/**
	 * Use this if IF uses brackets before the condition. Empty -> no brackets.
	 */
	public final static String CONDITION_BEGIN = "";
	/**
	 * Use this if IF uses brackets after the condition. Empty -> no brackets.
	 */
	public final static String CONDITION_END = "";
	public final static String ELSE = "#else";
	public final static String ENDIF = "#endif";
}
