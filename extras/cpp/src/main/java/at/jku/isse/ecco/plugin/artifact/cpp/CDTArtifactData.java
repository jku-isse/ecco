package at.jku.isse.ecco.plugin.artifact.cpp;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.eclipse.cdt.core.dom.ast.ASTNodeProperty;

public class CDTArtifactData implements ArtifactData {

	private static final long serialVersionUID = 1L;

	private String astNode;

	private String identifier;

	private String type;

	private ASTNodeProperty propertyInParent;


	private String source;

	private String sourceType;

	public CDTArtifactData(String astNode, String identifier, String type, ASTNodeProperty propertyInParent) {
		super();
		this.astNode = astNode;
		this.identifier = identifier;
		this.type = type;
		this.propertyInParent = propertyInParent;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void updateIdentifier(String ident) {
		this.identifier = ident;
	}

	public String getType() {
		return type;
	}

	public ASTNodeProperty getPropertyInParent() {
		return propertyInParent;
	}

	public String getSource() {
		return this.source;
	}

	public String getSourceType() {
		return this.sourceType;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public String getObject() {
		return astNode;
	}

	@Override
	public String toString() {
		return identifier + " [" + type + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
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
		CDTArtifactData other = (CDTArtifactData) obj;
		if (identifier == null) {
			if (other.identifier != null)
				return false;
		} else if (!identifier.equals(other.identifier))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}
}
