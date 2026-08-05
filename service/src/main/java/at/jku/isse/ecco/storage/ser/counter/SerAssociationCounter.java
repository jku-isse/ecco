package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import org.eclipse.collections.impl.factory.Maps;

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


	public SerAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = Maps.mutable.empty();
	}


	@Override
	public synchronized ModuleCounter addChild(Module child) {
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
		return this.children.get(child);
	}

	@Override
	public synchronized Collection<ModuleCounter> getChildren() {
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
