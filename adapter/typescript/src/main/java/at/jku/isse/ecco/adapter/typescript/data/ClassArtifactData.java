package at.jku.isse.ecco.adapter.typescript.data;

import java.util.Objects;

public class ClassArtifactData extends AbstractArtifactData {

    private final String classDecl;


    public ClassArtifactData(String block) {
        this.classDecl = block;
    }

    public String getClassDecl() {
        return this.classDecl;
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
