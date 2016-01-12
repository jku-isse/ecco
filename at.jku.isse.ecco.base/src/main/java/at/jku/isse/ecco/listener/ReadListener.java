package at.jku.isse.ecco.listener;

import at.jku.isse.ecco.plugin.artifact.ArtifactReader;

import java.nio.file.Path;

public interface ReadListener {

	/**
	 * Fired when a file has been read to create artifacts from it.
	 *
	 * @param file The file that was read.
	 */
	public void fileReadEvent(Path file, ArtifactReader reader);

}
