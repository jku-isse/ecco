package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commits the C variant evolution history under examples/c_variants (see its README) and checks
 * out each of the four feature combinations, verifying the checked-out main.c matches the
 * original exactly. Exercises the C adapter through EccoService's real commit/checkout flow, in
 * addition to CReaderIntegrationTest/CWriterIntegrationTest in adapter/c, which test CReader/
 * CWriter directly rather than the full repository round trip.
 */
public class CVariantsCommitCheckoutTest {

	private static final Path EXAMPLES_DIR = findRepoRoot().resolve("examples").resolve("c_variants");

	@Test
	@Timeout(30)
	public void commitAllVariants_thenCheckoutEach_reproducesOriginalContent() throws IOException {
		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory("c-variants-repo").resolve(".ecco"));
		service.init();

		commit(service, "V1_core");
		commit(service, "V2_core_kelvin");
		commit(service, "V3_core_logging");
		commit(service, "V4_core_kelvin_logging");

		checkoutAndVerify(service, "CORE.1", "V1_core");
		checkoutAndVerify(service, "CORE.1, KELVIN.1", "V2_core_kelvin");
		checkoutAndVerify(service, "CORE.1, LOGGING.1", "V3_core_logging");
		checkoutAndVerify(service, "CORE.1, KELVIN.1, LOGGING.1", "V4_core_kelvin_logging");

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

		String expectedContent = withoutBlankLines(Files.readString(EXAMPLES_DIR.resolve(expectedVariantDirName).resolve("main.c"), StandardCharsets.UTF_8));
		String actualContent = withoutBlankLines(Files.readString(checkoutDir.resolve("main.c"), StandardCharsets.UTF_8));
		assertEquals(expectedContent, actualContent);
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
