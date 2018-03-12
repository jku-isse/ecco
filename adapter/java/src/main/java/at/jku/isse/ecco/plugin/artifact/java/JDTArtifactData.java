package at.jku.isse.ecco.plugin.artifact.java;

public interface JDTArtifactData {

	public String getType();

	public String getIdentifier();

	public String getSource();

	boolean isExecuted();

	void setExecuted();

	String getFile();

}
