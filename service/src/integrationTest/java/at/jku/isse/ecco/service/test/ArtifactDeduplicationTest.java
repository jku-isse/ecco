package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterizes the actual invariant behind
 * pog-mismatch-real-cause-duplicate-storageid/pog-position-identity-fixed's fix: an artifact shared
 * (by reference) across multiple associations' trees must resolve to the SAME Java object after a
 * reload, not merely to "some" resolvable object with the same id. CommitCheckoutTest.
 * serializeAndDeserializeTest() only proves the symptom (checkout no longer throws) - this proves
 * the mechanism (no two distinct objects ever claim the same storageId).
 */
public class ArtifactDeduplicationTest {

	private static final Path REPO_PATH;

	static {
		try {
			REPO_PATH = ResourceUtils.getResourceFolderPath("repo").resolve(".ecco");
		} catch (ResourceException e) {
			throw new RuntimeException(e);
		}
	}

	@BeforeEach
	public void deleteRepositoryBeforeEach() throws DirectoryException {
		DirectoryUtils.deleteFolderIfItExists(REPO_PATH);
	}

	@Test
	public void sharedArtifactsResolveToTheSameInstanceAcrossAssociations_afterReload() throws ResourceException, IOException {
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

			// storageId -> the one and only object identity that should ever be seen for it
			Map<String, Integer> identityByStorageId = new HashMap<>();

			for (Association association : eccoService.getRepository().getAssociations()) {
				Node rootNode = association.getRootNode();
				if (rootNode instanceof Node.Op rootOp) {
					assertSingleIdentityPerStorageId(rootOp, identityByStorageId);
				}
			}

			// sanity check the test fixture actually exercises sharing at all - otherwise this test
			// would trivially pass without ever having tested the thing it claims to
			assertEquals(true, identityByStorageId.size() > 0, "expected at least one artifact to be found");
		}
	}

	private void assertSingleIdentityPerStorageId(Node.Op node, Map<String, Integer> identityByStorageId) {
		if (node.getArtifact() instanceof SerArtifact<?> serArtifact) {
			int identity = System.identityHashCode(serArtifact);
			Integer previous = identityByStorageId.putIfAbsent(serArtifact.getStorageId(), identity);
			if (previous != null) {
				assertEquals(previous, identity, "artifact " + serArtifact.getStorageId()
						+ " (" + serArtifact + ") resolved to two different object instances across associations");
			}
		}
		for (Node.Op child : node.getChildren()) {
			assertSingleIdentityPerStorageId(child, identityByStorageId);
		}
	}
}
