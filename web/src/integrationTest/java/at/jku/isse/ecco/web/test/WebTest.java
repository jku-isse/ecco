package at.jku.isse.ecco.web.test;

import at.jku.isse.ecco.web.server.EccoWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;

public class WebTest {

	@Test
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
