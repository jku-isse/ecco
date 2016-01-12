package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;

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

}
