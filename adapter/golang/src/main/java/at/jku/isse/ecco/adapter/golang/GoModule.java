package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.golang.io.FileSourceWriter;
import at.jku.isse.ecco.adapter.golang.io.SourceWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

public class GoModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        readerMultibinder.addBinding().to(GoReader.class);

        final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        writerMultibinder.addBinding().to(GoWriter.class);

        bind(SourceWriter.class).to(FileSourceWriter.class);
    }
}
