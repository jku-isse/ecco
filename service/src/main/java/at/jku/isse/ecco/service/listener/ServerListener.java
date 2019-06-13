package at.jku.isse.ecco.service.listener;

import at.jku.isse.ecco.service.EccoService;

public interface ServerListener {

	public default void serverEvent(EccoService service, String message) {
		// do nothing
	}

	public default void serverStartEvent(EccoService service, int port) {
		// do nothing
	}

	public default void serverStopEvent(EccoService service) {
		// do nothing
	}

}
