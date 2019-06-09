package at.jku.isse.ecco.uml.test;

import at.jku.isse.ecco.dao.PerstEntityFactory;
import at.jku.isse.ecco.plugin.artifact.uml.UmlReader;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class UmlTest {

	@Test(groups = {"integration", "uml"})
	public void Text_Module_Test() {

		UmlReader reader = new UmlReader(new PerstEntityFactory());
		reader.read(new Path[]{Paths.get("data/input/V1-canvas-line-wipe.uml")});


	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

}
