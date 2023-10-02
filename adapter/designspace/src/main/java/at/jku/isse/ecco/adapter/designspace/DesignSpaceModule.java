package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.*;
import at.jku.isse.designspace.sdk.core.operations.*;
import at.jku.isse.ecco.adapter.*;
import at.jku.isse.ecco.tree.*;
import com.google.inject.*;
import com.google.inject.multibindings.*;

import java.util.*;

public class DesignSpaceModule extends AbstractModule {

    @Inject
    public DesignSpaceModule() {    }

    @Override
    protected void configure() {
        super.configure();

        final Multibinder<ArtifactReader<Workspace, Set<Node.Op>>> readerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        readerMultibinder.addBinding().to(WorkspaceReader.class);

        final Multibinder<ArtifactWriter<Set<Node>, HashMap<Long, Operation>>> writerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        writerMultibinder.addBinding().to(OperationsWriter.class);
    }
}
