package at.jku.isse.ecco.storage.ser.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Memory implementation of {@link Commit}.
 * <p>
 * Holds association <b>IDs</b>, not the association objects themselves - a commit that references
 * an association it didn't just create/modify would otherwise pull that association's full content
 * along whenever the commit is serialized (most associations are referenced by many commits over
 * their lifetime), which is exactly what per-association-file persistence needs to avoid. IDs are
 * resolved against {@link #associationResolver} - set once by {@link at.jku.isse.ecco.storage.ser.repository.SerRepository}
 * (at commit creation time, and again after every load) - lazily, on demand.
 */
public class SerCommit implements Commit {
	public static final long serialVersionUID = 1L;

	private String id;
	private String committer;
	private Configuration configuration;
	private Date committingDate;
	private String commitMessage;
	private final Set<String> associationIds = new LinkedHashSet<>();

	private transient Repository associationResolver;


	public SerCommit(String username) {
		committer = username;
		committingDate = new Date();
	}

	public void setAssociationResolver(Repository associationResolver) {
		this.associationResolver = associationResolver;
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
		committer = System.getProperty("user.name");
	}

	@Override
	public String getUsername() {
		return committer;
	}

	@Override
	public void setCommitMessage(String commitMessage) {
		this.commitMessage = commitMessage;
	}

	@Override
	public String getCommitMessage() {
		return commitMessage;
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
	public boolean containsAssociation(final Association association) {
		return associationIds.contains(association.getId());
	}

	@Override
	public void addAssociation(final Association association) {
		associationIds.add(association.getId());
	}

	@Override
	public void deleteAssociation(final Association association) {
		associationIds.remove(association.getId());
	}

	public Collection<Association> getAssociations() {
		if (associationResolver == null) {
			throw new IllegalStateException("SerCommit " + id + " has no association resolver wired up - " +
					"this should be set by SerRepository at commit-creation time and again after every load.");
		}
		Collection<Association> result = new ArrayList<>(associationIds.size());
		for (String associationId : associationIds) {
			Association association = associationResolver.getAssociation(associationId);
			if (association != null) {
				result.add(association);
			}
		}
		return result;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (!(o instanceof SerCommit)) return false;
		final SerCommit memCommit = (SerCommit) o;
		return Objects.equals(id, memCommit.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
