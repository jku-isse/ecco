package at.jku.isse.ecco.gui.test;

import at.jku.isse.ecco.gui.*;
import org.junit.jupiter.api.*;

public class GuiTest {

	/**
	 * EccoGui.main() calls JavaFX's Application.launch(), which blocks the calling thread until
	 * the window is closed - there is no automatic exit. Running this as-is hangs the test runner
	 * indefinitely (and fails outright with no display, e.g. headless CI). Automating it for real
	 * would need a headless UI test harness (e.g. TestFX) driving and then closing the window, not
	 * just an assertion; disabled rather than left to hang.
	 */
	@Test
	@Disabled("EccoGui.main() blocks forever via Application.launch() - not automatable without a headless UI test harness (e.g. TestFX)")
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
