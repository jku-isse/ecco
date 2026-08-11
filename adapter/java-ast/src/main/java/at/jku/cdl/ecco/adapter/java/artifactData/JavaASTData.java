package at.jku.cdl.ecco.adapter.java.artifactData;

import java.io.Serializable;

import at.jku.isse.ecco.artifact.ArtifactData;

public abstract class JavaASTData implements ArtifactData, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ASTNodeType type = ASTNodeType.UNKNOWN;
	
	protected String unformattedString(String str) {
		return str.replaceAll("\t", " ").replaceAll("\n", " ").replaceAll("\r", "");
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
