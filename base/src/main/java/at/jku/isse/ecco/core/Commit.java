package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;

import java.util.Collection;
import java.util.Date;

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

	public void setUsername();

	public String getUsername();

	public void setCommitMassage(String message);

	public String getCommitMassage();

	public void setCurrDate();

	public Date getDate();

	public boolean containsAssociation(Association association);

	public void addAssociation(Association association);

	public void deleteAssociation(Association association);

	public Collection<Association> getAssociations();

}
