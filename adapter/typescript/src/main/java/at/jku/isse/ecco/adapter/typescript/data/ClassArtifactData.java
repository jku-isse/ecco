package at.jku.isse.ecco.adapter.typescript.data;

import java.util.Objects;

public class ClassArtifactData extends AbstractArtifactData {

    private static final long serialVersionUID = 4239417938195510632L;

    private final String classDecl;


    public ClassArtifactData(String block) {
        this.classDecl = block;
    }

    public String getClassDecl() {
        return this.classDecl;
    }

    /**
     * What closes the class after its last member, as written ("\n}", "\n} // end"): its trailing
     * comment. An empty class has its braces in its declaration, and nothing after it (null). A class
     * stored before this was recorded has "" and is closed with "\n}", as it always was.
     * @return The text after the last member
     */
    public String getClosing() {
        String closing = this.getTrailingComment();
        if (closing == null) return "";
        return closing.isEmpty() ? "\n}" : closing;
    }

    @Override
    public String toString() {
        return this.classDecl;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.classDecl);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClassArtifactData other = (ClassArtifactData) obj;
        if (classDecl == null) {
            return other.classDecl == null;
        } else return classDecl.equals(other.classDecl);
    }

}
