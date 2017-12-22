package at.jku.isse.ecco.storage.mem.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory implementation of {@link Commit}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseCommit implements Commit {

	private int id;
	private List<Association> associations;
	private String committer;
	private Configuration configuration;
	private List<Association> unmodifiedAssociations;
	private List<Association> newAssociations;
	private List<Association> removedAssociations;

	public BaseCommit() {
		this.associations = new ArrayList<Association>();
		this.unmodifiedAssociations = new ArrayList<Association>();
		this.newAssociations = new ArrayList<Association>();
		this.removedAssociations = new ArrayList<Association>();
	}

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public void setId(int id) {
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
	public List<Association> getAssociations() {
		return this.associations;
	}

	@Override
	public void addAssociation(Association association) {
		this.associations.add(association);
	}

	@Override
	public String getCommiter() {
		return this.committer;
	}

	@Override
	public void setCommitter(String committer) {
		this.committer = committer;
	}


	@Override
	public List<Association> getUnmodified() {
		return this.unmodifiedAssociations;
	}

	@Override
	public void addUnmodified(Association association) {
		this.unmodifiedAssociations.add(association);
	}

	@Override
	public List<Association> getRemoved() {
		return this.removedAssociations;
	}

	@Override
	public void addRemoved(Association association) {
		this.removedAssociations.add(association);
	}

	@Override
	public List<Association> getNew() {
		return this.newAssociations;
	}

	@Override
	public void addNew(Association association) {
		this.newAssociations.add(association);
	}

}
