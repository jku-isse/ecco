package at.jku.isse.ecco.plugin.artifact.php;

import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

/**
 * @author Timea Kovacs
 */
public class PhpModule extends AbstractModule {

	@Override
	protected void configure() {
		// bind(new TypeLiteral<ArtifactReader<File, Set<Node>>>() {
		// }).to(PhpReader.class);
		// bind(new TypeLiteral<ArtifactWriter<Set<Node>, File[]>>() {
		// }).to(PhpWriter.class);

		final Multibinder<ArtifactReader<Path, Set<Node>>> readerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactReader<Path, Set<Node>>>() {
				});
		readerMultibinder.addBinding().to(PhpReader.class);

		final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactWriter<Set<Node>, Path>>() {
				});
		writerMultibinder.addBinding().to(PhpWriter.class);
	}

}
