package at.jku.isse.ecco.listener;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;

public interface ServiceListener extends ReadListener, WriteListener, ServerListener {

	public default void statusChangedEvent(EccoService service) {
		// do nothing
	}

	public default void commitsChangedEvent(EccoService service, Commit commit) {
		// do nothing
	}

	public default void associationSelectedEvent(EccoService service, Association association) {
		// do nothing
	}

}
