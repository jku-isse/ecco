package at.jku.isse.ecco.storage.perst.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.storage.perst.dao.PerstEntityFactory;
import at.jku.isse.ecco.storage.perst.feature.PerstFeature;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.storage.perst.module.PerstModule;
import at.jku.isse.ecco.storage.perst.module.PerstModuleRevision;
import org.garret.perst.Persistent;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class PerstTest {

	public String connectionString = "data/test.db";
	public Storage database = null;
	public PerstEntityFactory entityFactory = new PerstEntityFactory();

	@Test(groups = {"integration", "perst", "association"})
	public void Perst_Test() throws EccoException {
		// initialize db
		database = StorageFactory.getInstance().createStorage();
		database.open(connectionString);
		if (database.getRoot() == null) {
			database.setRoot(new TestDatabaseRoot());
			System.out.println("LLL: " + ((TestDatabaseRoot) database.getRoot()).testModule.arrayList);
		}
		database.close();


		TestDatabaseRoot root;


		// open db
		root = this.openDatabase();

		TestModule testModule = root.testModule;

		this.closeDatabase();

		// do work
		PerstFeature feature = new PerstFeature("F1", "F1", "");
		ModuleFeature moduleFeature = new PerstModuleRevision(feature, true);

//		root.module.add(moduleFeature);
//		root.moduleFeatures.add(moduleFeature);
		testModule.add("BBBBBBBB");
		testModule.testCollection.add(moduleFeature);
		testModule.testArray[0] = moduleFeature;
		testModule.testCollection2.add("ASDF");

		// TODO: try with testmodule as ipersistent and modify it over different connections


		//root = this.openDatabase();
//		root.testModule = testModule;
//		root.testModule.modify();
//		root.testModule.store();
//		root.modify();
//		root.store();
//		database.modify(root.testModule);
//		database.store(root.testModule);
//		database.modify(root);
//		database.store(root);

		System.out.println("XXX: " + feature.getOid());


//		database.store(root.testModule);
//		database.store(root.module);
		root = this.openDatabase();
		System.out.println(root.testModule.arrayList);
		root.testModule = testModule;
		database.store(testModule);
		root.store();
		this.closeDatabase();
//		root.store();
//		database.store(root.testModule);
//		database.store(root.module);


		System.out.println("XXX: " + feature.getOid());


//		System.out.println("AAA: " + root.module);
//		System.out.println("AAA: " + root.moduleFeatures);
		System.out.println("AAA: " + root.testModule);
		System.out.println("AAA: " + root.testModule.testCollection);
		System.out.println("AAA: " + root.testModule.testCollection2);
		System.out.println("AAA: " + Arrays.toString(root.testModule.testArray));

		// close db
		//this.closeDatabase();


		// open db
		root = this.openDatabase();

//		System.out.println("BBB: " + root.module);
//		System.out.println("BBB: " + root.moduleFeatures);
		System.out.println("BBB: " + root.testModule);
		System.out.println("BBB: " + root.testModule.testCollection);
		System.out.println("BBB: " + root.testModule.testCollection2);
		System.out.println("BBB: " + Arrays.toString(root.testModule.testArray));

		// close db
		this.closeDatabase();


	}


	public void closeDatabase() {
		if (this.database.isOpened())
			this.database.close();
	}

	public TestDatabaseRoot openDatabase() {
		if (!database.isOpened())
			database.open(connectionString);
		return database.getRoot();
	}


	public class TestDatabaseRoot extends Persistent {
		public Collection<ModuleFeature> module;
		public Collection<ModuleFeature> moduleFeatures;
		public TestModule testModule;

		public List<String> templist;

		public TestDatabaseRoot() {
			this.module = new PerstModule();
			this.moduleFeatures = new HashSet<ModuleFeature>();
			this.testModule = new TestModule();
			this.templist = Arrays.asList(new String[]{"asdf"});
		}
	}


	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

}
