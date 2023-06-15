package at.jku.isse.ecco.gui.test;

import at.jku.isse.ecco.gui.*;
import org.junit.jupiter.api.*;

public class GuiTest {

	@Test
	public void Gui_Test() {
		EccoGui.main(new String[]{});
	}

	@AfterEach
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeEach
	public void beforeTest() {
		System.out.println("BEFORE");
	}

}
