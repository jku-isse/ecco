package at.jku.isse.ecco.plugin.emf;

import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import java.nio.file.Path;
import java.util.Set;

/**
 * Created by hhoyos on 18/05/2017.
 */
public class EmfModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactReader<Path, Set<Node.Op>>>() {
                });
        readerMultibinder.addBinding().to(EmfReader.class);
        final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactWriter<Set<Node>, Path>>() {
                });
        writerMultibinder.addBinding().to(EmfWriter.class);
        final Multibinder<ArtifactViewer> viewerMultibinder = Multibinder.newSetBinder(binder(),
                new TypeLiteral<ArtifactViewer>() {
                });
        viewerMultibinder.addBinding().to(EmfViewer.class);
    }

    /**
     * Make the resource set a singleton so it is shared by all EMF tasks (readers, writers, viewers) in the ECCO
     * application.
     * @return
     */
    @Provides @Singleton
    ResourceSet provideResourceSet() {
        return new ResourceSetImpl();
    }

}
