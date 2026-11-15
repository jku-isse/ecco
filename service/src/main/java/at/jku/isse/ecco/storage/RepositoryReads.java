package at.jku.isse.ecco.storage;

/**
 * A check on reads of the stored repository's structures - its associations, features and commits,
 * an association's tree, a node's children, an order graph - for tests: the
 * storage calls {@link #read()} where they are read, and a test sets what it checks (the GUI's tests:
 * that no view reads them on the FX thread, NFR-C3). Off unless {@code -Decco.checkRepositoryReads=true}:
 * {@link #CHECKED} is then false and constant, and a guarded call costs nothing. An artifact's data
 * is not reported: a view may keep artifacts, as NodeSnapshot does, and show their data on the FX thread.
 */
public final class RepositoryReads {

	/** Whether reads are checked; callers guard their call with it: {@code if (RepositoryReads.CHECKED) RepositoryReads.read();} */
	public static final boolean CHECKED = Boolean.getBoolean("ecco.checkRepositoryReads");

	private static volatile Runnable check = () -> {
	};

	private RepositoryReads() {
	}

	/** What a read checks - it may record or throw; null for nothing. */
	public static void setCheck(Runnable check) {
		RepositoryReads.check = check == null ? () -> {
		} : check;
	}

	/** A repository structure is being read. */
	public static void read() {
		check.run();
	}
}
