package at.jku.isse.ecco.storage.perst.test;

import at.jku.isse.ecco.EccoException;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class TransientTest {

	@Test(groups = {"integration", "perst", "multiclient"})
	public void Transient_1_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		//database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/transienttest.db");

		System.out.println("ROOT-1: " + database.getRoot());
		System.out.println("ROOT0: " + database.getRoot());

		database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
		try {
			// do something

			System.out.println("ROOT1: " + database.getRoot());
			System.out.println("ROOT2: " + database.getRoot());
			System.out.println("ROOT3: " + database.getRoot());

			database.setRoot(new Object());

			System.out.println("Exclusive transaction began!");
			System.out.println("ROOT4: " + database.getRoot());
			System.out.println("Exclusive transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		System.out.println("ROOT5: " + database.getRoot());
		System.out.println("ROOT6: " + database.getRoot());

		System.out.println("ROOT-1: " + database.getRoot());
		System.out.println("ROOT0: " + database.getRoot());

		database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
		try {
			// do something

			System.out.println("ROOT1: " + database.getRoot());
			System.out.println("ROOT2: " + database.getRoot());
			System.out.println("ROOT3: " + database.getRoot());

			database.setRoot(new Object());

			System.out.println("Exclusive transaction began!");
			System.out.println("ROOT4: " + database.getRoot());
			System.out.println("Exclusive transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		System.out.println("ROOT5: " + database.getRoot());
		System.out.println("ROOT6: " + database.getRoot());

		database.close();


		database.open("data/transienttest.db");

		System.out.println("ROOT-1: " + database.getRoot());
		System.out.println("ROOT0: " + database.getRoot());

		database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
		try {
			// do something

			System.out.println("ROOT1: " + database.getRoot());
			System.out.println("ROOT2: " + database.getRoot());
			System.out.println("ROOT3: " + database.getRoot());

			database.setRoot(new Object());

			System.out.println("Exclusive transaction began!");
			System.out.println("ROOT4: " + database.getRoot());
			System.out.println("Exclusive transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		System.out.println("ROOT5: " + database.getRoot());
		System.out.println("ROOT6: " + database.getRoot());

		database.close();
	}


	@Test(groups = {"integration", "perst", "multiclient"})
	public void Transient_2_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/transienttest.db");

//		System.out.println("ROOT-1: " + database.getRoot());
//		System.out.println("ROOT0: " + database.getRoot());

		database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
		try {
			// do something

			System.out.println("ROOT1: " + database.getRoot());
			System.out.println("ROOT2: " + database.getRoot());
			System.out.println("ROOT3: " + database.getRoot());

			TransientRoot transientRoot = new TransientRoot();
			transientRoot.transientInt = 5;
			transientRoot.normalInt = 5;
			database.setRoot(transientRoot);

			System.out.println("Exclusive transaction began!");
			System.out.println("ROOT4: " + ((TransientRoot) database.getRoot()).transientInt);
			System.out.println("Exclusive transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		System.out.println("ROOT5: " + database.getRoot());
		System.out.println("ROOT6: " + database.getRoot());

		System.out.println("ROOT-1: " + database.getRoot());
		System.out.println("ROOT0: " + database.getRoot());

		database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
		try {
			// do something

			System.out.println("ROOT1: " + database.getRoot());
			System.out.println("ROOT2: " + database.getRoot());
			System.out.println("ROOT3: " + database.getRoot());

			//database.setRoot(new Object());

			System.out.println("Exclusive transaction began!");
			System.out.println("ROOT4: " + ((TransientRoot) database.getRoot()).transientInt);
			System.out.println("Exclusive transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		System.out.println("ROOT5: " + database.getRoot());
		System.out.println("ROOT6: " + database.getRoot());

		database.close();


		database.open("data/transienttest.db");

//		System.out.println("ROOT-1: " + database.getRoot());
//		System.out.println("ROOT0: " + database.getRoot());

		database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
		try {
			// do something

			System.out.println("ROOT1: " + database.getRoot());
			System.out.println("ROOT2: " + database.getRoot());
			System.out.println("ROOT3: " + database.getRoot());

			//database.setRoot(new Object());

			System.out.println("Exclusive transaction began!");
			System.out.println("ROOT4: " + ((TransientRoot) database.getRoot()).transientInt);
			System.out.println("Exclusive transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		System.out.println("ROOT5: " + database.getRoot());
		System.out.println("ROOT6: " + database.getRoot());

		database.close();
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
