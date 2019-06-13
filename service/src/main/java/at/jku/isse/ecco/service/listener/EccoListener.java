package at.jku.isse.ecco.service.listener;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;

public interface EccoListener extends ReadListener, WriteListener, ServerListener {

	public default void statusChangedEvent(EccoService service) {
		// do nothing
	}

	public default void commitsChangedEvent(EccoService service, Commit commit) {
		// do nothing
	}

	public default void associationSelectedEvent(EccoService service, Association association) {
		// do nothing
	}

	public default void operationProgressEvent(EccoService service, String operationString, double progress) {
		// do nothing
	}

}
