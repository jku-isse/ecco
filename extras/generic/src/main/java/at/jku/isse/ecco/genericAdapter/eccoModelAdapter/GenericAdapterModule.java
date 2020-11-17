package at.jku.isse.ecco.genericAdapter.eccoModelAdapter;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder.GenericAdapterReader;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.printer.GenericAdapterWriter;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

/**
 * @author Michael Jahn
 */
public class GenericAdapterModule extends AbstractModule {


    @Override
    protected void configure() {

        final Multibinder<ArtifactReader<Path, Set<Node>>> readerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactReader<Path, Set<Node>>>() {
                });
        readerMultibinder.addBinding().to(GenericAdapterReader.class);

        final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactWriter<Set<Node>, Path>>() {
                });
        writerMultibinder.addBinding().to(GenericAdapterWriter.class);

    }
}
