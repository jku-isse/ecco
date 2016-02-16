package at.jku.isse.ecco.plugin.artifact;

import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;

public class DirectoryArtifactData implements ArtifactData {

	private Path path;

	protected DirectoryArtifactData() {
		this.path = null;
	}

	public DirectoryArtifactData(Path path) {
		checkNotNull(path);
//		checkArgument(Files.isDirectory(path));

		this.path = path;
	}

	public Path getPath() {
		return this.path;
	}

	public Path getDirectoryName() {
		return this.path.getFileName();
	}

	public Path getData() {
		return this.path;
	}

	@Override
	public String toString() {
		return this.path.toString();
	}

	@Override
	public int hashCode() {
		return this.path.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DirectoryArtifactData that = (DirectoryArtifactData) o;

		return path.equals(that.path);

	}

}
