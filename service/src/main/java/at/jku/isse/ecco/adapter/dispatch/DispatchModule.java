package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.EccoService;
import com.google.inject.AbstractModule;

public class DispatchModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(DispatchReader.class);
		bind(DispatchWriter.class);
	}

}
