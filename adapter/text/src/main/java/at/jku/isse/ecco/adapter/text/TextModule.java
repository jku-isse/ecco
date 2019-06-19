package at.jku.isse.ecco.adapter.text;

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

public class TextModule extends AbstractModule {

	@Override
	protected void configure() {
		// bind(new TypeLiteral<ArtifactReader<File, Set<Node>>>() {
		// }).to(TextReader.class);
		// bind(new TypeLiteral<ArtifactWriter<Set<Node>, File[]>>() {
		// }).to(TextWriter.class);

		final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactReader<Path, Set<Node.Op>>>() {
				});
		readerMultibinder.addBinding().to(TextReader.class);

		final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactWriter<Set<Node>, Path>>() {
				});
		writerMultibinder.addBinding().to(TextFileWriter.class);

		final Multibinder<ArtifactViewer> viewerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactViewer>() {
				});
		viewerMultibinder.addBinding().to(TextViewer.class);

		final Multibinder<ArtifactExporter<Set<Node>, Path>> exporterMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactExporter<Set<Node>, Path>>() {
				});
		exporterMultibinder.addBinding().to(TextExporter.class);
	}

}
