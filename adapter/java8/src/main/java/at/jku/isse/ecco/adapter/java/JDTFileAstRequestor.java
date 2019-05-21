package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.jdtast.Jdt2JavaAstVisitor;
import at.jku.isse.ecco.adapter.java.jdtast.ReferencedAstVisitor;
import at.jku.isse.ecco.adapter.java.jdtast.ReferencingAstVisitor;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.google.common.io.Files;
import org.eclipse.jdt.core.dom.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JDTFileAstRequestor extends FileASTRequestor {
    private final EntityFactory entityFactory;
    private final Path sourcesPath;
    private final Set<Node.Op> nodeSet = new HashSet<>();
    private final Map<IBinding, Artifact.Op<JavaTreeArtifactData>> referenced = new IdentityHashMap<>();
    private final List<Pair> referencing = new LinkedList<>();

    public Map<IBinding, Artifact.Op<JavaTreeArtifactData>> getReferenced() {
        return referenced;
    }

    public List<Pair> getReferencing() {
        return referencing;
    }

    public JDTFileAstRequestor(EntityFactory entityFactory, Path sourcesPath) {
        Objects.requireNonNull(entityFactory);
        this.entityFactory = entityFactory;
        this.sourcesPath = sourcesPath;
    }

    @Override
    public void acceptAST(String sourceFileName, CompilationUnit cu) {
        super.acceptAST(sourceFileName, cu);
        // Create the artifact data for the  file itself
        JavaFileArtifactData artifactData = new JavaFileArtifactData();
        artifactData.setClassName(getCanonicalClassName(sourceFileName, cu));

        final Artifact.Op<JavaFileArtifactData> javaFileArtifact = createArtifact(artifactData);

        //Not needed? checkForReferences(javaFileArtifact, cu);

        Node.Op classNode = entityFactory.createOrderedNode(javaFileArtifact);


        Path sourceFilePath = sourcesPath.toAbsolutePath();
        Path javaFilePath = Paths.get(sourceFileName);

        Artifact.Op<PluginArtifactData> pluginArtifactData = entityFactory.createArtifact(
                new PluginArtifactData(JavaPlugin.getPluginIdStatic(),
                        sourceFilePath.relativize(javaFilePath)
                )
        );
        final Node.Op pluginNode = entityFactory.createOrderedNode(pluginArtifactData);

        pluginNode.addChild(classNode);
        nodeSet.add(pluginNode);

        readAst(cu, classNode);
    }

    private void readAst(ASTNode jdtNode, Node.Op parentEccoNode) {
        if (jdtNode == null) return;
        Jdt2JavaAstVisitor jdt2JavaAstVisitor = new Jdt2JavaAstVisitor(parentEccoNode
                , javaTreeArtifactData -> newNode(entityFactory.createArtifact(javaTreeArtifactData)),
                (artifact, astNode) -> checkForReferences((Artifact.Op<JavaTreeArtifactData>) artifact, astNode), this::readAst
        );
        jdtNode.accept(jdt2JavaAstVisitor);
    }


    public String getCanonicalClassName(String sourceFileName, CompilationUnit cu) {
        final Optional<PackageDeclaration> packageDeclaration = Optional.ofNullable(cu.getPackage());
        String className = Files.getNameWithoutExtension(sourceFileName);
        // package.name + "."+ className
        //maps add the dot between package and classname
        return packageDeclaration.map(PackageDeclaration::getName).map(Object::toString).map(s -> s + ".").orElse("") + className;
    }

    public Set<Node.Op> getNodes() {
        return Collections.unmodifiableSet(nodeSet);
    }

    private <T extends ArtifactData> Artifact.Op<T> createArtifact(T artifactData) {
        return entityFactory.createArtifact(artifactData);
    }

    private void setArtifactPosition(Artifact.Op<?> artifact, int start, int line, int column) {
        artifact.putProperty("pos", start);
        artifact.putProperty("line", line);
        artifact.putProperty("col", column);
    }

    public void checkForReferences(Artifact.Op<JavaTreeArtifactData> artifact, ASTNode astNode) {
        if (astNode == null || artifact == null) return;
        // We have 6 types here in Java 8, one additional in Java 9. See: org.eclipse.jdt.core.dom.IBinding
        ReferencingAstVisitor<JavaTreeArtifactData> referencingAstVisitor = new ReferencingAstVisitor<>(artifact);
        ReferencedAstVisitor<JavaTreeArtifactData> referencedVisitor = new ReferencedAstVisitor<>(artifact, referenced);

        astNode.accept(referencingAstVisitor);
        referencing.addAll(referencingAstVisitor.getReferencingPairs());
        astNode.accept(referencedVisitor);
    }

    private Node.Op newNode(Artifact.Op<JavaTreeArtifactData> artifact) {
        boolean b = artifact.getData().isOrdered();
        return b ? entityFactory.createOrderedNode(artifact) : entityFactory.createNode(artifact);
    }

    public static class Pair {
        private final IBinding binding;
        private final Artifact.Op<?> artifact;

        public Pair(IBinding binding, Artifact.Op<?> artifact) {
            this.binding = binding;
            this.artifact = artifact;
        }

        public Pair(Artifact.Op<?> artifact, IBinding binding) {
            this.binding = binding;
            this.artifact = artifact;
        }

        public IBinding getBinding() {
            return binding;
        }

        public Artifact.Op<?> getArtifact() {
            return artifact;
        }
    }
}
