package at.jku.isse.ecco.plugin;

import at.jku.isse.ecco.plugin.artifact.DispatchReader;
import at.jku.isse.ecco.plugin.artifact.DispatchWriter;
import at.jku.isse.ecco.EccoService;
import com.google.inject.AbstractModule;

public class CoreModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(EccoService.class);

		bind(DispatchReader.class);
		bind(DispatchWriter.class);
	}

}
