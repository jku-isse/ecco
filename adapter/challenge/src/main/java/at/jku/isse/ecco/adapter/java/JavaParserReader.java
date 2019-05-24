package at.jku.isse.ecco.adapter.java;


import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.modules.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitor;

public class JavaParserReader implements VoidVisitor<Void> {


    public JavaParserReader() {

    }

    public interface JavaParReader {
        boolean handle(Node node);
    }

    private JavaParReader nodeHandler;


    public JavaParserReader(JavaParReader nodeHandler) {
        this.nodeHandler = nodeHandler;
    }

    public void explore(Node node) {
            for (Node child : node.getChildNodes()) {
                System.out.println(node);
                explore(child);

            }
    }


    @Override
    public void visit(NodeList n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(AnnotationDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(AnnotationMemberDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ArrayAccessExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ArrayCreationExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ArrayCreationLevel n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ArrayInitializerExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ArrayType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(AssertStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(AssignExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(BinaryExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(BlockComment n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(BlockStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(BooleanLiteralExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(BreakStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(CastExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(CatchClause n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(CharLiteralExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ClassExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ClassOrInterfaceType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(CompilationUnit n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ConditionalExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ConstructorDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ContinueStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(DoStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(DoubleLiteralExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(EmptyStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(EnclosedExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(EnumConstantDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(EnumDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ExplicitConstructorInvocationStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ExpressionStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(FieldAccessExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(FieldDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ForeachStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(IfStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ImportDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(InitializerDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(InstanceOfExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(IntegerLiteralExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(IntersectionType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(JavadocComment n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(LabeledStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(LambdaExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(LineComment n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(LocalClassDeclarationStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(LongLiteralExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(MarkerAnnotationExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(MemberValuePair n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(MethodReferenceExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(NameExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(Name n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(NormalAnnotationExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(NullLiteralExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(PackageDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(Parameter n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(PrimitiveType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ReturnStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(SimpleName n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(SingleMemberAnnotationExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(StringLiteralExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(SuperExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(SwitchEntryStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(SwitchStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(SynchronizedStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ThisExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ThrowStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(TryStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(TypeExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(TypeParameter n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(UnaryExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(UnionType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(UnknownType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(VariableDeclarationExpr n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(VariableDeclarator n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(VoidType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(WildcardType n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ModuleDeclaration n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ModuleRequiresStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ModuleExportsStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ModuleProvidesStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ModuleUsesStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ModuleOpensStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(UnparsableStmt n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(ReceiverParameter n, Void arg) {
        System.out.println(n.toString());
    }

    @Override
    public void visit(VarType n, Void arg) {
        System.out.println(n.toString());
    }
}
