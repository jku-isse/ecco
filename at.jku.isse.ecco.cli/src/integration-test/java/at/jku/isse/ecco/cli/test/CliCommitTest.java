package at.jku.isse.ecco.cli.test;

import java.io.File;

import at.jku.isse.ecco.cli.Main;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class CliCommitTest {

	@Test(groups = {"integration", "cli"})
	public void CLI_Commit() {
		System.out.println("ecco init");
		Main.main(new String[]{"init"});

//		System.out.println("ecco add");
//		Main.main(new String[]{"add"});

		System.out.println("ecco commit A.10,B.1,C',D");
		Main.main(new String[]{"commit", "A.10,B.1,C',D"});
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
