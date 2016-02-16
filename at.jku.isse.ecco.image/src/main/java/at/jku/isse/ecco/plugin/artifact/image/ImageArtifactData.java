package at.jku.isse.ecco.plugin.artifact.image;

import java.util.Arrays;
import java.util.Objects;

// TODO: preserve image type (and settings like global background color, and metadata!)

public class ImageArtifactData {

	private final int[] values;

	private String identifier;
	private String type;

	protected ImageArtifactData() {
		this.values = null;
		this.identifier = null;
		this.type = null;
	}

	public ImageArtifactData(final int[] values, final String type) {
		this.values = values;
		this.identifier = Arrays.toString(values);
		this.type = type;
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

	public int[] getValues() {
		return this.values;
	}

	public String getType() {
		return this.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(values);
	}

	@Override
	public String toString() {
		return this.identifier;
	}

}
