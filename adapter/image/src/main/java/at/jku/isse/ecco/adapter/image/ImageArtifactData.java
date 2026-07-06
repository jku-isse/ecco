package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Arrays;

public class ImageArtifactData implements ArtifactData {

	private final int[] values;

	private String type;

	protected ImageArtifactData() {
		this.values = null;
		this.type = null;
	}

	public ImageArtifactData(final int[] values, final String type) {
		this.values = values;
		this.type = type;
	}

	public int[] getValues() {
		return this.values;
	}

	public String getType() {
		return this.type;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ImageArtifactData other = (ImageArtifactData) obj;
		return Arrays.equals(values, other.values);
	}

	@Override
	public int hashCode() {
		// must stay content-based and consistent with equals() above: Objects.hash(values) would
		// hash the int[] by identity (arrays don't override hashCode()), breaking the
		// hashCode/equals contract and silently defeating any hash-based artifact lookup
		return Arrays.hashCode(values);
	}

	@Override
	public String toString() {
		return Arrays.toString(this.values);
	}

}
