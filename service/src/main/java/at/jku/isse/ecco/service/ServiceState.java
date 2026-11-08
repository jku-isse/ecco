package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.ArtifactPlugin;

import java.nio.file.Path;
import java.util.Collection;

/**
 * What the services {@link EccoService} delegates to read of it: whether a repository is open and
 * its {@link RepositoryContext}, the directories and plugins, and the service's monitor - instead of
 * the whole service. Read live: the base directory can change and an open replaces the context.
 * The other services a service uses are given to it when it is made, not reached through this.
 */
interface ServiceState {

	/** The open repository's context; throws {@link ServiceNotInitializedException} if none is open. */
	RepositoryContext context();

	/** The last opened repository's context, without checking that it is open; null before the first open. */
	RepositoryContext currentContext();

	/** Rolls back the current transaction if there is one; also safe before the first open and after close. */
	default void rollbackIfTransactionActive() {
		RepositoryContext context = this.currentContext();
		if (context != null)
			context.rollbackIfActive();
	}

	void checkInitialized();

	boolean isInitialized();

	/** The directory files are committed from and checked out to. */
	Path baseDir();

	Path repositoryDir();

	boolean repositoryDirectoryExists();

	/** The enabled artifact plugins, a copy. */
	Collection<ArtifactPlugin> artifactPlugins();

	/** The service's monitor, which its synchronized methods hold. */
	Object lock();

	/** Creates the repository at {@link #repositoryDir()} and opens it - see {@link EccoService#init()}; for a fork. */
	void init();

	/** Undoes a repository creation that failed part way - see {@link EccoService#init()}; for a fork. */
	void abandonNewRepository(Throwable cause);
}
