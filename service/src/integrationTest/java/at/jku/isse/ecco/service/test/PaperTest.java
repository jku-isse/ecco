package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PaperTest {

	private Path outputDir = Paths.get("C:\\Users\\user\\Desktop\\vcs_paper_output");
	private Path repositoryDir = outputDir.resolve("repo");
	private Path inputDir = Paths.get("C:\\Users\\user\\Bitbucket\\vcs_features_paper\\journal_extension\\illustrations\\ecco");


	@Test(groups = {"integration", "base", "service", "bugzilla"})
	public void Paper_Test() throws IOException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.init();
		System.out.println("Repository initialized.");
		((Repository.Op) service.getRepository()).setMaxOrder(3);

		// commit before variants
		for (int i = 1; i <= 16; i++) {
			service.setBaseDir(inputDir.resolve(Paths.get("variants_before").resolve("v" + i)));
			service.commit();
		}

		// print repository contents
		System.out.println(this.printRepository(service.getRepository()));

		// checkout 1
		service.setBaseDir(outputDir.resolve(Paths.get("checkout1")));
		service.checkout("BASE.1, SDSORT_USES_RAM.1, SDSORT_CACHE_NAMES.1, HAS_FOLDER_SORTING.1, SDSORT_DYNAMIC_RAM.1");

		// commit 1
		service.setBaseDir(inputDir.resolve(Paths.get("commit1")));
		service.commit();

		// print repository contents
		System.out.println(this.printRepository(service.getRepository()));

		// checkout 2
		service.setBaseDir(outputDir.resolve(Paths.get("checkout2")));
		//service.checkout("BASE.1, SDSORT_CACHE_NAMES.1, HAS_FOLDER_SORTING.1, SDSORT_DYNAMIC_RAM.1");
		service.checkout("BASE.1, SDSORT_DYNAMIC_RAM.1");

		// commit 2
		service.setBaseDir(inputDir.resolve(Paths.get("commit2")));
		service.commit();

		// print repository contents
		System.out.println(this.printRepository(service.getRepository()));

		// close repository
		service.close();
		System.out.println("Repository closed.");
	}


	private String printRepository(Repository repo) {
		Repository.Op repoOp = (Repository.Op) repo;
		Checkout checkout = repoOp.compose(repoOp.getAssociations(), true);
		StringBuilder sb = new StringBuilder();
		sb.append("-------------\n");
		this.visitTree(checkout.getNode(), sb);
		sb.append("-------------\n");
		return sb.toString();
	}

	private void visitTree(Node node, StringBuilder sb) {
		if (node.getArtifact() != null && node.getArtifact().getData() != null && node.getArtifact().getData() instanceof LineArtifactData) {
			sb.append(node).append("\n");
			sb.append(node.getArtifact().getContainingNode().getContainingAssociation().computeCondition().getSimpleModuleConditionString()).append("\n");
			sb.append(node.getArtifact().getContainingNode().getContainingAssociation().computeCondition().getModuleConditionString()).append("\n");
			sb.append("\n");
		}
		for (Node child : node.getChildren())
			this.visitTree(child, sb);
	}


	@BeforeTest(alwaysRun = true)
	public void beforeTest() throws IOException {
		System.out.println("BEFORE");
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
