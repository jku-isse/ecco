package at.jku.isse.ecco.core;

import at.jku.isse.ecco.feature.Configuration;
import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.List;

/**
 * Perst implementation of {@link Commit}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstCommit extends Persistent implements Commit {

	private int id;
	private List<Association> associations;
	private String committer;
	private Configuration configuration;
	private List<Association> existingAssociations;
	private List<Association> newAssociations;

	public PerstCommit() {
		this.associations = new ArrayList<Association>();
		this.existingAssociations = new ArrayList<Association>();
		this.newAssociations = new ArrayList<Association>();
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
	public List<Association> getExistingAssociation() {
		return this.existingAssociations;
	}

	@Override
	public void addExistingAssociation(Association association) {
		this.existingAssociations.add(association);
	}

	@Override
	public List<Association> getNewAssociations() {
		return this.newAssociations;
	}

	@Override
	public void addNewAssociation(Association association) {
		this.newAssociations.add(association);
	}

}
