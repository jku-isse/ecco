package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/**
 * A namespace, class/struct/union, enum, extern "C" block or function of a C++ file: groups the
 * lines (and nested scopes) it spans, so that variants are aligned scope by scope. It produces no
 * text itself - its first and last lines are ordinary lines inside it (see CppReader).
 */
public class ScopeArtifactData implements ArtifactData {

	private static final long serialVersionUID = 1L;

	public enum Kind {NAMESPACE, CLASS, ENUM, LINKAGE, FUNCTION}

	private final Kind kind;
	private final String signature;

	/**
	 * @param signature identifies the scope among its siblings: e.g. "geo" for a namespace, "class
	 *                  Shape", or a function's return type, name and parameters (whitespace
	 *                  normalized, so reformatting a declaration does not make it a different scope)
	 */
	public ScopeArtifactData(Kind kind, String signature) {
		this.kind = Objects.requireNonNull(kind);
		this.signature = Objects.requireNonNull(signature);
	}

	public Kind getKind() {
		return this.kind;
	}

	public String getSignature() {
		return this.signature;
	}

	@Override
	public String toString() {
		return switch (this.kind) {
			case CLASS -> this.signature; // starts with its key already (class/struct/union)
			case LINKAGE -> "extern " + this.signature;
			default -> this.kind.name().toLowerCase() + " " + this.signature;
		};
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.kind, this.signature);
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj || obj instanceof ScopeArtifactData other && this.kind == other.kind && this.signature.equals(other.signature);
	}
}
