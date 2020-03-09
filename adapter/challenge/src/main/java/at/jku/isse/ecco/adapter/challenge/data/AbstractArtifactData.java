package at.jku.isse.ecco.adapter.challenge.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class AbstractArtifactData implements ArtifactData {

	private String id;
	private String source;
	private boolean executed = false;

	public AbstractArtifactData(String name) {
		this.id = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String name) {
		this.id = name;
	}

	@Override
	public String toString() {
		return this.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		//if (getClass() != obj.getClass())
		//	return false;
		//AbstractArtifactData other = (AbstractArtifactData) obj;
		if (id == null) {
			//if (other.id != null)
			if (obj.toString() != null)
				return false;
			//} else if (!id.equals(other.id))
		}else if (!id.equals(obj.toString().replace(" at.jku.isse.ecco.adapter.runtime.data","at.jku.isse.ecco.adapter.challenge.data")))
			return false;
		return true;
	}

	public boolean isExecuted() {
		return executed;
	}

	public void setExecuted() {
		executed = true;
	}

	public String getFile() {
		if (source == null || !source.endsWith(".java") || source.equals("")) {
			return "";
		}
		Path p = Paths.get(source);
		return p.getFileName().toString();
	}


}
