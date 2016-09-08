package at.jku.isse.ecco.test;

import at.jku.isse.ecco.EccoService;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ForkTest {

	@Test(groups = {"integration", "base"})
	public void Fork_Test() throws IOException {
		// create parent repo
		EccoService parentService = new EccoService();
		parentService.setRepositoryDir(Paths.get("data/parent_repo/.ecco"));
		parentService.createRepository();
		parentService.init();
		parentService.setBaseDir(Paths.get("data/input/V1/"));
		parentService.commit();
		parentService.destroy();

		// create child repo
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("data/forked_repo/.ecco"));
		service.fork(Paths.get("data/parent_repo/.ecco"));
	}


	@Test(groups = {"integration", "base"})
	public void Pull_Test() throws IOException {
		// create parent repo
		EccoService parentService = new EccoService();
		parentService.setRepositoryDir(Paths.get("data/parent_repo/.ecco"));
		parentService.createRepository();
		parentService.init();

		// commit first variant to parent
		parentService.setBaseDir(Paths.get("data/input/V1/"));
		parentService.commit();
		parentService.destroy();

		// create child repo and fork it from parent
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("data/forked_repo/.ecco"));
		service.fork(Paths.get("data/parent_repo/.ecco"));

		System.out.println("---");

		// commit second variant to parent
		parentService = new EccoService();
		parentService.setRepositoryDir(Paths.get("data/parent_repo/.ecco"));
		parentService.createRepository();
		parentService.init();
		parentService.setBaseDir(Paths.get("data/input/V2/"));
		parentService.commit();
		parentService.destroy();

		System.out.println("---");

		// pull changes from parent to child
		service.pull("origin");

		service.destroy();
	}


	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() throws Exception {
		System.out.println("BEFORE");

		Files.deleteIfExists(Paths.get("data/parent_repo/.ecco/ecco.db"));
		Files.deleteIfExists(Paths.get("data/parent_repo/.ecco"));

		Files.deleteIfExists(Paths.get("data/forked_repo/.ecco/ecco.db"));
		Files.deleteIfExists(Paths.get("data/forked_repo/.ecco"));
	}

}
