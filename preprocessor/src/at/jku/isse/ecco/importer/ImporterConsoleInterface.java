package at.jku.isse.ecco.importer;

import java.nio.file.Path;
import java.nio.file.Paths;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.dao.TransactionStrategy.TRANSACTION;
import at.jku.isse.ecco.repository.Repository.Op;
import at.jku.isse.ecco.txt.TextFileLineImporter;

public class ImporterConsoleInterface {

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("-h")) {
			System.out.println("Please enter the path of the repository as first parameter and the directory which should be imported as second parameter.");
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
			new TraceImporterV2(service, fromPath, repPath, ".txt", new TextFileLineImporter()).importFolder();
			service.commit();
			service.close();
		} catch (EccoException e) {
			e.printStackTrace();
		}
	}

}
