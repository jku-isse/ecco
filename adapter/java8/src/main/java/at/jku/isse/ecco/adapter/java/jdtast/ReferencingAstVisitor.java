package at.jku.isse.ecco.adapter.java.jdtast;

import at.jku.isse.ecco.adapter.java.JDTFileAstRequestor;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import org.eclipse.jdt.core.dom.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ReferencingAstVisitor<T extends ArtifactData> extends SingleJDTNodeAstVisitor {

    private Artifact.Op<T> artifactOp;
    private List<JDTFileAstRequestor.Pair> referencingPairs = new LinkedList<>();

    public List<JDTFileAstRequestor.Pair> getReferencingPairs() {
        return referencingPairs;
    }

    public ReferencingAstVisitor(Artifact.Op<T> artifactOp) {
        Objects.requireNonNull(artifactOp);
        this.artifactOp = artifactOp;
    }

    @Override
    public boolean visit(ArrayType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(IntersectionType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(NameQualifiedType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(ParameterizedType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(PrimitiveType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(QualifiedType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(SimpleType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(UnionType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(WildcardType node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(QualifiedName node) {
        visitName(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SimpleName node) {
        visitName(node);
        return super.visit(node);
    }

    private void visitName(Name name) {
        addBinding(name.resolveBinding());
        addBinding(name.resolveTypeBinding());
    }

    @Override
    public boolean visit(LambdaExpression node) {
        addBinding(node.resolveMethodBinding());
        addBinding(node.resolveTypeBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        addBinding(node.resolveConstructorBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(MethodInvocation node) {
        addBinding(node.resolveMethodBinding());
        addBinding(node.resolveTypeBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        addBinding(node.resolveMethodBinding());
        addBinding(node.resolveTypeBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        addBinding(node.resolveConstructorBinding());
        addBinding(node.resolveConstructorBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayAccess node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayCreation node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ArrayInitializer node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(Assignment node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(BooleanLiteral node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(CastExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(CharacterLiteral node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ConditionalExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(CreationReference node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ExpressionMethodReference node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldAccess node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(InfixExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(InstanceofExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(MethodRef node) {
        addBinding(node.resolveBinding());
        return super.visit(node);
    }


    @Override
    public boolean visit(NullLiteral node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(NumberLiteral node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ParenthesizedExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(PostfixExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(PrefixExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(StringLiteral node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperFieldAccess node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodReference node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ThisExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeLiteral node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(TypeMethodReference node) {
        visitExpression(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        visitExpression(node);
        return super.visit(node);
    }

    private void visitExpression(Expression e) {
        addBinding(e.resolveTypeBinding());
    }


    private void addBinding(IBinding iBinding) {
        referencingPairs.add(new JDTFileAstRequestor.Pair(artifactOp, iBinding));
    }
}
