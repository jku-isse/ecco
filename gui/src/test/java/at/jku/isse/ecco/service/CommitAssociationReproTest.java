package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization test for Repository.extract()'s cross-commit association bookkeeping
 * (the "for origA : originalAssociations { for c : getCommits() { if c.containsAssociation(origA) ... } }"
 * loops), pinning down the exact per-commit artifact counts across a short sequence of commits
 * before replacing the O(n) "scan every commit" lookup with a precomputed reverse index. Kept
 * small (6 commits) since the current implementation is polynomial in commit count - large enough
 * to exercise the bookkeeping (each commit re-touches prior commits' association lists) but far
 * short of where that blowup becomes slow.
 */
public class CommitAssociationReproTest {

	@Test
	@Timeout(30)
	public void extract_sequenceOfCommits_producesExpectedPerCommitArtifactCounts() throws IOException {
		Path base = Files.createTempDirectory("ecco-repro");
		Path repoDir = base.resolve(".ecco");

		EccoService service = new EccoService();
		service.setRepositoryDir(repoDir);
		service.init();

		int numVariants = 6;
		for (int i = 0; i < numVariants; i++) {
			String variant = "v" + i;
			Path variantDir = base.resolve(variant);
			Files.createDirectories(variantDir);

			StringBuilder content = new StringBuilder();
			for (int line = 0; line < 10; line++) {
				content.append("line").append(line).append("\n");
			}
			content.append("unique_to_variant").append(i).append("\n");
			Files.write(variantDir.resolve("file.txt"), content.toString().getBytes());

			service.setBaseDir(variantDir);
			service.commit(variant);
		}

		List<String> summary = summarizeCommits(service);

		assertEquals(List.of(
				"v0: associations=2, totalArtifacts=13",
				"v1: associations=2, totalArtifacts=13",
				"v2: associations=2, totalArtifacts=13",
				"v3: associations=2, totalArtifacts=13",
				"v4: associations=2, totalArtifacts=13",
				"v5: associations=2, totalArtifacts=13"
		), summary);

		service.close();
	}

	private List<String> summarizeCommits(EccoService service) {
		List<String> summary = new ArrayList<>();
		Collection<Commit> commits = service.getCommits();
		for (Commit c : commits) {
			int totalArtifacts = 0;
			for (Association a : c.getAssociations()) {
				totalArtifacts += a.getRootNode().countArtifacts();
			}
			summary.add(c.getCommitMessage() + ": associations=" + c.getAssociations().size() + ", totalArtifacts=" + totalArtifacts);
		}
		return summary;
	}
}
