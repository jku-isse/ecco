package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commits the C variant evolution history under examples/c_variants (see its README) and checks
 * out all eight feature combinations, verifying every checked-out file (converter.h, converter.c,
 * main.c) matches the original exactly. Exercises the C adapter through EccoService's real
 * commit/checkout flow and its multi-file handling, in addition to CReaderIntegrationTest/
 * CWriterIntegrationTest in adapter/c, which test CReader/CWriter directly rather than the full
 * repository round trip.
 */
public class CVariantsCommitCheckoutTest {

	private static final Path EXAMPLES_DIR = findRepoRoot().resolve("examples").resolve("c_variants");
	private static final List<String> FILE_NAMES = List.of("converter.h", "converter.c", "main.c");

	private static final List<String> VARIANT_DIRS = List.of(
			"V1_core",
			"V2_core_kelvin",
			"V3_core_logging",
			"V4_core_validation",
			"V5_core_kelvin_logging",
			"V6_core_kelvin_validation",
			"V7_core_logging_validation",
			"V8_core_kelvin_logging_validation"
	);

	private static final List<String> CONFIGURATIONS = List.of(
			"CORE.1",
			"CORE.1, KELVIN.1",
			"CORE.1, LOGGING.1",
			"CORE.1, VALIDATION.1",
			"CORE.1, KELVIN.1, LOGGING.1",
			"CORE.1, KELVIN.1, VALIDATION.1",
			"CORE.1, LOGGING.1, VALIDATION.1",
			"CORE.1, KELVIN.1, LOGGING.1, VALIDATION.1"
	);

	@Test
	@Timeout(60)
	public void commitAllVariants_thenCheckoutEach_reproducesOriginalContent() throws IOException {
		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory("c-variants-repo").resolve(".ecco"));
		service.init();

		for (String variantDir : VARIANT_DIRS) {
			commit(service, variantDir);
		}

		for (int i = 0; i < VARIANT_DIRS.size(); i++) {
			checkoutAndVerify(service, CONFIGURATIONS.get(i), VARIANT_DIRS.get(i));
		}

		service.close();
	}

	private void commit(EccoService service, String variantDirName) {
		service.setBaseDir(EXAMPLES_DIR.resolve(variantDirName));
		service.commit(variantDirName);
	}

	private void checkoutAndVerify(EccoService service, String configurationString, String expectedVariantDirName) throws IOException {
		Path checkoutDir = Files.createTempDirectory("c-variants-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout(configurationString);

		for (String fileName : FILE_NAMES) {
			String expectedContent = withoutBlankLines(Files.readString(EXAMPLES_DIR.resolve(expectedVariantDirName).resolve(fileName), StandardCharsets.UTF_8));
			String actualContent = withoutBlankLines(Files.readString(checkoutDir.resolve(fileName), StandardCharsets.UTF_8));
			assertEquals(expectedContent, actualContent, () -> fileName + " mismatch for configuration \"" + configurationString + "\"");
		}
	}

	/**
	 * CReader doesn't track blank lines as artifacts (see CReaderIntegrationTest's expected-children
	 * lists, which never include one), so they don't survive a commit/checkout round trip. The
	 * example files keep them for readability; strip them here rather than from the examples.
	 */
	private String withoutBlankLines(String content) {
		return content.lines().filter(line -> !line.isBlank()).reduce("", (a, b) -> a + b + "\n");
	}

	private static Path findRepoRoot() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			if (Files.exists(dir.resolve("settings.gradle"))) {
				return dir;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("Could not locate repository root (no settings.gradle found in any ancestor directory).");
	}
}
