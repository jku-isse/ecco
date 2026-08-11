package at.jku.cdl.ecco.adapter.java.artifactData;

import java.io.Serializable;

import at.jku.isse.ecco.artifact.ArtifactData;

public abstract class JavaASTData implements ArtifactData, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ASTNodeType type = ASTNodeType.UNKNOWN;
	
	/**
	 * Used to collapse a captured fragment's tabs/newlines to spaces, which silently corrupted any
	 * construct where a newline is semantically or syntactically load-bearing - most notably text
	 * blocks, whose value IS its internal line structure, and whose opening delimiter requires a
	 * newline immediately after it. Now just normalizes line endings to plain "\n" (still needed
	 * since JavaParser's printer output could otherwise carry CRLF depending on platform) without
	 * discarding any of the original content.
	 */
	protected String unformattedString(String str) {
		return str.replace("\r\n", "\n").replace("\r", "\n");
	}
	
	public void setType(ASTNodeType type) {
		if(type != null) {
			this.type = type;
		} else {
			throw new IllegalArgumentException("Type can not be null!");
		}
	}
	
	public ASTNodeType getType() {
		return type;
	}

	@Override
	public String toString() {
		return type.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JavaASTData other = (JavaASTData) obj;
		if (type != other.type)
			return false;
		return true;
	}
	
	
	
}
