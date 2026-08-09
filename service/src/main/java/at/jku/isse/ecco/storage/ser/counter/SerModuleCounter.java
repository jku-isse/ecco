package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;

import java.util.ArrayList;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Synchronized on {@code this} for every read AND write of {@link #children}/{@link #count} - same
 * live-shared-mutable-state race as SerAssociationCounter (see association-counter-unsynchronized-race
 * in project memory and that class's javadoc); this counter is reached via
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
	private Collection<SerModuleRevisionCounter> children;
	//private Map<MemModuleRevision, MemModuleRevisionCounter> children;


	public SerModuleCounter(SerModule module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
		this.children = new ArrayList<>();
		//this.children = Maps.mutable.empty();
		//this.children = HashObjObjMaps.newMutableMap();
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
//			throw new EccoException("Only MemModuleRevision can be added as a child to MemModuleCounter!");
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
			throw new EccoException("Only MemModuleRevision can be added as a child to MemModuleCounter!");
		SerModuleRevision memChild = (SerModuleRevision) child;
		for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(memChild))
				return null;
		}
		SerModuleRevisionCounter moduleRevisionCounter = new SerModuleRevisionCounter(memChild);
		this.children.add(moduleRevisionCounter);
		return moduleRevisionCounter;
	}

	@Override
	public synchronized ModuleRevisionCounter getChild(ModuleRevision child) {
		for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(child))
				return moduleRevisionCounter;
		}
		return null;
	}

	@Override
	public synchronized Collection<ModuleRevisionCounter> getChildren() {
		return new ArrayList<>(this.children);
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
