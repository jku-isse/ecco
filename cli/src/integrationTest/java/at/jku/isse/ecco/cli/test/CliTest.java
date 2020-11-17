package at.jku.isse.ecco.cli.test;

import at.jku.isse.ecco.cli.Main;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class CliTest {

	private Path repoDirA = Paths.get("reports/integrationTest/output/repositoryA/.ecco");
	private Path repoDirB = Paths.get("reports/integrationTest/output/repositoryB/.ecco");

	private Path baseDirA = Paths.get("resources/integrationTest/input/variantA");
	private Path baseDirB = Paths.get("resources/integrationTest/input/variantB");
	private Path baseDirC = Paths.get("resources/integrationTest/input/variantC");


	@Test(groups = {"integration", "cli", "error", "help", "init", "status", "remotes", "features", "traces", "dg", "commit", "checkout", "fork", "push", "pull"})
	public void CLI_Full() throws IOException {
		SecurityManager origSM = System.getSecurityManager();

		// error
		System.setSecurityManager(new CliTestSecurityManager(1)); // cli main should exit with status code == 1 when passed wrong parameters
		System.out.println("ecco --error");
		try {
			Main.main(new String[]{"--error"});
		} catch (SuccessException e) {
			// supposed to happen
		}

		//System.setSecurityManager(new CliTestSecurityManager(0)); // cli main should only exit when it fails, and if it does it will always do so with a status code != 0
		System.setSecurityManager(origSM);


		// help
		System.out.println("ecco --help");
		Main.main(new String[]{"--help"});

		// init
		System.out.println("ecco --repodir=" + repoDirA + " init");
		Main.main(new String[]{"--repodir=" + repoDirA, "init"});

		// status
		System.out.println("ecco --repodir=" + repoDirA + " status");
		Main.main(new String[]{"--repodir=" + repoDirA, "status"});


		// commit A to A
		System.out.println("ecco --repodir=" + repoDirA + " --basedir=" + baseDirA + " commit");
		Main.main(new String[]{"--repodir=" + repoDirA, "--basedir=" + baseDirA, "commit"});

		// fork B from A
		System.out.println("ecco --repodir=" + repoDirB + " fork " + repoDirA);
		Main.main(new String[]{"--repodir=" + repoDirB, "fork", repoDirA.toString()});

		// commit B to B
		System.out.println("ecco --repodir=" + repoDirB + " --basedir=" + baseDirB + " commit");
		Main.main(new String[]{"--repodir=" + repoDirB, "--basedir=" + baseDirB, "commit"});

		// commit C to A
		System.out.println("ecco --repodir=" + repoDirA + " --basedir=" + baseDirC + " commit");
		Main.main(new String[]{"--repodir=" + repoDirA, "--basedir=" + baseDirC, "commit"});

		// add B as a remote to A
		System.out.println("ecco --repodir=" + repoDirA + " remotes add repoB " + repoDirB);
		Main.main(new String[]{"--repodir=" + repoDirA, "remotes", "add", "repoB", repoDirB.toString()});

		// list remotes of A
		System.out.println("ecco --repodir=" + repoDirA + " remotes list");
		Main.main(new String[]{"--repodir=" + repoDirA, "remotes", "list"});

		// list remotes of B
		System.out.println("ecco --repodir=" + repoDirB + " remotes list");
		Main.main(new String[]{"--repodir=" + repoDirB, "remotes", "list"});

		// list details of origin remote
		System.out.println("ecco --repodir=" + repoDirB + " remotes show origin");
		Main.main(new String[]{"--repodir=" + repoDirB, "remotes", "show", "origin"});

		// push A to B
		System.out.println("ecco --repodir=" + repoDirA + " push repoB");
		Main.main(new String[]{"--repodir=" + repoDirA, "push", "repoB"});

		// pull B from A
		System.out.println("ecco --repodir=" + repoDirA + " pull repoB");
		Main.main(new String[]{"--repodir=" + repoDirA, "pull", "repoB"});

		// checkout all from A
		System.out.println("ecco --repodir=" + repoDirA + " --basedir=" + repoDirA.getParent() + " checkout BASE, A, B, C");
		Main.main(new String[]{"--repodir=" + repoDirA, "--basedir=" + repoDirA.getParent(), "checkout", "BASE, A, B, C"});

		// checkout all from B
		System.out.println("ecco --repodir=" + repoDirB + " --basedir=" + repoDirB.getParent() + " checkout BASE, A, B, C");
		Main.main(new String[]{"--repodir=" + repoDirB, "--basedir=" + repoDirB.getParent(), "checkout", "BASE, A, B, C"});

		// compare checked out files -> should be equal
		String fileA = Files.readAllLines(repoDirA.getParent().resolve("file.txt")).stream().collect(Collectors.joining("\n"));
		String fileB = Files.readAllLines(repoDirB.getParent().resolve("file.txt")).stream().collect(Collectors.joining("\n"));

		System.out.println(fileA);
		System.out.println("---");
		System.out.println(fileB);

		Assert.assertEquals(fileA, fileB);


		// remotes
		System.out.println("ecco --repodir=" + repoDirA + " remotes list");
		Main.main(new String[]{"--repodir=" + repoDirA, "remotes", "list"});

		// features
		System.out.println("ecco --repodir=" + repoDirA + " features list");
		Main.main(new String[]{"--repodir=" + repoDirA, "features", "list"});

		// traces
		System.out.println("ecco --repodir=" + repoDirA + " traces list");
		Main.main(new String[]{"--repodir=" + repoDirA, "traces", "list"});

		// dg
		System.out.println("ecco --repodir=" + repoDirA + " dg");
		Main.main(new String[]{"--repodir=" + repoDirA, "dg"});


		System.setSecurityManager(origSM);
	}


	@Test(groups = {"integration", "cli", "error"})
	public void CLI_Error() {
		SecurityManager origSM = System.getSecurityManager();

		// error
		System.setSecurityManager(new CliTestSecurityManager(1)); // cli main should exit with status code == 1 when passed wrong parameters
		System.out.println("ecco --error");
		try {
			Main.main(new String[]{"--error"});
		} catch (SuccessException e) {
			// supposed to happen
		}

		System.setSecurityManager(origSM);

	}

	@Test(groups = {"integration", "cli", "help"})
	public void CLI_Help() {
		System.out.println("ecco --help");
		Main.main(new String[]{"--help"});
	}


	// TODO: tests from here on down

//	@Test(groups = {"integration", "cli", "init"})
//	public void CLI_Init() {
//		System.out.println("ecco --repodir=" + repoDirA + " init");
//		Main.main(new String[]{"--repodir=" + repoDirA, "init"});
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Status() {
//		System.out.println("ecco --repodir=" + repoDirA + " status");
//		Main.main(new String[]{"--repodir=" + repoDirA, "status"});
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Properties() {
//		System.out.println("ecco set maxorder 1");
//		Main.main(new String[]{"set", "maxorder", "1"});
//
//		System.out.println("ecco get maxorder");
//		Main.main(new String[]{"get", "maxorder"});
//
//		System.out.println("ecco set basedir /test/");
//		Main.main(new String[]{"set", "basedir", "/test/"});
//
//		System.out.println("ecco get basedir");
//		Main.main(new String[]{"get", "basedir"});
//	}
//
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Commit() {
//		System.out.println("ecco commit A.10,B.1,C',D");
//		Main.main(new String[]{"commit"});
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Checkout() {
//
//	}
//
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Fork() {
//
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Pull() {
//
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Push() {
//
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Fetch() {
//
//	}
//
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Remotes() {
//		System.out.println("ecco remotes list");
//
//		Main.main(new String[]{"init"});
//		Main.main(new String[]{"remotes", "list"});
//		Main.main(new String[]{"remotes", "add", "local", "/test/"});
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Features() {
//		System.out.println("ecco features list");
//		Main.main(new String[]{"features", "list"});
//	}
//
//	@Test(groups = {"integration", "cli"})
//	public void CLI_Traces() {
//		System.out.println("ecco traces list");
//		Main.main(new String[]{"traces", "list"});
//	}


	@BeforeTest(alwaysRun = true)
	public void beforeTest() throws IOException {
		System.out.println("BEFORE");

		// delete files and directories
		Files.deleteIfExists(this.repoDirA.resolve("ecco.db"));
		Files.deleteIfExists(this.repoDirA);
		Files.deleteIfExists(this.repoDirA.getParent().resolve("file.txt"));
		Files.deleteIfExists(this.repoDirA.getParent().resolve(".config"));
		Files.deleteIfExists(this.repoDirA.getParent().resolve(".warnings"));

		Files.deleteIfExists(this.repoDirB.resolve("ecco.db"));
		Files.deleteIfExists(this.repoDirB);
		Files.deleteIfExists(this.repoDirB.getParent().resolve("file.txt"));
		Files.deleteIfExists(this.repoDirB.getParent().resolve(".config"));
		Files.deleteIfExists(this.repoDirB.getParent().resolve(".warnings"));

		// create directories
		Files.createDirectories(this.repoDirA.getParent());
		Files.createDirectories(this.repoDirB.getParent());
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() throws IOException {
		System.out.println("AFTER");
	}

//	private void deleteRepository() {
//		File dbFile = new File("./.ecco/ecco.db");
//		if (dbFile.exists() && !dbFile.delete()) {
//			System.out.println("Could not delete the database file.");
//		}
//		File repoDir = new File("./.ecco/");
//		if (repoDir.exists() && !repoDir.delete()) {
//			System.out.println("Could not delete the repository directory.");
//		}
//	}

}
