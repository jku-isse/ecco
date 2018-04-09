package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.tree.RootNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

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
//		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V2_stripedshirt"));
//		service.commit();
//		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V3_purpleshirt_jacket"));
//		service.commit();

		System.out.println("Commits done.");

		Collection<Path> paths = new ArrayList<>();
		paths.add(Paths.get("person.png"));
		RootNode mapped = service.map(paths);

		System.out.println("Map done.");

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void Test2() throws EccoException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\ECCO_TEST\\.ecco"));
		service.init();
		System.out.println("Repository initialized.");

		// commit variants to the new repository
		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\map_test_variants\\v1"));
		service.commit();
		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\map_test_variants\\v2"));
		service.commit();
//		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\map_test_variants\\v3"));
//		service.commit();

		System.out.println("Commits done.");

		Collection<Path> paths = new ArrayList<>();
		paths.add(Paths.get("t.txt"));
		RootNode mapped = service.map(paths);

		System.out.println("Map done.");

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void Test3() throws EccoException, IOException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\ECCO_TEST\\.ecco"));
		service.init();
		System.out.println("Repository initialized.");

		// commit variants to the new repository
		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\ecco_map_base"));
		service.commit();

		System.out.println("Commits done.");

		//Collection<Path> paths = new ArrayList<>();
		Collection<Path> paths = Files.walk(service.getBaseDir()).filter(p -> {
			return !p.getFileName().toString().startsWith(".");
		}).filter(p -> !p.equals(service.getBaseDir())).collect(Collectors.toList());
		paths = paths.stream().map(p -> service.getBaseDir().relativize(p)).collect(Collectors.toList());

		RootNode mapped = service.map(paths);

		System.out.println("Map done.");

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

}
