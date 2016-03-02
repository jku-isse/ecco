package at.jku.isse.ecco.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ServiceTest {

	@Test(groups = {"integration", "service", "init"})
	public void Service_Init_Test() throws EccoException, IOException {
		EccoService service = new EccoService(Paths.get("data/input"), Paths.get("data/repository/.ecco"));

		service.createRepository();

	}

	@Test(groups = {"integration", "service", "commit"})
	public void Service_Commit_Test() throws EccoException, IOException {
		EccoService service = new EccoService(Paths.get("data/input"), Paths.get("data/repository/.ecco"));

		service.createRepository();

		service.commit("aaa");

		for (Association a : service.getAssociations()) {
			System.out.println("A: " + a.getPresenceCondition().toString());
		}
//
//		service.commit("bbb");
//
//		for (Association a : service.getAssociations()) {
//			System.out.println("A: " + a.getPresenceCondition().toString());
//		}

	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
		deleteDatabaseFile();
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
		deleteDatabaseFile();
	}

	private void deleteDatabaseFile() {
		try {
			Files.deleteIfExists(Paths.get("data/repository/.ecco/ecco.db"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			Files.deleteIfExists(Paths.get("data/repository/.ecco"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
