package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.eclipse.jdt.core.BindingKey;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

public class JavaReader implements ArtifactReader<Path, Set<Node.Op>> {
    private final EntityFactory entityFactory;
    private static Map<Integer, String[]> prioritizedPatterns = new HashMap();

    @Inject
    public JavaReader(EntityFactory entityFactory) {
        this.entityFactory = entityFactory;
    }

    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    @Override
    public String getPluginId() {
        return JavaPlugin.class.getName();
    }

    static {
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.java"});
    }

    @Override
    public Set<Node.Op> read(final Path base, Path[] input) {
        Consumer<FileASTRequestor> fileASTRequestorConsumer = createNewParser(base, input);
        JDTFileAstRequestor fileAstRequestor = new JDTFileAstRequestor(entityFactory, base);

        fileASTRequestorConsumer.accept(fileAstRequestor);

        resolveReferences(fileAstRequestor);
        return fileAstRequestor.getNodes();
    }

    private void resolveReferences(JDTFileAstRequestor fileAstRequestor) {
        final List<JDTFileAstRequestor.Pair> referencing = fileAstRequestor.getReferencing();
        final Map<IBinding, Artifact.Op<JavaTreeArtifactData>> referenced = fileAstRequestor.getReferenced();
        for (JDTFileAstRequestor.Pair pair : referencing) {
            final Artifact.Op<JavaTreeArtifactData> reference = referenced.get(pair.getBinding());
            if (reference != null) {
                pair.getArtifact().addUses(reference);
            }
        }
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return read(Paths.get("."), input);
    }


    // Is unused!
    private Collection<ReadListener> readListeners = new LinkedList<>();

    @Override
    public void addListener(ReadListener listener) {
        readListeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        readListeners.remove(listener);
    }

    public static final String MAXIMUM_SUPPORTED_JAVA_VERSION = JavaCore.VERSION_1_8;
    public static final int AST_VERSION = AST.JLS9;

    public Consumer<FileASTRequestor> createNewParser(Path sourceFolderPath, Path[] sourceFiles) {
        String sourceFolder = sourceFolderPath.toString();
        String[] classpath = new String[]{System.getProperty("java.class.path")};

        final ASTParser parser = ASTParser.newParser(AST_VERSION);
        //Configure parser
        parser.setEnvironment(classpath, new String[]{sourceFolder}, new String[]{"UTF-8"}, true);

        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);

        parser.setKind(ASTParser.K_COMPILATION_UNIT); // Always parse whole java files

        final Hashtable<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, MAXIMUM_SUPPORTED_JAVA_VERSION);
        options.put(JavaCore.COMPILER_SOURCE, MAXIMUM_SUPPORTED_JAVA_VERSION);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, MAXIMUM_SUPPORTED_JAVA_VERSION);

        parser.setCompilerOptions(options);

        final String[] bindingKeys = Arrays.stream(sourceFiles)
                .map(Object::toString)
                .map(BindingKey::createTypeBindingKey)
                .toArray(String[]::new);

        sourceFolderPath = sourceFolderPath.toAbsolutePath();

        final String[] sourcePathnames = Arrays.stream(sourceFiles)
                .map(sourceFolderPath::resolve)
                .map(Object::toString)
                .toArray(String[]::new);

        return astRequestor -> parser.createASTs(sourcePathnames, null, bindingKeys, astRequestor, null);
    }
}
