package at.jku.isse.ecco.test;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

import java.io.File;

public abstract class EccoIntegrationTest {


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
		File dbFile = new File("./.ecco/ecco.db");
		if (dbFile.exists() && !dbFile.delete()) {
			System.out.println("Could not delete the database file.");
		}
		File repoDir = new File("./.ecco/");
		if (repoDir.exists() && !repoDir.delete()) {
			System.out.println("Could not delete the repository directory.");
		}
	}


}
