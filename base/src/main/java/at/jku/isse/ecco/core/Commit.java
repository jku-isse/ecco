package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;

/**
 * A commit has a unique identifier, information about the committer and references all the associations that were committed or affected by the commit.
 */
public interface Commit extends Persistable {

	/**
	 * The identifier of the commit. This can be anything from an incrementing number to an SHA-1 hash, as long as it uniquely identifies the commit.
	 *
	 * @return The identifier of the commit.
	 */
	public String getId();

	public void setId(String id);


	public Configuration getConfiguration();

	public void setConfiguration(Configuration configuration);

}
