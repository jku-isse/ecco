package at.jku.isse.ecco.service.listener;

import at.jku.isse.ecco.adapter.ArtifactWriter;

import java.nio.file.Path;

public interface WriteListener {

	/**
	 * Fired when a file has been written from artifacts.
	 *
	 * @param file   The file that was written.
	 * @param writer The used writer.
	 */
	public default void fileWriteEvent(Path file, ArtifactWriter writer) {
		// do nothing
	}

}
