package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.DesignspaceReader;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.DesignspaceWriter;

import at.jku.isse.ecco.adapter.designspace.util.DesignSpaceInfo;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.util.Set;

public class DesignSpaceModule extends AbstractModule {

    public static final String javaAdpaterString = "Java8";
    public static final String generalAdpaterString = "general";
    public static final String generalAdpaterV2String = "generalv2";

    @Inject
    public DesignSpaceModule() {
    }

    @Override
    protected void configure() {
        super.configure();

        final Multibinder<ArtifactReader<DesignSpaceInfo, Set<Node.Op>>> readerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        readerMultibinder.addBinding().to(DesignspaceReader.class);

        final Multibinder<ArtifactWriter<Set<Node>, DesignSpaceInfo>> writerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        writerMultibinder.addBinding().to(DesignspaceWriter.class);
    }


}
