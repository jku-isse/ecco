package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;

import java.util.List;

/**
 * A commit has a unique identifier, information about the committer and references all the associations that were committed or affected by the commit.
 */
public interface Commit extends Persistable {

	/**
	 * The identifier of the commit. This can be anything from an incrementing number to an SHA-1 hash, as long as it uniquely identifies the commit.
	 */
	public int getId();

	public void setId(int id);


	public Configuration getConfiguration();

	public void setConfiguration(Configuration configuration);


	/**
	 * The list of associations that were committed.
	 */
	public List<Association> getAssociations();

	/**
	 * Adds an association to the commit.
	 */
	public void addAssociation(Association association);


	/**
	 * The name of the committer, usually a person's name or email address.
	 */
	public String getCommiter();

	public void setCommitter(String committer);


	public List<Association> getExistingAssociation();

	public void addExistingAssociation(Association association);


	public List<Association> getNewAssociations();

	public void addNewAssociation(Association association);

}
