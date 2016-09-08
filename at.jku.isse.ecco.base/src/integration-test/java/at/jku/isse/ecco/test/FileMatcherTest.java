package at.jku.isse.ecco.test;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;

public class FileMatcherTest {

	@Test(groups = {"integration", "base"})
	public void Fork_Test() throws IOException {
		PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:testfolder/testfile.txt");

		Path path = Paths.get("testfolder/testfile.txt");
		Path path2 = Paths.get("testfoldera/testfolder/testfile.txt");

		System.out.println(path + ": " + pm.matches(path));
		System.out.println(path2 + ": " + pm.matches(path2));
	}


	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() throws Exception {
		System.out.println("BEFORE");
	}

}
