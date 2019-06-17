package at.jku.isse.ecco.importer;

import java.nio.file.Path;
import java.nio.file.Paths;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;

public class ImporterConsoleInterface {

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("-h")) {
			System.out.println("Help"); //TODO Help message
			return;
		}
		if (args.length != 2) {
			System.out.println("Wrong number of Arguments\n"
					+ "Please enter the path of the repository and the import directory. For more information, enter -h.");
			return;
		}
		Path repPath = Paths.get(args[0]);
		Path fromPath = Paths.get(args[1]);
	
		
		try(EccoService service = new EccoService(repPath)) {	
			if(service.repositoryDirectoryExists())
				service.open();
			else service.init();
			TraceImporter.importFolder(service.getRepository(), fromPath, ".txt");
			service.close();
		} catch (EccoException e) {
			e.printStackTrace();
		}
	}

}
