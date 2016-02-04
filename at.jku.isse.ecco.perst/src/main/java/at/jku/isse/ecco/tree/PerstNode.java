package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import org.garret.perst.*;
import org.garret.perst.impl.StorageImpl;

import java.util.List;

/**
 * A perst implementation of the node. When recursive loading for this node is disabled it will be loaded on demand as soon as any of its object members is accessed.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstNode extends BaseNode implements Node, IPersistent, ICloneable {

//	private Link<Node> allChildren;
//	private Link<Node> uniqueChildren;


	@Override
	public Node createNode() {
		return new PerstNode();
	}


	/**
	 * TODO: this is a really ugly workaround! and it would also be needed for artifacts... instead of using recursive loading maybe use a perst list (link)?
	 * <p>
	 * best thing probably is to use perst lists (links) for uses/usedby and all/unique/ordered children. for this i need to pass the storage in the constructor of nodes and artifacts in the perst daos, but that is no problem. the bigger probelm is that perst has to reimplement many of the things defined in the basenode and maybe an extend makes no sense anymore.
	 */

	private static boolean loadNodesRecursively = true;

	public static void setRecursiveNodeLoading(boolean loadRecursively) {
		PerstNode.loadNodesRecursively = loadRecursively;
	}

	public static boolean getRecursiveNodeLoading() {
		return PerstNode.loadNodesRecursively;
	}

	/**
	 * TODO: since i need artifact and parent right away i need to recursively load every node that i intend to use! but not its children...
	 * <p>
	 * always recursively load nodes (i.e. artifact, parent, etc.)! BUT: do NOT recursively load children! that means: the lists of unique/all children should be perst indices!
	 */

	transient boolean loaded = false;
	transient boolean loadRecursively = false; // TODO: when to set this? in the perst dao? when loading an association specify whether its nodes should be loaded recursively? hm...

	public void setRecursiveLoading(boolean recursiveLoading) {
		this.loadRecursively = recursiveLoading;
	}

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
	public List<Node> getChildren() {
		this.loadNode();
		return super.getChildren();
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
		//return this.loadRecursively;
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
