package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.lilypond.view.CodeViewer;
import at.jku.isse.ecco.adapter.lilypond.view.ImageViewer;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.nio.file.Path;
import java.util.Set;

public class LilypondModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
        readerMultibinder.addBinding().to(LilypondReader.class);

        final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
        writerMultibinder.addBinding().to(LilypondWriter.class);

        final Multibinder<AssociationInfoArtifactViewer> assInfoMultiBinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<>() {
                });
        assInfoMultiBinder.addBinding().to(CodeViewer.class);

        if (LilypondCompiler.LilypondPath() != null) {
            final Multibinder<ArtifactViewer> viewerMultibinder = Multibinder.newSetBinder(binder(),
                    new TypeLiteral<>() {
                    });
            viewerMultibinder.addBinding().to(ImageViewer.class);
        }
    }
}
