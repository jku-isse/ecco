package at.jku.isse.ecco.storage.perst.core;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import org.garret.perst.Persistent;

/**
 * Perst implementation of {@link Commit}.
 */
public class PerstCommit extends Persistent implements Commit {

	private int id;
	private Configuration configuration;

	public PerstCommit() {
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

}
