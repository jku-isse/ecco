package at.jku.isse.ecco.adapter.java.jdtast;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import org.eclipse.jdt.core.dom.*;

import java.util.Map;

public class ReferencedAstVisitor<T extends ArtifactData> extends SingleJDTNodeAstVisitor {

    private final Artifact.Op<T> underlyingArtifact;
    private final Map<IBinding, Artifact.Op<T>> referenced;

    public ReferencedAstVisitor(Artifact.Op<T> underlyingArtifact, Map<IBinding, Artifact.Op<T>> referenced) {
        this.underlyingArtifact = underlyingArtifact;
        this.referenced = referenced;
    }

    public Map<IBinding, Artifact.Op<T>> getReferenced() {
        return referenced;
    }

    public boolean visit(PackageDeclaration astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    public boolean visit(TypeDeclaration astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    public boolean visit(AnonymousClassDeclaration astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        addBinding(node.resolveTypeBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(VariableDeclarationFragment node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    public boolean visit(MethodDeclaration astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    public boolean visit(AnnotationTypeDeclaration astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    public boolean visit(AnnotationTypeMemberDeclaration astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    public boolean visit(EnumDeclaration astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    public boolean visit(TypeParameter astNode) {
        addBinding(astNode.resolveBinding());
        return super.visit(astNode);
    }

    public boolean visit(MemberValuePair astNode) {
        addBinding(astNode.resolveMemberValuePairBinding());
        return super.visit(astNode);
    }


    public boolean visit(EnumConstantDeclaration astNode) {
        addBinding(astNode.resolveVariable());
        return super.visit(astNode);
    }

    private void addBinding(IBinding binding) {
        if (binding != null)
            referenced.put(binding, underlyingArtifact);
    }

}