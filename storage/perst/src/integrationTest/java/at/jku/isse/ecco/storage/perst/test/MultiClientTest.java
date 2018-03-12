package at.jku.isse.ecco.storage.perst.test;

import at.jku.isse.ecco.EccoException;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class MultiClientTest {

	@Test(groups = {"integration", "perst", "multiclient"})
	public void MultiClient_Exclusive_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/test.db");

		database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
		try {
			// do something

			System.out.println("Exclusive transaction began!");
			System.in.read();
			System.out.println("Exclusive transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		database.close();
	}


	@Test(groups = {"integration", "perst", "multiclient"})
	public void MultiClient_ReadWrite_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/test.db");

		database.beginThreadTransaction(Storage.READ_WRITE_TRANSACTION);
		try {
			// do something

			System.out.println(database.getRoot() + "!!!");
			database.setRoot(new Object());
			System.out.println(database.getRoot() + "!!!");

			System.out.println("ReadWrite transaction began!");
			//System.in.read();
			Thread.sleep(10000);
			System.out.println("ReadWrite transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		database.close();
	}


	@Test(groups = {"integration", "perst", "multiclient"})
	public void MultiClient_ReadOnly_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/test.db");

		database.beginThreadTransaction(Storage.READ_ONLY_TRANSACTION);
		try {
			// do something

			System.out.println(database.getRoot() + "!!!");

			System.out.println("ReadOnly transaction began!");
			//System.in.read();
			Thread.sleep(10000);
			System.out.println("ReadOnly transaction ended!");

			System.out.println(database.getRoot().toString());

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		database.close();
	}


	@Test(groups = {"integration", "perst", "multiclient"})
	public void MultiClient_NoInput_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/test.db");

		database.beginThreadTransaction(Storage.READ_ONLY_TRANSACTION);
		try {
			// do something

			System.out.println("ReadOnly transaction began!");

			System.out.println("ReadOnly transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		database.close();
	}


	@Test(groups = {"integration", "perst", "multiclient"})
	public void MultiClient_NoTransaction_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/test.db");

		try {
			// do something

			Object root = database.getRoot();
			System.out.println("NotExplicit transaction began!");
			System.in.read();
			System.out.println("NotExplicit transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		database.close();
	}


	@Test(groups = {"integration", "perst", "multiclient"})
	public void MultiClient_ReadOnly_Write_Test() throws EccoException {
		Storage database = StorageFactory.getInstance().createStorage();

		// enable multiclient access
		database.setProperty("perst.multiclient.support", Boolean.TRUE);

		database.open("data/test.db");

		database.beginThreadTransaction(Storage.READ_ONLY_TRANSACTION);
		try {
			// do something
			System.out.println(database.getRoot() + " ");

			System.out.println("ReadOnly transaction began!");
			database.setRoot(new Integer(1));

			System.out.println(database.getRoot() + " ");
			//System.in.read();
			System.out.println("ReadOnly transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

		database.close();
		database.open("data/test.db");

		database.beginThreadTransaction(Storage.READ_ONLY_TRANSACTION);
		try {
			// do something
			System.out.println(database.getRoot() + " ");

			System.out.println("ReadOnly transaction began!");
			database.setRoot(new Integer(2));

			System.out.println(database.getRoot() + " ");
			//System.in.read();
			System.out.println("ReadOnly transaction ended!");

			database.endThreadTransaction();
		} catch (Exception e) {
			database.rollbackThreadTransaction();
			e.printStackTrace();
		}

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
