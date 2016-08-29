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
		parentService.setBaseDir(Paths.get("data/input/"));
		parentService.commit();
		parentService.destroy();

		// create child repo
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("data/forked_repo/.ecco"));
		service.fork(Paths.get("data/parent_repo/.ecco"), "A.1");


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
