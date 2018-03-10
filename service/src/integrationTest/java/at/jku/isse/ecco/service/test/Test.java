package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;

import java.nio.file.Paths;

public class Test {

	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void Test() throws EccoException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("/home/user/Desktop/ECCO_TEST/REPO/.ecco"));
		service.init();
		System.out.println("Repository initialized.");

		// commit variants to the new repository
		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V1_purpleshirt"));
		service.commit();
		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V2_stripedshirt"));
		service.commit();
		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V3_purpleshirt_jacket"));
		service.commit();

		System.out.println("Commits done.");

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

}
