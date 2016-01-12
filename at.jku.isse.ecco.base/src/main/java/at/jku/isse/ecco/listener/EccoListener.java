package at.jku.isse.ecco.listener;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;

public interface EccoListener extends ReadListener, WriteListener {

	public void statusChangedEvent(EccoService service);

	public void commitsChangedEvent(EccoService service, Commit commit);

}
