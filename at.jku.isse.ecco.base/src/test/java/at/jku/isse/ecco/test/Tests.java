package at.jku.isse.ecco.test;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.UUID;

public class Tests {

	@Test(groups = {"unit", "base", "uuid"})
	public void UUID_Test() {
		UUID id = UUID.randomUUID();
		System.out.println("ID: " + id.toString());
		System.out.println("HASH: " + id.hashCode());
	}

	@Test(groups = {"unit", "base", "pathmatcher"})
	public void PathMatcher_Test() throws IOException {
		PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:testfolder/testfile.txt");

		Path path = Paths.get("testfolder/testfile.txt");
		Path path2 = Paths.get("testfoldera/testfolder/testfile.txt");

		System.out.println(path + ": " + pm.matches(path));
		System.out.println(path2 + ": " + pm.matches(path2));
	}


	@Test(groups = {"unit", "base", "server"})
	public void Server_Test() {

	}

	@Test(groups = {"unit", "base", "client"})
	public void Client_Test() {

	}


}
