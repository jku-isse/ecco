package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.listener.ReadListener;

/**
 * An ArtifactReader is responsible for transforming a particular type of artifact into an Ecco tree.
 *
 * @param <I>
 * @param <O>
 */
public interface ArtifactReader<I, O> {

	public String getPluginId();

	public String[] getTypeHierarchy(); // this should be abstract static which java unfortunately does not support...

	public boolean canRead(I input);

	public O read(I base, I[] input);

	public O read(I[] input);

	public void addListener(ReadListener listener);

	public void removeListener(ReadListener listener);

}
