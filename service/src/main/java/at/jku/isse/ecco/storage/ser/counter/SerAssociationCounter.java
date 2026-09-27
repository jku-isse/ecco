package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import org.eclipse.collections.impl.factory.Maps;

import at.jku.isse.ecco.storage.ser.repository.SerRepository;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Synchronized on {@code this} for every read AND write of {@link #children}/{@link #count}: this
 * counter is a live, shared, mutable object reachable from any {@code Association} a caller holds a
 * reference to, and it is genuinely read from one thread (e.g. GUI rendering via
 * {@code Association.Op#computeLikelyCondition()}) while written from another (e.g. a commit's
 * background write via {@code addChild()}) - see association-counter-unsynchronized-race in project
 * memory for the real, twice-recurring production crash this caused
 * ({@code ArrayIndexOutOfBoundsException} inside Eclipse Collections' {@code UnifiedMap} iterator,
 * not the more familiar {@code ConcurrentModificationException} you'd get from a java.util
 * collection - confirmed empirically, see AssociationCounterTest). {@link #getChildren()} returns a
 * defensive copy rather than a live view for the same reason: a caller iterating the returned
 * collection must never be able to race a concurrent structural change to {@link #children}, no
 * matter how long that iteration takes.
 */
public class SerAssociationCounter implements AssociationCounter {

	public static final long serialVersionUID = 1L;


	private Association association;
	private int count;
	private Map<Module, SerModuleCounter> children;

	/**
	 * The serialized form: the fields above, except that in an association file (written with
	 * {@link CompactCounters.Stream}) the children are {@code compactChildren} instead - resolved
	 * against the repository by {@link #resolveCompactChildren} once it is loaded. Files written
	 * before carry {@code children} and are read as they were.
	 */
	private static final ObjectStreamField[] serialPersistentFields = {
			new ObjectStreamField("association", Association.class),
			new ObjectStreamField("count", int.class),
			new ObjectStreamField("children", Map.class),
			new ObjectStreamField("compactChildren", byte[].class)
	};

	/** Read compact children not yet resolved against the repository - see {@link #resolveCompactChildren}. */
	private transient byte[] unresolvedChildren;


	public SerAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = Maps.mutable.empty();
	}


	private synchronized void writeObject(ObjectOutputStream out) throws IOException {
		ObjectOutputStream.PutField fields = out.putFields();
		fields.put("association", this.association);
		fields.put("count", this.count);
		if (out instanceof CompactCounters.Stream) {
			this.checkResolved();
			fields.put("compactChildren", CompactCounters.encode(this.children.values()));
		} else {
			fields.put("children", this.children);
		}
		out.writeFields();
	}

	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		ObjectInputStream.GetField fields = in.readFields();
		this.association = (Association) fields.get("association", null);
		this.count = fields.get("count", 0);
		byte[] compactChildren = (byte[]) fields.get("compactChildren", null);
		if (compactChildren != null) {
			this.unresolvedChildren = compactChildren;
			this.children = Maps.mutable.empty();
		} else {
			this.children = (Map<Module, SerModuleCounter>) fields.get("children", null);
		}
	}

	/**
	 * Builds the children read in compact form on the repository's own features, modules and
	 * module revisions. Called by SerTransactionStrategy once the repository is loaded; a no-op
	 * for counters read in the old form or created in this session.
	 */
	public synchronized void resolveCompactChildren(SerRepository repository) {
		if (this.unresolvedChildren == null)
			return;
		this.children = CompactCounters.decode(this.unresolvedChildren, repository, this.association.getId());
		this.unresolvedChildren = null;
	}

	/** Children read in compact form are not there until resolved - reading them before would silently find none. */
	private void checkResolved() {
		if (this.unresolvedChildren != null)
			throw new IllegalStateException("The counter of association " + this.association.getId() + " was read but not resolved against its repository.");
	}

	@Override
	public synchronized ModuleCounter addChild(Module child) {
		this.checkResolved();
		if (!(child instanceof SerModule))
			throw new EccoException("Only MemModule can be added as a child to MemAssociationCounter!");
		SerModule memChild = (SerModule) child;
		if (this.children.containsKey(memChild))
			return null;
		SerModuleCounter moduleCounter = new SerModuleCounter(memChild);
		this.children.put(moduleCounter.getObject(), moduleCounter);
		return moduleCounter;
	}

	@Override
	public synchronized ModuleCounter getChild(Module child) {
		this.checkResolved();
		return this.children.get(child);
	}

	@Override
	public synchronized Collection<ModuleCounter> getChildren() {
		this.checkResolved();
		return new ArrayList<>(this.children.values());
	}

	@Override
	public Association getObject() {
		return this.association;
	}

	@Override
	public synchronized int getCount() {
		return this.count;
	}

	@Override
	public synchronized void setCount(int count) {
		this.count = count;
	}

	@Override
	public synchronized void incCount() {
		this.count++;
	}

	@Override
	public synchronized void incCount(int count) {
		this.count += count;
	}


	@Override
	public String toString() {
		return this.getAssociationCounterString();
	}

}
