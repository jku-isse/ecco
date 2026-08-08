package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/** An unordered (bullet) list, wrapping {@code ListItemArtifactData} children. The bullet marker (e.g. {@code -}, {@code *}, {@code +}) is diagnostic/distinguishing only, not needed for reconstruction. */
public class BulletListArtifactData implements ArtifactData {

	private final String marker;

	public BulletListArtifactData(String marker) {
		this.marker = marker;
	}

	public String getMarker() {
		return this.marker;
	}

	@Override
	public String toString() {
		return "(bullet list, marker=" + this.marker + ")";
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.marker);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		BulletListArtifactData other = (BulletListArtifactData) obj;
		return Objects.equals(this.marker, other.marker);
	}

}
