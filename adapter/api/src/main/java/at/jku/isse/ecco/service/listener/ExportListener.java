package at.jku.isse.ecco.service.listener;

import at.jku.isse.ecco.adapter.ArtifactExporter;

import java.nio.file.Path;

public interface ExportListener {

	public default void fileExportEvent(Path file, ArtifactExporter exporter) {
		// do nothing
	}

}
