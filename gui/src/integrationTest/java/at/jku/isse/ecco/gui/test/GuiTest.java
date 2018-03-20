package at.jku.isse.ecco.gui.test;

import at.jku.isse.ecco.gui.EccoGui;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class GuiTest {

	@Test(groups = {"integration", "gui"})
	public void Gui_Test() {
		EccoGui.main(new String[]{});
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
