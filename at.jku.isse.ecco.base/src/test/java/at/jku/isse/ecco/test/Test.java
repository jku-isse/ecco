package at.jku.isse.ecco.test;

import java.nio.file.Paths;

public class Test {

	@org.testng.annotations.Test(groups = {"unit", "base"})
	public void Test() {
		System.out.println(Paths.get(".").toAbsolutePath().toString());
	}

}
