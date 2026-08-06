package at.jku.isse.ecco.adapter.java.jdtast;

import org.eclipse.jdt.core.dom.*;

public class NeedsMoreDetailASTVisitor extends ASTVisitor {
    private boolean found = false;

    public boolean lambdaFound() {
        return found;
    }

    @Override
    public boolean visit(LambdaExpression node) {
        return setFound();
    }

    @Override
    public boolean visit(DoStatement node) {
        return setFound();
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        return setFound();
    }

    @Override
    public boolean visit(ForStatement node) {
        return setFound();
    }

    @Override
    public boolean visit(IfStatement node) {
        return setFound();
    }

    @Override
    public boolean visit(SwitchStatement node) {
        return setFound();
    }

    @Override
    public boolean visit(TryStatement node) {
        return setFound();
    }

    @Override
    public boolean visit(VariableDeclarationFragment node) {
        return setFound();
    }

    @Override
    public boolean visit(WhileStatement node) {
        return setFound();
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
        return setFound();
    }


    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        return setFound();
    }

    private boolean setFound() {
        found = true;
        return false;
    }
}