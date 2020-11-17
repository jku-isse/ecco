package at.jku.isse.ecco.plugin.artifact.uml;

public class UmlAssociationArtifactData extends UmlArtifactData {

	private String name;

	public UmlAssociationArtifactData(String id, String name, String type) {
		super(id, type);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
