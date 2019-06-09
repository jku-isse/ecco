package at.jku.isse.ecco.adapter.challenge.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class MethodArtifactData implements ArtifactData {

	private String signature;

	public MethodArtifactData(String signature) {
		this.signature = signature;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	@Override
	public String toString() {
		return this.signature;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.signature);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MethodArtifactData other = (MethodArtifactData) obj;
		if (signature == null) {
			if (other.signature != null)
				return false;
		} else if (!signature.equals(other.signature))
			return false;
		return true;
	}

}
