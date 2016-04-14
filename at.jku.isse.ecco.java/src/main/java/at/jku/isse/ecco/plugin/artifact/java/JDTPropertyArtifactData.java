package at.jku.isse.ecco.plugin.artifact.java;

import at.jku.isse.ecco.plugin.artifact.ArtifactData;

import java.util.Objects;

public class JDTPropertyArtifactData implements ArtifactData, JDTArtifactData {

	private String identifier;
	private boolean mandatory;
	private String source;
	private String sourceType;
	private String structuralProperty;
	private String type;

	public JDTPropertyArtifactData(String structuralProperty, String identifier, String type, boolean mandatory) {
		super();
		this.structuralProperty = structuralProperty;
		this.identifier = identifier;
		this.type = type;
		this.mandatory = mandatory;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		JDTPropertyArtifactData other = (JDTPropertyArtifactData) obj;
		if (identifier == null) {
			if (other.identifier != null) return false;
		} else if (!identifier.equals(other.identifier)) return false;
		if (type == null) {
			if (other.type != null) return false;
		} else if (!type.equals(other.type)) return false;
		return true;
	}

	public String getIdentifier() {
		return identifier;
	}

	public String getObject() {
		return this.structuralProperty;
	}

	public String getSource() {
		return this.source;
	}

	public String getSourceType() {
		return this.sourceType;
	}

	public String getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(identifier, type);
	}

	public boolean isMandatory() {
		return mandatory;
	}

	public void setIdentifier(String ident) {
		this.identifier = ident;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	@Override
	public String toString() {
		return identifier + " [" + type + "]";
	}

}
