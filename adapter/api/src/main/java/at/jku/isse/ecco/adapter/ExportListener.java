package at.jku.isse.ecco.adapter;


import java.nio.file.Path;

public interface ExportListener {

	public default void fileExportEvent(Path file, ArtifactExporter exporter) {
		// do nothing
	}

}
