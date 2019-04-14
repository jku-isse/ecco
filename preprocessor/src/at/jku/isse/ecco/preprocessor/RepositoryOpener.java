package at.jku.isse.ecco.preprocessor;

import java.nio.file.Path;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.repository.Repository;

public class RepositoryOpener { //TODO allow different RepDir

	public static Repository openRepository(Path repPath) {
		EccoService service = new EccoService(repPath); 
		service.open();
		return service.getRepository();		
	}
}
