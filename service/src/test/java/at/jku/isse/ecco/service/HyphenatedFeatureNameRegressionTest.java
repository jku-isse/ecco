package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression test for a real "Import from Git" crash: {@code LogicException: String could not be
 * parsed according to grammar ... Lexer exception when parsing the formula}, thrown from {@code
 * Repository.setRetroactiveConditions()} while committing a SECOND variant, as soon as more than
 * one feature exists in the repository.
 * <p>
 * Root cause: {@code SerFeatureRevision.getLogicLiteralRepresentation()} sanitized its internal id
 * (stripping "-") before handing the string to LogicNG's propositional parser, but never sanitized
 * the feature's own NAME the same way - despite a comment right above it already warning "don't
 * use '.' and '-' in order to make it parsable by logicNG". A hyphenated feature name (e.g.
 * "shopping-cart" - a common, legitimate style, and exactly what the "Import from Git" LLM
 * suggestion prompt is told to produce for multi-word names) produced a literal LogicNG's lexer
 * rejected outright.
 * <p>
 * Fixed by sanitizing the feature name the same way the id already was.
 */
public class HyphenatedFeatureNameRegressionTest {

	@Test
	@Timeout(30)
	public void secondCommit_withHyphenatedFeatureName_doesNotThrow() throws IOException {
		Path v1 = Files.createTempDirectory("hyphenated-feature-v1");
		Path v2 = Files.createTempDirectory("hyphenated-feature-v2");
		Files.writeString(v1.resolve("real.txt"), "hello world");
		Files.writeString(v2.resolve("real.txt"), "hello world, updated");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(Files.createTempDirectory("hyphenated-feature-repo").resolve(".ecco"));
			service.init();

			service.setBaseDir(v1);
			service.commit("commit 1", "shopping-cart");

			service.setBaseDir(v2);
			service.commit("commit 2", "user-login");
		}
	}
}