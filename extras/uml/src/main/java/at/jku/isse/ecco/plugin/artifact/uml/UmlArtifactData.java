package at.jku.isse.ecco.plugin.artifact.uml;

import at.jku.isse.ecco.artifact.ArtifactData;

import static com.google.common.base.Preconditions.checkNotNull;

public class UmlArtifactData implements ArtifactData {

	private String type;
	private String id;

	public UmlArtifactData(String id, String type) {
		checkNotNull(id);
		checkNotNull(type);

		this.id = id;
		this.type = type;
	}

	public String getType() {

		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		UmlArtifactData that = (UmlArtifactData) o;

		if (!type.equals(that.type)) return false;
		return id.equals(that.id);

	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + id.hashCode();
		return result;
	}

}
