package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.service.listener.ReadListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An ArtifactReader is responsible for transforming a particular type of artifact into an Ecco tree.
 *
 * @param <I> The input to the reader.
 * @param <O> The output of the reader.
 */
public interface ArtifactReader<I, O> {

	//public void setListOfCommitHashes(List<String> commitHashes);

	public void SetGitCommitDetails(String contentOfFile);

	public String getPluginId();


	public Map<Integer, String[]> getPrioritizedPatterns();


	public O read(I base, I[] input) throws IOException;

	public O read(I[] input) throws IOException;


	public void addListener(ReadListener listener);

	public void removeListener(ReadListener listener);

}
