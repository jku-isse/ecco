package at.jku.isse.ecco.perst.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.PerstEntityFactory;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.module.*;
import org.garret.perst.Persistent;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

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
		}
		database.close();


		TestDatabaseRoot root;


		// open db
		root = this.openDatabase();

		TestModule testModule = root.testModule;

		this.closeDatabase();

		// do work
		PerstFeature feature = new PerstFeature("F1");
		ModuleFeature moduleFeature = new BaseModuleFeature(feature, true);

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

		public TestDatabaseRoot() {
			this.module = new PerstModule();
			this.moduleFeatures = new HashSet<ModuleFeature>();
			this.testModule = new TestModule();
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


	public Configuration parseConfigurationString(String value) throws EccoException {
		if (value == null || value.isEmpty()) throw new EccoException("No configuration string provided.");

		if (!value.matches("(\\+|\\-)?[a-zA-Z]+('?|(\\.([0-9])+)?)(|,[a-zA-Z]+('?|(\\.([0-9])+)?))"))
			throw new EccoException("Invalid configuration string provided.");

		Configuration configuration = this.entityFactory.createConfiguration();

		String[] featureInstanceStrings = value.split(",");
		for (String featureInstanceString : featureInstanceStrings) {
			if (featureInstanceString.contains(".")) {
				String[] pair = featureInstanceString.split("\\.");
				//String featureName = pair[0].replace("!", "").replace("+", "").replace("-", "");
				String featureName = pair[0];
				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
					featureName = featureName.substring(1);
				int version = Integer.parseInt(pair[1]);
				boolean featureSign = !(pair[0].startsWith("!") || pair[0].startsWith("-"));

				Feature feature = this.entityFactory.createFeature(featureName);
				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);

				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
			} else {
				String featureName = featureInstanceString;
				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
					featureName = featureName.substring(1);

				int version = -1; // TODO: how to deal with this? always use newest?
				boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

				Feature feature = this.entityFactory.createFeature(featureName);
				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);

				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
			}
		}

		return configuration;
	}

}
