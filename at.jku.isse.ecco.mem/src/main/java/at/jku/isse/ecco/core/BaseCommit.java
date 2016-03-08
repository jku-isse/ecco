package at.jku.isse.ecco.core;

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

	public BaseCommit() {
		this.associations = new ArrayList<Association>();
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

}
