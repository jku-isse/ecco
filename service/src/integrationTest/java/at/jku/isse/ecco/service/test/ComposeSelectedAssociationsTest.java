package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Characterizes the GUI's "Compose Selected" button (ArtifactsView.java): builds a
 * LazyCompositionRootNode from every selected association's root node and walks it, exactly what
 * the button's Task does. Reported by the user as failing in the GUI after reload, alongside the
 * Commit tab bug (CommitAssociationConsistencyTest) - same commit/reload shape, different code
 * path (LazyCompositionNode.activate() -> DefaultOrderSelector.select(), not
 * EccoService.checkout()/mainTree).
 */
public class ComposeSelectedAssociationsTest {

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
	public void composeAllAssociations_afterReload() throws ResourceException, IOException {
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

			LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
			for (Association association : eccoService.getRepository().getAssociations()) {
				rootNode.addOrigNode(association.getRootNode());
			}

			// fully walk/activate the composed tree, exactly what rendering it in the GUI's
			// ArtifactTreeView would do
			Deque<Node> stack = new ArrayDeque<>();
			stack.push(rootNode);
			int visited = 0;
			while (!stack.isEmpty()) {
				Node node = stack.pop();
				visited++;
				for (Node child : node.getChildren()) {
					stack.push(child);
				}
			}
			System.out.println("Composed tree: visited " + visited + " nodes");
		}
	}
}