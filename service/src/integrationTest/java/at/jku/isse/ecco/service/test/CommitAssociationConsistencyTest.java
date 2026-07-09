package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Characterizes the "Commit Tab doesn't properly show Details on an Association" bug report:
 * after a second, overlapping commit, Repository.extract() splits/replaces the FIRST commit's
 * original association with a new intersection association, updating the first commit's own
 * association references via Commit.addAssociation()/deleteAssociation() (Repository.java, the
 * commitsByAssociation bookkeeping). SerCommit.getAssociations() now resolves those references by
 * ID against SerRepository's live association map, and silently drops any ID it can't resolve -
 * unlike the pre-ID-indirection SerCommit, which held direct object references that stayed valid
 * regardless of what happened to the repository's own top-level association collection.
 */
public class CommitAssociationConsistencyTest {

	private static final Path REPO_PATH;

	static {
		try {
			REPO_PATH = ResourceUtils.getResourceFolderPath("repo").resolve(".ecco");
		} catch (ResourceException e) {
			throw new RuntimeException(e);
		}
	}

	@BeforeAll
	public static void deleteRepositoryBefore() throws DirectoryException {
		DirectoryUtils.deleteFolderIfItExists(REPO_PATH);
	}

	@BeforeEach
	public void deleteRepositoryBeforeEach() throws DirectoryException {
		DirectoryUtils.deleteFolderIfItExists(REPO_PATH);
	}

	@AfterAll
	public static void deleteRepositoryAfter() throws DirectoryException {
		DirectoryUtils.deleteFolderIfItExists(REPO_PATH);
	}

	@Test
	public void firstCommitStillHasResolvableAssociationsAfterSecondOverlappingCommit_liveSession() throws ResourceException, IOException {
		try (EccoService eccoService = new EccoService()) {
			Path repositoryPath = ResourceUtils.getResourceFolderPath("repo");
			eccoService.setRepositoryDir(repositoryPath.resolve(".ecco"));
			eccoService.init();

			Path variant1Path = ResourceUtils.getResourceFolderPath("input/V1");
			Path variant2Path = ResourceUtils.getResourceFolderPath("input/V2");
			eccoService.setBaseDir(variant1Path);
			Commit firstCommit = eccoService.commit();
			eccoService.setBaseDir(variant2Path);
			eccoService.commit();

			// re-fetch via the repository/commit list, same as the GUI's Commit tab would, rather
			// than trusting the in-hand `firstCommit` reference
			for (Commit commit : eccoService.getCommits()) {
				var associations = commit.getAssociations();
				assertFalse(associations.isEmpty(), "commit " + commit.getId() + " has no resolvable associations");
				for (Association association : associations) {
					assertNotNull(association.computeCondition(), "association " + association.getId() + " has no condition");
				}
			}
		}
	}

	@Test
	public void firstCommitStillHasResolvableAssociationsAfterSecondOverlappingCommit_afterReload() throws ResourceException, IOException {
		Path repositoryPath = ResourceUtils.getResourceFolderPath("repo");
		Path variant1Path = ResourceUtils.getResourceFolderPath("input/V1");
		Path variant2Path = ResourceUtils.getResourceFolderPath("input/V2");

		try (EccoService eccoService = new EccoService()) {
			eccoService.setRepositoryDir(repositoryPath.resolve(".ecco"));
			eccoService.init();
			eccoService.setBaseDir(variant1Path);
			eccoService.commit();
			eccoService.setBaseDir(variant2Path);
			eccoService.commit();
		}

		try (EccoService eccoService = new EccoService()) {
			eccoService.setRepositoryDir(repositoryPath.resolve(".ecco"));
			eccoService.open();

			for (Commit commit : eccoService.getCommits()) {
				var associations = commit.getAssociations();
				assertFalse(associations.isEmpty(), "commit " + commit.getId() + " has no resolvable associations after reload");
				for (Association association : associations) {
					assertNotNull(association.computeCondition(), "association " + association.getId() + " has no condition after reload");
				}
			}
		}
	}
}
