package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.designspace.sdk.core.operations.Operation;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DesignSpaceModuleTest {
    @Test
    public void mustBeInstanceOfAbstractModule() {
        assertInstanceOf(AbstractModule.class, new DesignSpaceModule());
    }

    @Test
    public void configuresMultiBinderForWriter() {
        try {
            final TypeLiteral<Set<ArtifactWriter<Set<Node>, HashMap<Long, Operation>>>> setArtifactWriterType = new TypeLiteral<>(){};

            DesignSpaceModule module = new DesignSpaceModule();
            Injector injector = Guice.createInjector(new EntityFactoryModule(), module);
            Key<Set<ArtifactWriter<Set<Node>, HashMap<Long, Operation>>>> setKey = Key.get(setArtifactWriterType);

            var bindings = injector.getBindings();

            assertTrue(bindings.containsKey(setKey), "OperationsWriter is not bound to a Multibinder");

            Set<ArtifactWriter<Set<Node>, HashMap<Long, Operation>>> writers = injector.getInstance(setKey);

            assertEquals(1, writers.size());
            assertInstanceOf(OperationsWriter.class, writers.toArray()[0]);
        } catch (ConfigurationException e) {
            fail(e);
        }
    }

    @Test
    public void configuresMultiBinderForReader() {
        try {
            final TypeLiteral<Set<ArtifactReader<Workspace, Set<Node.Op>>>> artifactReaderSetType = new TypeLiteral<>(){};

            DesignSpaceModule module = new DesignSpaceModule();
            Injector injector = Guice.createInjector(new EntityFactoryModule(), module);
            Key<Set<ArtifactReader<Workspace, Set<Node.Op>>>> setKey = Key.get(artifactReaderSetType);

            var bindings = injector.getBindings();

            assertTrue(bindings.containsKey(setKey), "WorkspaceReader is not bound to a Multibinder");

            Set<ArtifactReader<Workspace, Set<Node.Op>>> readers = injector.getInstance(setKey);

            assertEquals(1, readers.size());
            assertInstanceOf(WorkspaceReader.class, readers.toArray()[0]);
        } catch (ConfigurationException e) {
            fail(e);
        }
    }
}
