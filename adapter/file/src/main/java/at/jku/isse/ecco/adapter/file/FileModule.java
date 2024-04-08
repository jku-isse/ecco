package at.jku.isse.ecco.adapter.file;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

public class FileModule extends AbstractModule {

	@Override
	protected void configure() {
		final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
		readerMultibinder.addBinding().to(FileReader.class);

		final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
		writerMultibinder.addBinding().to(FileWriter.class);

		final Multibinder<ArtifactViewer> viewerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
		viewerMultibinder.addBinding().to(FileViewer.class);
	}

}
