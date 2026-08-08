package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/** An ordered (numbered) list, wrapping {@code ListItemArtifactData} children. Start number and delimiter (e.g. {@code .} or {@code )}) are diagnostic/distinguishing only, not needed for reconstruction. */
public class OrderedListArtifactData implements ArtifactData {

	private final Integer startNumber;
	private final String delimiter;

	public OrderedListArtifactData(Integer startNumber, String delimiter) {
		this.startNumber = startNumber;
		this.delimiter = delimiter;
	}

	public Integer getStartNumber() {
		return this.startNumber;
	}

	public String getDelimiter() {
		return this.delimiter;
	}

	@Override
	public String toString() {
		return "(ordered list, start=" + this.startNumber + this.delimiter + ")";
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.startNumber, this.delimiter);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		OrderedListArtifactData other = (OrderedListArtifactData) obj;
		return Objects.equals(this.startNumber, other.startNumber) && Objects.equals(this.delimiter, other.delimiter);
	}

}
