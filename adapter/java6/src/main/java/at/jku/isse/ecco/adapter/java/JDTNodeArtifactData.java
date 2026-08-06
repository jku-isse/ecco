package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A java artifact that uses the JDT parses and its AST nodes and which can be
 * stored in the perst database.
 *
 * @author JKU-ISSE, Hannes Thaller
 * @version 1.0
 */
public class JDTNodeArtifactData implements ArtifactData, JDTArtifactData {

	private String astNode;
	private String identifier;
	private boolean simpleType;
	private String source;
	private String sourceType;
	private String type;
	private boolean executed = false;

	/**
	 * Constructs a new JDTArtifact with the given <code>astNode</code> as
	 * object. The constructed artifact will not be of a simpleType.
	 *
	 * @param astNode    actual content
	 * @param identifier of the content
	 * @param type       of the content
	 */
	public JDTNodeArtifactData(String astNode, String identifier, String type) {
		this(astNode, identifier, type, false);
	}

	/**
	 * Constructs a new JDTArtifact with the given <code>astNode</code> as
	 * object.
	 *
	 * @param astNode    actual content
	 * @param identifier of the content
	 * @param type       of the content
	 * @param simpleType whether it is a simple content
	 */
	public JDTNodeArtifactData(String astNode, String identifier, String type, boolean simpleType) {
		checkNotNull(astNode);
		checkNotNull(identifier);
		checkNotNull(type);

		this.astNode = astNode;
		this.identifier = identifier;
		this.type = type;
		this.simpleType = simpleType;
	}

	public String getIdentifier() {
		return identifier;
	}

	public Object getObject() {
		return astNode;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		checkNotNull(source);

		this.source = source;
	}

	public void setSourceType(String sourceType) {
		checkNotNull(sourceType);

		this.sourceType = sourceType;
	}

	public String getSourceType() {
		return sourceType;
	}

	public String getType() {
		return type;
	}

	/**
	 * Returns whether the artifact is a simple type.
	 *
	 * @return True if it is a simple, false otherwise.
	 */
	public boolean isSimpleType() {
		return simpleType;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	@Override
	public int hashCode() {
		return Objects.hash(identifier, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		JDTNodeArtifactData other = (JDTNodeArtifactData) obj;
		if (identifier == null) {
			if (other.identifier != null) return false;
		} else if (!identifier.equals(other.identifier)) return false;
		if (type == null) {
			if (other.type != null) return false;
		} else if (!type.equals(other.type)) return false;
		return true;
	}

	@Override
	public String toString() {
		return identifier;
	}

	public String getFile() {
		if (source == null || !source.endsWith(".java") || source.equals("")) {
			return "";
		}
		Path p = Paths.get(source);
		return p.getFileName().toString();
	}

	public boolean isExecuted() {
		return executed;
	}

	public void setExecuted() {
		executed = true;
	}

}
