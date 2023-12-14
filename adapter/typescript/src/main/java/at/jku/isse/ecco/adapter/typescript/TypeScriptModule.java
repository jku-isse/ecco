package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.ArtifactExporter;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

public class TypeScriptModule extends AbstractModule {

	@Override
	protected void configure() {
		// bind(new TypeLiteral<ArtifactReader<File, Set<Node>>>() {
		// }).to(TextReader.class);
		// bind(new TypeLiteral<ArtifactWriter<Set<Node>, File[]>>() {
		// }).to(TextWriter.class);

		final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
		readerMultibinder.addBinding().to(TypeScriptReader.class);

		final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
		writerMultibinder.addBinding().to(TypeScriptWriter.class);

	}

}
