package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.artifact.ArtifactData;

/**
 * The line separator that ends a text file, as the last child of the file's node: traced to features
 * like a line, so each variant ends as it was committed - recorded once per file, the newest commit's
 * final newline was written into every variant (see TextFileFormat).
 */
public class FinalNewlineArtifactData implements ArtifactData {

	private static final long serialVersionUID = 1L;

	@Override
	public boolean equals(Object o) {
		return o instanceof FinalNewlineArtifactData;
	}

	@Override
	public int hashCode() {
		return FinalNewlineArtifactData.class.getName().hashCode();
	}

	@Override
	public String toString() {
		return "(final newline)";
	}
}
