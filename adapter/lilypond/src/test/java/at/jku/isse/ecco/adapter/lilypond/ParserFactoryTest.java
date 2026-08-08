package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parce.NodesDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Exercises {@link ParserFactory}. Deliberately does not assert on exactly which backend (if any)
 * {@code getParser()} resolves to when {@code parseFiles} is true - that's this test environment's
 * classpath, not this class's behavior. What matters, and what the hardening this covers is about,
 * is that a broken-but-present backend (confirmed in this environment: the py4j FileParser class
 * loads but its own py4j.GatewayServerListener dependency doesn't, throwing NoClassDefFoundError)
 * never escapes getParser() uncaught - it must always return cleanly, logging the real cause instead
 * of masking it as "not installed". */
public class ParserFactoryTest {

	@AfterEach
	void restoreDefault() {
		// parseFiles is a module-wide static flag - reset it so this test class can't leak state
		// into any other test running in the same JVM
		ParserFactory.setParseFiles(true);
	}

	@Test
	void getParserNeverThrowsRegardlessOfWhichBackendsAreActuallyAvailable() {
		ParserFactory.setParseFiles(true);

		assertDoesNotThrow(ParserFactory::getParser);
	}

	@Test
	void getParserWithParseFilesDisabledReturnsANodesDeserializer() {
		ParserFactory.setParseFiles(false);

		assertInstanceOf(NodesDeserializer.class, ParserFactory.getParser());
	}
}
