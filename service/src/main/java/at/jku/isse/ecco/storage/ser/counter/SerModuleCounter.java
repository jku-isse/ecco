package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Synchronized on {@code this} for every read AND write of the children/{@link #count} - same
 * live-shared-mutable-state race as SerAssociationCounter (see the unsynchronized-counter crash
 * and that class's javadoc); this counter is reached via
 * AssociationCounter#getChildren() and walked the same way by
 * {@code Association.Op#computeLikelyCondition()}, so it's vulnerable to the identical
 * concurrent-iteration-vs-addChild() race even though it hasn't been the one to actually crash yet
 * (it backs onto a plain ArrayList, whose iterator does throw the "expected"
 * ConcurrentModificationException rather than SerAssociationCounter's UnifiedMap-flavored
 * ArrayIndexOutOfBoundsException - still a crash either way).
 */
public class SerModuleCounter implements ModuleCounter {

	public static final long serialVersionUID = 1L;


	private SerModule module;
	private int count;
	/**
	 * The children: the first module revision counted, and any further ones. A module nearly always
	 * has one revision counted - each of tinycc's 17.8 million module counters had - and a list of
	 * its own for that one (an ArrayList and its array of ten) took 1.4 of 2.9 GB.
	 */
	private transient SerModuleRevisionCounter first;
	private transient List<SerModuleRevisionCounter> more;

	/** The serialized form is the one before - module, count and the children as a collection - see CounterStreamFormTest. */
	private static final ObjectStreamField[] serialPersistentFields = {
			new ObjectStreamField("module", SerModule.class),
			new ObjectStreamField("count", int.class),
			new ObjectStreamField("children", Collection.class)
	};
	//private Map<MemModuleRevision, MemModuleRevisionCounter> children;


	public SerModuleCounter(SerModule module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
	}

	private synchronized void writeObject(ObjectOutputStream out) throws IOException {
		ObjectOutputStream.PutField fields = out.putFields();
		fields.put("module", this.module);
		fields.put("count", this.count);
		fields.put("children", this.children());
		out.writeFields();
	}

	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		ObjectInputStream.GetField fields = in.readFields();
		this.module = (SerModule) fields.get("module", null);
		this.count = fields.get("count", 0);
		Collection<SerModuleRevisionCounter> children = (Collection<SerModuleRevisionCounter>) fields.get("children", null);
		if (children != null)
			for (SerModuleRevisionCounter child : children)
				this.add(child);
	}

	private void add(SerModuleRevisionCounter child) {
		if (this.first == null)
			this.first = child;
		else {
			if (this.more == null)
				this.more = new ArrayList<>(2);
			this.more.add(child);
		}
	}

	/** A copy of the children, in the order added. */
	private ArrayList<ModuleRevisionCounter> children() {
		ArrayList<ModuleRevisionCounter> children = new ArrayList<>(this.first == null ? 0 : 1 + (this.more == null ? 0 : this.more.size()));
		if (this.first != null)
			children.add(this.first);
		if (this.more != null)
			children.addAll(this.more);
		return children;
	}

	/**
	 * Used only by SerTransactionStrategy's post-load resolution pass: each association is
	 * deserialized from its own separate file, so this counter's {@code module} field - a direct,
	 * non-transient reference embedded in that same stream - comes back as its own independent copy
	 * of the module, not the repository's single canonical instance (Module.equals()/hashCode() are
	 * content-based, so this copy is data-equal but object-distinct). Since Module.getCount() is a
	 * mutable field read directly off whichever instance happens to be referenced
	 * (Association.computeCondition() -> moduleRevisionCounter.getObject().getCount()), every
	 * association ending up with its own divergent copy - instead of all sharing the repository's
	 * one true count - corrupts presence-condition computation after any reload. Replacing the
	 * reference with the repository's canonical instance (found by content equality, no id needed)
	 * restores the same-object-sharing invariant a single continuous session gets for free.
	 */
	public synchronized void resolveModule(SerModule module) {
		this.module = module;
	}


//	@Override
//	public MemModuleRevisionCounter addChild(ModuleRevision child) {
//		if (!(child instanceof MemModuleRevision))
//			throw new EccoException("Only a SerModuleRevision can be added as a child to a SerModuleCounter.");
//		MemModuleRevision memChild = (MemModuleRevision) child;
//		if (this.children.containsKey(memChild))
//			return null;
//		MemModuleRevisionCounter moduleRevisionCounter = new MemModuleRevisionCounter(memChild);
//		this.children.put(moduleRevisionCounter.getObject(), moduleRevisionCounter);
//		return moduleRevisionCounter;
//	}
//
//	@Override
//	public ModuleRevisionCounter getChild(ModuleRevision child) {
//		return this.children.get(child);
//	}
//
//	@Override
//	public Collection<ModuleRevisionCounter> getChildren() {
//		return Collections.unmodifiableCollection(this.children.values());
//	}

	@Override
	public synchronized SerModuleRevisionCounter addChild(ModuleRevision child) {
		if (!(child instanceof SerModuleRevision))
			throw new EccoException("Only a SerModuleRevision can be added as a child to a SerModuleCounter.");
		SerModuleRevision memChild = (SerModuleRevision) child;
		if (this.getChild(memChild) != null)
			return null;
		SerModuleRevisionCounter moduleRevisionCounter = new SerModuleRevisionCounter(memChild);
		this.add(moduleRevisionCounter);
		return moduleRevisionCounter;
	}

	@Override
	public synchronized ModuleRevisionCounter getChild(ModuleRevision child) {
		if (this.first != null && this.first.getObject().equals(child))
			return this.first;
		if (this.more != null)
			for (ModuleRevisionCounter moduleRevisionCounter : this.more) {
				if (moduleRevisionCounter.getObject().equals(child))
					return moduleRevisionCounter;
			}
		return null;
	}

	@Override
	public synchronized Collection<ModuleRevisionCounter> getChildren() {
		return this.children();
	}


	@Override
	public synchronized Module getObject() {
		return this.module;
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
		return this.getModuleCounterString();
	}

}
