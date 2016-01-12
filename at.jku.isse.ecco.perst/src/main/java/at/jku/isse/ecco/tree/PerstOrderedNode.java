package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sequenceGraph.PerstSequenceGraph;
import at.jku.isse.ecco.sequenceGraph.SequenceGraph;
import org.garret.perst.*;
import org.garret.perst.impl.StorageImpl;

import java.util.List;

/**
 * A perst implementation of the ordered node.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstOrderedNode extends BaseOrderedNode implements OrderedNode, IPersistent, ICloneable {


	@Override
	protected Node createNode() {
		return new PerstOrderedNode();
	}

	@Override
	public SequenceGraph createSequenceGraph() {
		return new PerstSequenceGraph();
	}


	// # PERST RECURSIVE LOADING ################################################

	transient boolean loaded = false;

	private void loadNode() {
		if (oid != 0) {
			if (!this.recursiveLoading() && !this.loaded) {
				// TODO: check if storage is open, if not open it
				this.load();
				// TODO: close storage
			}
		}
	}

	@Override
	public Artifact getArtifact() {
		this.loadNode();
		return super.getArtifact();
	}

	@Override
	public Node getParent() {
		this.loadNode();
		return super.getParent();
	}

	@Override
	public List<Node> getAllChildren() {
		this.loadNode();
		return super.getAllChildren();
	}

	@Override
	public List<Node> getUniqueChildren() {
		this.loadNode();
		return super.getUniqueChildren();
	}

	// # PERST ################################################

	protected void finalize() {
		if ((state & DIRTY) != 0 && oid != 0) {
			storage.storeFinalizedObject(this);
		}
		state = DELETED;
	}

	public synchronized void load() {
		if (oid != 0 && (state & RAW) != 0) {
			storage.loadObject(this);
			this.loaded = true;
		}
	}

	public synchronized void loadAndModify() {
		load();
		modify();
	}

	public final boolean isRaw() {
		return (state & RAW) != 0;
	}

	public final boolean isModified() {
		return (state & DIRTY) != 0;
	}

	public final boolean isDeleted() {
		return (state & DELETED) != 0;
	}

	public final boolean isPersistent() {
		return oid != 0;
	}

	public void makePersistent(Storage storage) {
		if (oid == 0) {
			storage.makePersistent(this);
		}
	}

	public void store() {
		if ((state & RAW) != 0) {
			throw new StorageError(StorageError.ACCESS_TO_STUB);
		}
		if (storage != null) {
			storage.storeObject(this);
			state &= ~DIRTY;
		}
	}

	public void modify() {
		if ((state & DIRTY) == 0 && oid != 0) {
			if ((state & RAW) != 0) {
				throw new StorageError(StorageError.ACCESS_TO_STUB);
			}
			Assert.that((state & DELETED) == 0);
			storage.modifyObject(this);
			state |= DIRTY;
		}
	}

	public final int getOid() {
		return oid;
	}

	public void deallocate() {
		if (oid != 0) {
			storage.deallocateObject(this);
		}
	}

	public boolean recursiveLoading() {
		return PerstNode.getRecursiveNodeLoading();
	}

	public final Storage getStorage() {
		return storage;
	}

	public void onLoad() {
	}

	public void onStore() {
	}

	public void invalidate() {
		state &= ~DIRTY;
		state |= RAW;
	}

	transient Storage storage;
	transient int oid;
	transient int state;

	static public final int RAW = 1;
	static public final int DIRTY = 2;
	static public final int DELETED = 4;

	public void unassignOid() {
		oid = 0;
		state = DELETED;
		storage = null;
	}

	public void assignOid(Storage storage, int oid, boolean raw) {
		this.oid = oid;
		this.storage = storage;
		if (raw) {
			state |= RAW;
		} else {
			state &= ~RAW;
		}
	}

	protected void clearState() {
		state = 0;
		oid = 0;
	}

	public Object clone() throws CloneNotSupportedException {
		PerstNode p = (PerstNode) super.clone();
		p.oid = 0;
		p.state = 0;
		return p;
	}

	public void readExternal(java.io.ObjectInput s) throws java.io.IOException, ClassNotFoundException {
		oid = s.readInt();
	}

	public void writeExternal(java.io.ObjectOutput s) throws java.io.IOException {
		if (s instanceof StorageImpl.PersistentObjectOutputStream) {
			makePersistent(((StorageImpl.PersistentObjectOutputStream) s).getStorage());
		}
		s.writeInt(oid);
	}

}
