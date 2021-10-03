package at.jku.isse.ecco.adapter.runtime.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class ClassArtifactData implements ArtifactData {

	private String name, classDeclaration, annotation, comment, javadoc;

	public ClassArtifactData(String name) {
		this.name = name;
	}

	public ClassArtifactData(String name, String classDeclaration) {
		this.name = name;
		this.classDeclaration = classDeclaration;
	}

	public ClassArtifactData(String name, String classDeclaration, String annotation, String comment) {
		this.name = name;
		this.classDeclaration = classDeclaration;
		this.annotation = annotation;
		this.comment = comment;
	}

	public ClassArtifactData(String name, String classDeclaration, String annotation, String comment, String javadoc) {
		this.name = name;
		this.classDeclaration = classDeclaration;
		this.annotation = annotation;
		this.comment = comment;
		this.javadoc = javadoc;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getClassDeclaration() { return classDeclaration; }

	public void setClassDeclaration(String classDeclaration){
		this.classDeclaration = classDeclaration;
	}

	public String getAnnotation() {
		return annotation;
	}

	public void setAnnotation(String annotation) {
		this.annotation = annotation;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getJavadoc() {
		return javadoc;
	}

	public void setJavadoc(String javadoc) {
		this.javadoc = javadoc;
	}

	@Override
	public String toString() {
		return this.name;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name);
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
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
