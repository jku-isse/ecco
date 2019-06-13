package at.jku.isse.ecco.service.listener;

import at.jku.isse.ecco.adapter.ArtifactReader;

import java.nio.file.Path;

public interface ReadListener {

	/**
	 * Fired when a file has been read to create artifacts from it.
	 *
	 * @param file   The file that was read.
	 * @param reader The used reader.
	 */
	public default void fileReadEvent(Path file, ArtifactReader reader) {
		// do nothing
	}

}
