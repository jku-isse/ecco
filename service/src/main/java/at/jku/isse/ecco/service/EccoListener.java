package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.ReadListener;
import at.jku.isse.ecco.adapter.WriteListener;
import at.jku.isse.ecco.core.Association;

public interface EccoListener extends ReadListener, WriteListener, ServerListener {

	public default void statusChangedEvent(EccoService service) {
		// do nothing
	}

	public default void associationSelectedEvent(EccoService service, Association association) {
		// do nothing
	}

	public default void operationProgressEvent(EccoService service, String operationString, double progress) {
		// do nothing
	}

}
