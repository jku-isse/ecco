package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

public class CModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactReader<Path, Set<Node.Op>>>() {});
        readerMultibinder.addBinding().to(CReader.class);

        final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactWriter<Set<Node>, Path>>() {});
        writerMultibinder.addBinding().to(CWriter.class);
    }

}
