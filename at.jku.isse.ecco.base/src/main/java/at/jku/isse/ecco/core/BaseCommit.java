package at.jku.isse.ecco.core;

import java.util.ArrayList;
import java.util.List;

public class BaseCommit implements Commit {

	private int id;
	private List<Association> associations;
	private String committer;

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
