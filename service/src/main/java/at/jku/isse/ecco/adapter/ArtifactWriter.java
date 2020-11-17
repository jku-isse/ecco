package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.service.listener.WriteListener;

public interface ArtifactWriter<I, O> {

	public abstract String getPluginId();

	public abstract O[] write(O base, I input);

	public abstract O[] write(I input);

	public void addListener(WriteListener listener);

	public void removeListener(WriteListener listener);

}
