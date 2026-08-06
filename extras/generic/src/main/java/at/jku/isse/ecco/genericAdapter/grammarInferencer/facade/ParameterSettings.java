package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

/**
 * Collection of static settings
 *
 * @author Michael Jahn
 */
public class ParameterSettings {


    /**
     * General Settings
     */

    public static boolean EXCEPTION_ON_PARSE_ERROR = true; /* if true, thwrow an exception if a parse error was found, otherwise ignore parse errors */

    public static boolean STATISTICS_OUTPUT = true; /* whether statistic relevant messages shall be printed */

    public static boolean INFO_OUTPUT = false; /* whether info messages shall be printed */

    public static boolean DEBUG_OUTPUT = false; /* whether debug messages shall be printed */

    public static final boolean IGNORE_WHITESPACES = true; /* ignore all whitespaces that are included in a sample */

    public static final boolean PERFORM_FINAL_GRAMMAR_SANITY_CHECK = true; /* check if the final grammar can parse all samples */

    public static final boolean FAIL_ON_INVALID_GRAMMAR_SANITY_CHECK = false; /* let the whole mutation process fail, if a sanity check fails */


    /**
     * Settings for Sequitur module
     */

    public static final boolean USE_SEQUITUR_INITIAL_GRAMMAR = false;  /* use sequitur for initial grammar generation */


    /**
     * Settings for GrammarMutator module
     */

    public static final boolean USE_OPTIMIZED_DIFF_FOR_DISTANCE = false;
    public static final boolean IGNORE_REPEATING_PATTERNS_FOR_DISTANCE = false;

    // summarization optimization
    public static final boolean SUMMARIZE_ALL_BETWEEN_TWO_EQUALS = true; /* whether to first summarize all mutation between two equal operations */

    public static final int MIN_SUMMARIZATION_DISTANCE = 2; /* 0 means no summarization at all */
    public static final int MIN_SUMMARIZATION_COUNT = 3; /* Attention: a value smaller than 3 does not really make sense and may lead to errors */
    public static final boolean INCLUDE_EQUALS_IN_COUNT = true; /* if equal diffs should be counted here as well */

    // general grammar mutation settings
    public static final boolean USE_DEFAULT_FALLBACK = true; /* if enabled, a fallback mutation will used in case the default mutation fails*/
    public static final boolean TRY_OPTIMAL_FALLBACK = true; /* try an optimal fallback mutation first - disable this if there are performance issues */
    public static final boolean MATCH_DIFF_TO_FIND_MODIFY_RULE = true; /* use the diffed sample as basis to find the place where to mutate the grammar */
    public static final boolean CATCH_EXCEPTIONS_DURING_MUTATION = true; /* use fallback mutation in case an (common) exception occured during mutation */
    public static final int MIN_NR_REPETITIONS = 4; /* min nr of repetitions to use a loop rule in the grammar for the repeating pattern */

    public static final int MAX_DISTANCE_NORMAL_DIFF = 10; /* the maximum distance to which a "normal" diff mutation is used */
    public static final int MIN_NR_MUTATION_DIFFS = 1; /* the minimum number of mutation diffs that are necessary to use fallback diff beforehead*/
    public static final boolean DONT_MERGE_DIFFERENT_BEGININGS_IN_ROOT = true; /* ensure that the rootRule contains only terminals as beginning -> simplifies subsequent mutations */

}
