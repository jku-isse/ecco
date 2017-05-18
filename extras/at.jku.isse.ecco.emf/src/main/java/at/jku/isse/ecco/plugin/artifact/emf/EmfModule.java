package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import org.eclipse.emf.common.util.URI;

import java.util.Set;

/**
 * Created by hhoyos on 18/05/2017.
 */
public class EmfModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<ArtifactReader<URI, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactReader<URI, Set<Node.Op>>>() {
                });
        readerMultibinder.addBinding().to(EmfReader.class);

//        final Multibinder<ArtifactWriter<Set<Node>, URI>> writerMultibinder = Multibinder.newSetBinder(binder(),
//                new TypeLiteral<ArtifactWriter<Set<Node>, URI>>() {
//                });
//        writerMultibinder.addBinding().to(EmfWriter.class);
//        final Multibinder<ArtifactViewer> viewerMultibinder = Multibinder.newSetBinder(binder(),
//                new TypeLiteral<ArtifactViewer>() {
//                });
//        viewerMultibinder.addBinding().to(EmfViewer.class);
    }

}
