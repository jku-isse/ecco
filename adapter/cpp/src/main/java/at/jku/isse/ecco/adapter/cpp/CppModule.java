package at.jku.isse.ecco.adapter.cpp;


import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

public class CppModule extends AbstractModule {

	@Override
	protected void configure() {
		final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactReader<Path, Set<Node.Op>>>() {
				});
		readerMultibinder.addBinding().to(CppReader.class);

		final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactWriter<Set<Node>, Path>>() {
				});
		writerMultibinder.addBinding().to(CppWriter.class);

	}

}
