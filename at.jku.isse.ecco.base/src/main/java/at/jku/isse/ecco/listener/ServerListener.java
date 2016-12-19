package at.jku.isse.ecco.listener;

import at.jku.isse.ecco.EccoService;

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
