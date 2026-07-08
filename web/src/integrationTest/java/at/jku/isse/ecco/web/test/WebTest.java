package at.jku.isse.ecco.web.test;

import at.jku.isse.ecco.web.server.EccoWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;

public class WebTest {

	/**
	 * EccoWebServer.main() calls Thread.currentThread().join() with no timeout after starting the
	 * HTTP server - it never returns. Running this as-is hangs the test runner forever. Automating
	 * it for real would need starting the server on a background thread, waiting for it to become
	 * ready, exercising it, then shutting it down - not just an assertion; disabled rather than
	 * left to hang.
	 */
	@Test
	@Disabled("EccoWebServer.main() blocks forever via Thread.currentThread().join() with no shutdown - not automatable as-is")
	public void Web_Test() throws IOException, InterruptedException {
		EccoWebServer.main(new String[]{});
	}

	@BeforeEach
	public void beforeTest() {
		System.out.println("BEFORE");
	}

	@BeforeEach
	public void afterTest() {
		System.out.println("AFTER");
	}

}
