package at.jku.cdl.ecco.adapter.java.artifactData;

import java.util.Objects;

/**
 * A part of a class that occurs more than once with the same text - two identical initializer blocks
 * ({@code static { x++; }} twice): the second and later get this, numbered, so they differ from the
 * first, which keeps its {@link JavaASTSimpleStringData}. Equal children of the class node, whose
 * children must differ, the file could not be read as Java (gap #64). A class without such repeats
 * is read as before.
 */
public class JavaASTRepeatedData extends JavaASTSimpleStringData {

	private static final long serialVersionUID = 1L;

	/** 1 for the second occurrence of the text, 2 for the third, ... */
	private final int occurrence;

	public JavaASTRepeatedData(String data, int occurrence) {
		super(data);
		if (occurrence < 1)
			throw new IllegalArgumentException("the first occurrence is a JavaASTSimpleStringData: " + occurrence);
		this.occurrence = occurrence;
	}

	public int getOccurrence() {
		return this.occurrence;
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.occurrence);
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj) && ((JavaASTRepeatedData) obj).occurrence == this.occurrence;
	}
}
