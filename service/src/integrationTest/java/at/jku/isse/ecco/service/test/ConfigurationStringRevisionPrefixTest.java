package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes a real bug found while testing the Compose Configuration dialog's paste path: every
 * place a {@link Configuration} is displayed uses {@link FeatureRevision#getFeatureRevisionString()},
 * which truncates a revision's id to 7 characters (git-short-hash style) - but
 * {@link EccoService#parseConfigurationString} required an exact id match, silently creating a brand
 * new, bogus revision for any truncated id instead of resolving it. Pasting a configuration string
 * copied from anywhere else in the GUI back into a text field that round-trips through
 * parseConfigurationString (e.g. ConfigurationPickerDialog) would never actually select the feature
 * it named.
 */
public class ConfigurationStringRevisionPrefixTest {

	@Test
	public void parseConfigurationString_resolvesTruncatedRevisionId() throws IOException {
		Path repoDir = Files.createTempDirectory("truncated-id-repo");
		Path eccoDir = repoDir.resolve(".ecco");
		Path baseDir = Files.createTempDirectory("truncated-id-base");

		try (EccoService svc = new EccoService()) {
			svc.setRepositoryDir(eccoDir);
			svc.init();
			svc.setBaseDir(baseDir);

			Files.write(baseDir.resolve("a.txt"), "hello\n".getBytes(), StandardOpenOption.CREATE);
			svc.commit("c1", "FeatureA");

			Feature featureA = svc.getRepository().getFeatures().stream()
					.filter(f -> f.getName().equals("FeatureA"))
					.findFirst().orElseThrow();
			FeatureRevision revA = featureA.getLatestRevision();

			String truncated = "FeatureA." + revA.getId().substring(0, Math.min(revA.getId().length(), 7));

			Configuration parsed = svc.parseConfigurationString(truncated);

			assertEquals(1, parsed.getFeatureRevisions().length);
			assertTrue(java.util.Arrays.asList(parsed.getFeatureRevisions()).contains(revA),
					"truncated revision id '" + truncated + "' should resolve to the existing revision "
							+ revA.getId() + ", not create a new one");
		} finally {
			deleteRecursively(repoDir);
			deleteRecursively(baseDir);
		}
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (var stream = Files.walk(dir)) {
			stream.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException ignored) {
				}
			});
		}
	}
}
