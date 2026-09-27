package at.jku.isse.ecco.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.logging.Logger;

/**
 * Object input streams for the fetch/pull/push/fork wire protocol. Everything read from a peer goes
 * through an allow-list filter: only ECCO's own classes plus the JDK value and collection types they
 * are built from may be deserialized. A plain ObjectInputStream would instantiate any serializable
 * class on the classpath - and run its readObject() - before the caller's cast could reject it, the
 * classic deserialization-gadget remote-code-execution vector, reachable by anyone who can connect
 * to a running sync server.
 * <p>
 * at.jku.cdl.ecco is included because the java-ast adapter's artifact data lives there, Eclipse
 * Collections because several entities hold its maps/sets. If a new
 * adapter persists a type outside these packages, sync will fail with InvalidClassException naming
 * it - add it here deliberately rather than widening the patterns.
 */
public final class SyncObjectStreams {

	private static final Logger LOGGER = Logger.getLogger(SyncObjectStreams.class.getName());

	private static final ObjectInputFilter ALLOW_LIST = ObjectInputFilter.Config.createFilter(
			"at.jku.isse.ecco.**;at.jku.cdl.ecco.**;java.lang.*;java.util.*;java.util.concurrent.*;java.util.concurrent.atomic.*;org.eclipse.collections.impl.**;!*");

	/**
	 * The allow-list, logging what it rejects - InvalidClassException's own message ("filter status:
	 * REJECTED") doesn't name the class.
	 */
	static final ObjectInputFilter FILTER = info -> {
		ObjectInputFilter.Status status = ALLOW_LIST.checkInput(info);
		if (status == ObjectInputFilter.Status.REJECTED)
			LOGGER.warning("Sync: rejected deserializing " + info.serialClass());
		return status;
	};

	private SyncObjectStreams() {
	}

	public static ObjectInputStream newObjectInputStream(InputStream in) throws IOException {
		ObjectInputStream ois = new ObjectInputStream(in);
		ois.setObjectInputFilter(FILTER);
		return ois;
	}
}
