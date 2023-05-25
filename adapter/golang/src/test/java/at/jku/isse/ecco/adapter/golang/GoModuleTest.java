package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.golang.node.EntityFactoryModule;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GoModuleTest {
    @Test
    public void mustBeInstanceOfAbstractModule() {
        assertInstanceOf(AbstractModule.class, new GoModule());
    }

    @Test
    public void configuresMultiBinderForWriter() {
        try {
            final TypeLiteral<Set<ArtifactWriter<Set<Node>, Path>>> setArtifactWriterType = new TypeLiteral<>(){};

            GoModule module = new GoModule();
            Injector injector = Guice.createInjector(new EntityFactoryModule(), module);
            Key<Set<ArtifactWriter<Set<Node>, Path>>> setKey = Key.get(setArtifactWriterType);

            var bindings = injector.getBindings();

            assertTrue(bindings.containsKey(setKey), "GoWriter is not bound to a Multibinder");

            Set<ArtifactWriter<Set<Node>, Path>> writers = injector.getInstance(setKey);

            assertEquals(1, writers.size());
            assertInstanceOf(GoWriter.class, writers.toArray()[0]);
        } catch (ConfigurationException e) {
            fail(e);
        }
    }

    @Test
    public void configuresMultiBinderForReader() {
        try {
            final TypeLiteral<Set<ArtifactReader<Path, Set<Node.Op>>>> artifactReaderSetType = new TypeLiteral<>(){};

            GoModule module = new GoModule();
            Injector injector = Guice.createInjector(new EntityFactoryModule(), module);
            Key<Set<ArtifactReader<Path, Set<Node.Op>>>> setKey = Key.get(artifactReaderSetType);

            var bindings = injector.getBindings();

            assertTrue(bindings.containsKey(setKey), "GoReader is not bound to a Multibinder");

            Set<ArtifactReader<Path, Set<Node.Op>>> readers = injector.getInstance(setKey);

            assertEquals(1, readers.size());
            assertInstanceOf(GoReader.class, readers.toArray()[0]);
        } catch (ConfigurationException e) {
            fail(e);
        }
    }
}
