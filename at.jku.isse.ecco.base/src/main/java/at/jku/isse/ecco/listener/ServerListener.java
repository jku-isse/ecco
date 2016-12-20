package at.jku.isse.ecco.listener;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.ProgressInputStream;

public interface ServerListener extends ProgressInputStream.ProgressListener {

	public default void serverEvent(EccoService service, String message) {
		// do nothing
	}

	public default void serverStartEvent(EccoService service, int port) {
		// do nothing
	}

	public default void serverStopEvent(EccoService service) {
		// do nothing
	}


	@Override
	public default void progress(double progress) {
		// do nothing
	}

}
