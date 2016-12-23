package at.jku.isse.ecco.listener;

import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;

import java.nio.file.Path;

public interface WriteListener {

	/**
	 * Fired when a file has been written from artifacts.
	 *
	 * @param file The file that was written.
	 */
	public default void fileWriteEvent(Path file, ArtifactWriter writer) {
		// do nothing
	}

}
