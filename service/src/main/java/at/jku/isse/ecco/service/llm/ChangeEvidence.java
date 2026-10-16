package at.jku.isse.ecco.service.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic evidence about a commit for the feature suggestion: the kind of each changed file
 * and hints in the commit message. Features are cross-cutting - one feature's changes can be
 * sources, tests, documentation and build files alike - so the kind of a file is evidence about the
 * change, never a feature by itself. (The same rules as experiments/llm-feature-suggestion/evidence.py.)
 */
public final class ChangeEvidence {

	private ChangeEvidence() {
	}

	private static final Pattern TEST = Pattern.compile("(^|/)(tests?|__tests__|spec|testing|test-?utils?)/|\\.(spec|test)\\.[^/]+$|(Test|Tests|IT)\\.(java|kt)$|_test\\.(go|py)$|(^|/)test_[^/]+\\.py$", Pattern.CASE_INSENSITIVE);
	private static final Pattern DOCS = Pattern.compile("(^|/)(docs?|documentation)/|\\.(md|markdown|rst|adoc|txt)$|(^|/)(readme|changelog|contributing|authors|notice)[^/]*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CI = Pattern.compile("(^|/)(\\.github|\\.circleci|\\.gitlab)/|(^|/)(\\.travis\\.yml|\\.gitlab-ci\\.yml|azure-pipelines\\.yml|jenkinsfile|appveyor\\.yml)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BUILD = Pattern.compile("(^|/)(pom\\.xml|build\\.gradle(\\.kts)?|settings\\.gradle(\\.kts)?|gradle\\.properties|gradlew(\\.bat)?|package\\.json|package-lock\\.json|pnpm-lock\\.yaml|yarn\\.lock|"
			+ "pnpm-workspace\\.yaml|tsconfig[^/]*\\.json|makefile|cmakelists\\.txt|[^/]+\\.cmake|setup\\.py|setup\\.cfg|pyproject\\.toml|requirements[^/]*\\.txt|"
			+ "cargo\\.toml|cargo\\.lock|go\\.mod|go\\.sum|[^/]+\\.csproj|license[^/]*|\\.gitignore|\\.gitattributes|\\.editorconfig|\\.npmignore|"
			+ "rolldown\\.config\\.[^/]+|rollup\\.config\\.[^/]+|vite\\.config\\.[^/]+|eslint[^/]*|\\.prettierrc[^/]*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SOURCE = Pattern.compile("\\.(java|js|jsx|mjs|cjs|ts|tsx|mts|cts|vue|c|h|cc|cpp|hpp|cxx|py|go|rs|cs|kt|css|scss|less|html|htm|rb|php|scala|swift|m|mm|sh|sql|groovy)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern RESOURCE = Pattern.compile("\\.(json|ya?ml|xml|properties|toml|ini|svg|png|jpe?g|gif|ico|woff2?|ttf|csv)$", Pattern.CASE_INSENSITIVE);

	/** The kind of a changed file: source, test, documentation, build, ci, resource or other. A test's source is a test. */
	public static String kind(String path) {
		if (TEST.matcher(path).find())
			return "test";
		if (CI.matcher(path).find())
			return "ci";
		if (BUILD.matcher(path).find())
			return "build";
		if (DOCS.matcher(path).find())
			return "documentation";
		if (SOURCE.matcher(path).find())
			return "source";
		if (RESOURCE.matcher(path).find())
			return "resource";
		return "other";
	}

	private static final Pattern RELEASE = Pattern.compile("\\b(release|v?\\d+\\.\\d+(\\.\\d+)?|version|bump)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern MERGE = Pattern.compile("^merge\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern REVERT = Pattern.compile("^revert\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern ISSUE = Pattern.compile("(#\\d+|\\bissue\\b|\\bfix(es|ed)? #?\\d+)", Pattern.CASE_INSENSITIVE);

	/** What the commit message says about the kind of commit: release or version, merge, revert, issue reference. */
	public static List<String> messageHints(String message) {
		List<String> hints = new ArrayList<>();
		String text = message == null ? "" : message;
		if (RELEASE.matcher(text).find())
			hints.add("release or version");
		if (MERGE.matcher(text).find())
			hints.add("merge");
		if (REVERT.matcher(text).find())
			hints.add("revert");
		if (ISSUE.matcher(text).find())
			hints.add("issue reference");
		return hints;
	}
}
