package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.service.listener.ExportListener;

public interface ArtifactExporter<I, O> {

	public abstract String getPluginId();

	public abstract O[] export(O base, I input);

	public abstract O[] export(I input);

	public void addListener(ExportListener listener);

	public void removeListener(ExportListener listener);

}
