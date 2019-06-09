package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.listener.ReadListener;

import java.util.Map;

/**
 * An ArtifactReader is responsible for transforming a particular type of artifact into an Ecco tree.
 *
 * @param <I> The input to the reader.
 * @param <O> The output of the reader.
 */
public interface ArtifactReader<I, O> {

	public String getPluginId();


//	public String[] getTypeHierarchy(); // this should be abstract static which java unfortunately does not support...
//
//	public boolean canRead(I input);


	public Map<Integer, String[]> getPrioritizedPatterns();


	public O read(I base, I[] input);

	public O read(I[] input);


	public void addListener(ReadListener listener);

	public void removeListener(ReadListener listener);

}
