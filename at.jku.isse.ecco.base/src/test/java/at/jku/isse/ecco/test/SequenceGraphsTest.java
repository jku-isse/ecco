package at.jku.isse.ecco.test;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class SequenceGraphsTest {


	// align sequence to sg

	// align sg to sg

	// get all orders from sg -> must be merge of all orders that were added before


	@Test(groups = {"unit", "base", "sg"})
	public void SequenceGraphs_Full() {

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
