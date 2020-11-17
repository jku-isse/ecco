package at.jku.isse.ecco.storage.perst.test;


import at.jku.isse.ecco.module.ModuleFeature;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class TestModule extends HashSet<String> {//} implements IPersistent, ICloneable {

	public TestModule() {
		super();
		this.add("AAAAAAAAAAAAAAAA");
		this.testCollection2.add("ABBBBB");

		//this.arrayList = Arrays.asList(new String[]{"asdf", "blubb"});
//		this.arrayList.add(new Object());
		//this.arrayList.add("asdf");
	}

	public Collection<ModuleFeature> testCollection = new HashSet<ModuleFeature>();
	//public Collection<ModuleFeature> testCollection = new BaseModule();

	public Collection<String> testCollection2 = new HashSet<String>();

	public ModuleFeature[] testArray = new ModuleFeature[10];

	public List<String> arrayList;


//	// # PERST ################################################
//
//	protected void finalize() {
//		if ((state & DIRTY) != 0 && oid != 0) {
//			storage.storeFinalizedObject(this);
//		}
//		state = DELETED;
//	}
//
//	public synchronized void load() {
//		if (oid != 0 && (state & RAW) != 0) {
//			storage.loadObject(this);
//		}
//	}
//
//	public synchronized void loadAndModify() {
//		load();
//		modify();
//	}
//
//	public final boolean isRaw() {
//		return (state & RAW) != 0;
//	}
//
//	public final boolean isModified() {
//		return (state & DIRTY) != 0;
//	}
//
//	public final boolean isDeleted() {
//		return (state & DELETED) != 0;
//	}
//
//	public final boolean isPersistent() {
//		return oid != 0;
//	}
//
//	public void makePersistent(Storage storage) {
//		if (oid == 0) {
//			storage.makePersistent(this);
//		}
//	}
//
//	public void store() {
//		if ((state & RAW) != 0) {
//			throw new StorageError(StorageError.ACCESS_TO_STUB);
//		}
//		if (storage != null) {
//			storage.storeObject(this);
//			state &= ~DIRTY;
//		}
//	}
//
//	public void modify() {
//		if ((state & DIRTY) == 0 && oid != 0) {
//			if ((state & RAW) != 0) {
//				throw new StorageError(StorageError.ACCESS_TO_STUB);
//			}
//			Assert.that((state & DELETED) == 0);
//			storage.modifyObject(this);
//			state |= DIRTY;
//		}
//	}
//
//	public final int getOid() {
//		return oid;
//	}
//
//	public void deallocate() {
//		if (oid != 0) {
//			storage.deallocateObject(this);
//		}
//	}
//
//	public boolean recursiveLoading() {
//		return true;
//	}
//
//	public final Storage getStorage() {
//		return storage;
//	}
//
////	public boolean equals(Object o) {
////		if (o == this) {
////			return true;
////		}
////		if (oid == 0) {
////			return super.equals(o);
////		}
////		return o instanceof IPersistent && ((IPersistent) o).getOid() == oid;
////	}
////
////	public int hashCode() {
////		return oid;
////	}
//
//	public void onLoad() {
//	}
//
//	public void onStore() {
//	}
//
//	public void invalidate() {
//		state &= ~DIRTY;
//		state |= RAW;
//	}
//
//	transient Storage storage;
//	transient int oid;
//	transient int state;
//
//	static public final int RAW = 1;
//	static public final int DIRTY = 2;
//	static public final int DELETED = 4;
//
//	public void unassignOid() {
//		oid = 0;
//		state = DELETED;
//		storage = null;
//	}
//
//	public void assignOid(Storage storage, int oid, boolean raw) {
//		this.oid = oid;
//		this.storage = storage;
//		if (raw) {
//			state |= RAW;
//		} else {
//			state &= ~RAW;
//		}
//	}
//
//	protected void clearState() {
//		state = 0;
//		oid = 0;
//	}
//
//	public Object clone() {
//		TestModule p = (TestModule) super.clone();
//		p.oid = 0;
//		p.state = 0;
//		return p;
//	}
//
//	public void readExternal(java.io.ObjectInput s) throws java.io.IOException, ClassNotFoundException {
//		oid = s.readInt();
//	}
//
//	public void writeExternal(java.io.ObjectOutput s) throws java.io.IOException {
//		if (s instanceof StorageImpl.PersistentObjectOutputStream) {
//			makePersistent(((StorageImpl.PersistentObjectOutputStream) s).getStorage());
//		}
//		s.writeInt(oid);
//	}
}
