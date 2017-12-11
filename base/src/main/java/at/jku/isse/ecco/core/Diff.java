package at.jku.isse.ecco.core;

import java.util.List;

/**
 * A diff object contains associations for the new artifacts, the removed artifacts, and the unmodified artifacts.
 */
public class Diff {

	protected List<Association> newAssocations;
	protected List<Association> removedAssociations;
	protected List<Association> unmodifiedAssociations;


	public List<Association> getUnmodified() {
		return this.unmodifiedAssociations;
	}

	public void addUnmodified(Association association) {
		this.unmodifiedAssociations.add(association);
	}


	public List<Association> getRemoved() {
		return this.removedAssociations;
	}

	public void addRemoved(Association association) {
		this.removedAssociations.add(association);
	}


	public List<Association> getNew() {
		return this.newAssocations;
	}

	public void addNew(Association association) {
		this.newAssocations.add(association);
	}

}
