package at.jku.isse.ecco.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServiceTest {

	private Path outputDir = Paths.get("reports/integrationTest/output");
	private Path repositoryDir = outputDir.resolve("repository");
	private Path inputDir = Paths.get("resources/integrationTest/input");


	@Test(groups = {"integration", "base", "service", "init"})
	public void Init_Test() throws EccoException, IOException {
		EccoService service = new EccoService(inputDir.resolve(Paths.get("V1")), repositoryDir.resolve(Paths.get(".ecco")));

		service.init();

		service.open();
	}

	@Test(groups = {"integration", "base", "service", "commit"})
	public void Commit_Test() throws EccoException, IOException {
		EccoService service = new EccoService(inputDir.resolve(Paths.get("V1")), repositoryDir.resolve(Paths.get(".ecco")));
		service.init();

		System.out.println("Commit 1:");
		service.commit();
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.getPresenceCondition().toString());
		}

		System.out.println("Commit 2:");
		service.setBaseDir(inputDir.resolve(Paths.get("V2")));
		service.commit();
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.getPresenceCondition().toString());
		}

		service.close();
	}


	@Test(groups = {"integration", "base", "service", "fork"})
	public void Fork_Test() throws IOException {
		// create parent repo
		EccoService parentService = new EccoService();
		parentService.setRepositoryDir(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		parentService.init();
		parentService.open();
		parentService.setBaseDir(inputDir.resolve(Paths.get("V1")));
		parentService.commit();
		System.out.println("OUTPUT1:");
		for (Association a : parentService.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.getPresenceCondition().toString());
		}
		parentService.close();

		parentService.open();
		System.out.println("OUTPUT2:");
		for (Association a : parentService.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.getPresenceCondition().toString());
		}
		parentService.close();

		// create child repo
		EccoService service = new EccoService();
		service.setRepositoryDir(outputDir.resolve(Paths.get("forked_repo/.ecco")));
		service.fork(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		System.out.println("OUTPUT3:");
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.getPresenceCondition().toString());
		}
		service.close();
	}


	@Test(groups = {"integration", "base", "service", "pull"})
	public void Pull_Test() throws IOException {
		// create parent repo
		EccoService parentService = new EccoService();
		parentService.setRepositoryDir(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		parentService.init();
		parentService.open();

		// commit first variant to parent
		parentService.setBaseDir(inputDir.resolve(Paths.get("V1")));
		parentService.commit();
		parentService.close();

		// create child repo and fork it from parent
		EccoService service = new EccoService();
		service.setRepositoryDir(outputDir.resolve(Paths.get("forked_repo/.ecco")));
		service.fork(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		service.close();

		System.out.println("---");

		// commit second variant to parent
		parentService = new EccoService();
		parentService.setRepositoryDir(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		parentService.init();
		parentService.open();
		parentService.setBaseDir(inputDir.resolve(Paths.get("V2")));
		parentService.commit();
		System.out.println("OUTPUT1:");
		for (Association a : parentService.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.getPresenceCondition().toString());
		}
		parentService.close();

		System.out.println("---");

		// pull changes from parent to child
		service = new EccoService();
		service.setRepositoryDir(outputDir.resolve(Paths.get("forked_repo/.ecco")));
		service.open();
		service.pull("origin");
		System.out.println("OUTPUT2:");
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.getPresenceCondition().toString());
		}
		service.close();
	}


	@BeforeTest(alwaysRun = true)
	public void beforeTest() throws IOException {
		System.out.println("BEFORE");

		// delete files and directories
		Files.deleteIfExists(this.repositoryDir.resolve(".ecco/ecco.db"));
		Files.deleteIfExists(this.repositoryDir.resolve(".ecco"));

		Files.deleteIfExists(this.outputDir.resolve("parent_repo/.ecco/ecco.db"));
		Files.deleteIfExists(this.outputDir.resolve("parent_repo/.ecco"));
		Files.deleteIfExists(this.outputDir.resolve("forked_repo/.ecco/ecco.db"));
		Files.deleteIfExists(this.outputDir.resolve("forked_repo/.ecco"));

		// create directories
		Files.createDirectories(this.repositoryDir);
		Files.createDirectories(this.outputDir.resolve("parent_repo"));
		Files.createDirectories(this.outputDir.resolve("forked_repo"));
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
