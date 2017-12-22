package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkNotNull;

public class DirectoryArtifactData implements ArtifactData {

	private transient Path path = null;
	private String pathString = null;

	protected DirectoryArtifactData() {
		this.path = null;
		this.pathString = null;
	}

	public DirectoryArtifactData(Path path) {
		checkNotNull(path);
//		checkArgument(Files.isDirectory(path));

		this.path = path;
		this.pathString = path.toString();
	}

	public Path getPath() {
		if (this.path == null && this.pathString != null) {
			this.path = Paths.get(this.pathString);
		}
		return this.path;
	}

	public Path getDirectoryName() {
		return this.getPath().getFileName();
	}

	public Path getData() {
		return this.getPath();
	}

	@Override
	public String toString() {
		return this.getPath().toString();
	}

	@Override
	public int hashCode() {
		return this.getPath().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DirectoryArtifactData that = (DirectoryArtifactData) o;

		return this.getPath().equals(that.getPath());
	}

}
