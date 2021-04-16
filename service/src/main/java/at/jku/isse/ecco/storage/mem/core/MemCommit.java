package at.jku.isse.ecco.storage.mem.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * Memory implementation of {@link Commit}.
 */
public class MemCommit implements Commit {

	public static final long serialVersionUID = 1L;


	private String id;
	private String committer;
	private Configuration configuration;
	private Date committingDate;
	private Collection<Association> associations = new ArrayList<>();;


	public MemCommit() {
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public Configuration getConfiguration() {
		return this.configuration;
	}

	@Override
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void setUsername() {
		committer = System.getProperty("user.name"); 	//gets username of the logged in User of the OS
	}

	@Override
	public String getUsername() {
		return committer;
	}

	@Override
	public void setCurrDate() {
		committingDate = new Date();
	}

	@Override
	public Date getDate() {
		return committingDate;
	}

	@Override
	public boolean containsAssociations(final Association association) {
		return associations.contains(association);
	}

	@Override
	public void addAssociations(final Association association) {
		associations.add(association);
	}

	@Override
	public void deleteAssociations(final Association association) {
		associations.remove(association);
	}

	public Collection<Association> getAssociations() {
		return associations;
	}


}
