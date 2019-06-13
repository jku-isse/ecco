package at.jku.isse.ecco.adapter.dispatch;

import com.google.inject.AbstractModule;

public class DispatchModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(DispatchReader.class);
		bind(DispatchWriter.class);
	}

}
