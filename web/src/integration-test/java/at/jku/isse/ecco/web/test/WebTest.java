package at.jku.isse.ecco.web.test;

import at.jku.isse.ecco.web.server.EccoWebServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;

public class WebTest {

	@Test(groups = {"integration", "web"})
	public void Web_Test() throws IOException, InterruptedException {
		EccoWebServer.main(new String[]{});
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
