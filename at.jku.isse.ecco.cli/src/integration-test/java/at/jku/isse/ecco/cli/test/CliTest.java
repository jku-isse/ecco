package at.jku.isse.ecco.cli.test;

import at.jku.isse.ecco.cli.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;

public class CliTest {

	@Test(groups = {"integration", "cli"})
	public void CLI_Error() {
		System.out.println("ecco --error");
		Main.main(new String[]{"--error"});
	}

	@Test(groups = {"integration", "cli"})
	public void CLI_Help() {
		System.out.println("ecco --help");
		Main.main(new String[]{"--help"});
	}

	@Test(groups = {"integration", "cli"})
	public void CLI_Init() {
		System.out.println("ecco init");
		Main.main(new String[]{"init"});
	}

	@Test(groups = {"integration", "cli"})
	public void CLI_Status() {
		System.out.println("ecco status");
		Main.main(new String[]{"status"});
	}

	@Test(groups = {"integration", "cli"})
	public void CLI_Set_Configuration() {
		System.out.println("ecco set configuration A.10,B.1,C',D");
		Main.main(new String[]{"set", "configuration", "A.10,B.1,C',D"});

		System.out.println("ecco set maxorder 1");
		Main.main(new String[]{"set", "maxorder", "1"});
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
