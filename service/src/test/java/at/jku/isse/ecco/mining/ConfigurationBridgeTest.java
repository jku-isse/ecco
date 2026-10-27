package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * readConfigurations() used to call tokensOf(commit.getConfiguration()) for every commit
 * unconditionally. A MERGE commit (Repository.extract(Association.Op, Commit), used by
 * Repository.merge() -- and so by EccoService#fork() -- for every merged association) never gets a
 * configuration of its own (unlike the normal commit path, extract(Configuration, Set<Node.Op>,
 * String), which always sets one). tokensOf(null) immediately NPEs on cfg.getFeatureRevisions().
 * Any repository that has ever been forked/pulled/pushed into -- a core, real feature -- crashed here
 * whenever something called readConfigurations() (e.g. ConstraintService#acceptedSuggestions(),
 * wired into EccoService#compose() whenever surplus suppression is enabled).
 */
public class ConfigurationBridgeTest {

	@Test
	@Timeout(30)
	public void readConfigurationsSkipsMergeCommitsInsteadOfThrowing() throws IOException {
		Path workDir = Files.createTempDirectory("configuration-bridge-merge");
		Path repoADir = workDir.resolve("repoA").resolve(".ecco");
		Path repoBDir = workDir.resolve("repoB").resolve(".ecco");
		Files.createDirectories(repoADir.getParent());
		Files.createDirectories(repoBDir.getParent());

		Path xDir = workDir.resolve("x");
		Files.createDirectories(xDir);
		Files.writeString(xDir.resolve("x.txt"), "x\n");

		try (EccoService serviceA = new EccoService()) {
			serviceA.setRepositoryDir(repoADir);
			serviceA.init();
			serviceA.setBaseDir(xDir);
			serviceA.commit("commit x", "X");
			Files.writeString(xDir.resolve("w.txt"), "w\n");
			serviceA.commit("commit x and w", "X, W");

			try (EccoService serviceB = new EccoService()) {
				serviceB.setRepositoryDir(repoBDir);
				// a fork leaving W out goes through Repository.merge() -> extract(Association.Op, Commit)
				// per merged association -- this is what produces null-configuration MERGE commits in B
				// (a fork of everything copies the commits instead, configurations included)
				serviceB.fork(repoADir, "W." + serviceA.getRepository().getFeatures().stream()
						.filter(feature -> feature.getName().equals("W")).findFirst().orElseThrow().getLatestRevision().getId());
				assertTrue(serviceB.getCommits().stream().anyMatch(commit -> commit.getConfiguration() == null), "the fork has merge commits");

				Path yDir = workDir.resolve("y");
				Files.createDirectories(yDir);
				Files.writeString(yDir.resolve("y.txt"), "y\n");
				serviceB.setBaseDir(yDir);
				serviceB.commit("commit y", "Y");

				List<Set<String>> configs = assertDoesNotThrow(() -> ConfigurationBridge.readConfigurations(serviceB),
						"a MERGE commit's null configuration must not crash readConfigurations()");

				assertEquals(1, configs.size(),
						"only the real 'Y' commit has a configuration to contribute -- the merge commit(s) from forking must be skipped");
				assertTrue(configs.get(0).contains("Y"));
			}
		}
	}
}
