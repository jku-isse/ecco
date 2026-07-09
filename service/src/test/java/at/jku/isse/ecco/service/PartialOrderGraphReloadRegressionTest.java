package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression test for a real bug found while investigating a checkout-correctness report against a
 * user's actual repository (see memory/lytiny-treefusion-duplicate-token-bug and
 * memory/pog-reload-headtail-wiring-bug): closing and reopening a repository between two commits -
 * ordinary usage any time the GUI/CLI is restarted between commits, not an edge case - used to throw
 * {@code EccoException("Not all partial order graph nodes can be reached...")} on the second commit.
 * <p>
 * Root cause: {@code SerPartialOrderGraph.readObject()} wired a reloaded POG's head/tail boundary
 * edges by checking {@code n.getArtifact() != null} to tell a real content node apart from the tail
 * sentinel. That worked when {@code SerPartialOrderGraphNode.artifact} was an ordinary field, but it
 * became transient (resolved later by {@code SerTransactionStrategy}'s post-load pass, not by this
 * object's own {@code readObject()}) when artifacts were made independently-persisted, deduplicated
 * entities. At the point this check runs, artifact is always still null - for every node, real or
 * not - so the check always evaluated false and head/tail were silently never wired to their real
 * neighbors after a reload, corrupting the graph's reachability count. Fixed by checking node
 * identity against the known sentinels instead of artifact presence.
 * <p>
 * Committing all variants in one continuous session (no reopen in between) never exercised this,
 * which is why the existing {@link LilypondVariantsCommitCheckoutTest} didn't catch it - this test
 * specifically forces a close/reopen between two commits to match real (non-single-session) usage.
 */
public class PartialOrderGraphReloadRegressionTest {

	private static final Path EXAMPLES_DIR = LilypondVariantsCommitCheckoutTest.findRepoRoot()
			.resolve("examples").resolve("lilypond_variants");

	@Test
	@Timeout(30)
	public void secondCommitAfterReopen_doesNotThrow() throws IOException {
		Path repoDir = Files.createTempDirectory("pog-reload-regression").resolve(".ecco");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();
			service.setBaseDir(EXAMPLES_DIR.resolve("v1_setup"));
			service.commit("v1_setup", "setup.1");
		}

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.open();
			service.setBaseDir(EXAMPLES_DIR.resolve("v2_setup_notes"));
			// used to throw: "Not all partial order graph nodes can be reached (this indicates a
			// cycle or an orphan node without parent)!"
			service.commit("v2_setup_notes", "setup.1, notes.1");

			Path checkoutDir = Files.createTempDirectory("pog-reload-regression-checkout");
			service.setBaseDir(checkoutDir);
			service.checkout("setup.1, notes.1");
		}
	}
}
