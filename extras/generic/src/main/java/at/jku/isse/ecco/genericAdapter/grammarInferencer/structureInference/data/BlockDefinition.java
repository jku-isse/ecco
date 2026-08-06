package at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data;

import java.util.regex.Pattern;

/**
 * Class representing a structure block in a file
 *
 * @author Michael Jahn
 */
public class BlockDefinition {

    private static int UniqueId = 0;

    private final int id;
    private final String name;

    // to resolve ambiguous token definitions, the one with the highest priority will be used
    private final int priority;

    private final String startRegexString;
    private final String endRegexString;

    // if true, then the capturing groups in the startRegex, must have the same values as the same groups in the endRegex
    // e.g. "model (.*);" and end (.*);"
    private final boolean matchGroups;

    private final Pattern startRegex;
    private final Pattern endRegex;


    public BlockDefinition(String name, int priority, String startRegexString, String endRegexString, boolean matchGroups) {
        this.priority = priority;

        this.startRegexString = startRegexString;
        this.endRegexString = endRegexString;
        this.matchGroups = matchGroups;

        this.id = BlockDefinition.UniqueId++;
        this.name = name;
        this.startRegex = startRegexString != null ? Pattern.compile(startRegexString) : null;
        this.endRegex = endRegexString != null ? Pattern.compile(endRegexString): null;
    }

    public String getStartRegexString() {
        return startRegexString;
    }

    public String getEndRegexString() {
        return endRegexString;
    }

    public Pattern getStartRegex() {
        return startRegex;
    }

    public Pattern getEndRegex() {
        return endRegex;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public int compareTo(BlockDefinition other) {
        return other.getPriority() - getPriority();
    }

    public boolean matchesStartRegex(String input) {
        return startRegex != null && startRegex.matcher(input).find();
    }

    public boolean matchesEndRegex(String input) {
        return endRegex != null && endRegex.matcher(input).find();
    }

    public boolean isMatchGroups() {
        return matchGroups;
    }
}
