package at.jku.isse.ecco.exporter;

import java.nio.file.Path;
import java.nio.file.Paths;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;

public class ExporterConsoleInterface {

	public static void main(String[] args) { //TODO allow also different RepDir
		if (args.length == 1 && args[0].equals("-h")) {
			System.out.println("Help"); //TODO Help message
			return;
		}
		if (args.length != 2) {
			System.out.println("Wrong number of Arguments\n"
					+ "Please enter the path of the repository and the export directory. For more information, enter -h.");
			return;
		}
		Path repPath = Paths.get(args[0]);
		Path toPath = Paths.get(args[1]);
		try(EccoService service = new EccoService(repPath); ) {			
			service.open();
			TraceExporter.exportAssociations(service.getRepository().getAssociations(), toPath);
		} catch (EccoException e) {
			// ignore Exception, error message is printed in the EccoService
			System.err.println("Repository does not exist.");
		}
	}

}
