package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.mem.dao.Database;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class SerTransactionStrategy implements TransactionStrategy {

	private static final String ID_FILENAME = "lock";
	private static final String WRITELOCK_FILENAME = "lock";

	// repository directory
	protected final Path repositoryDir;
	// file containing the current database id
	protected final Path idFile;
	// lock file for making sure there is onyl one write transaction going on at a time
	protected final Path writeLockFile;
	// currently loaded database object
	protected Database database;
	// id of currently loaded database file
	protected String id;


	@Inject
	public SerTransactionStrategy(@Named("repositoryDir") final Path repositoryDir) {
		checkNotNull(repositoryDir);
		this.repositoryDir = repositoryDir;
		this.idFile = repositoryDir.resolve(ID_FILENAME);
		this.writeLockFile = repositoryDir.resolve(WRITELOCK_FILENAME);
		this.database = null;
		this.id = null;
	}

	protected Database getDatabase() {
		return this.database;
	}

	@Override
	public void open() throws EccoException {
		// read current (according to ID_FILENAME) serialized file (store this.id) and set this.database

	}

	@Override
	public void close() throws EccoException {
		this.database = null;
	}

	@Override
	public void begin() throws EccoException {
		// for read/write transactions get a write lock (full lock) of WRITELOCK_FILENAME


		// check (while having full lock on the id file) if currently open file (this.id) is the same is in ID_FILENAME. if not deserialize new current file (according to ID_FILENAME) and set this.id and this.database (i.e. same as open)


		// for read only transactions get a read lock on the database file?



	}

	@Override
	public void end() throws EccoException {
		// for read only transactions simply release the read lock on the database file. if there are no more locks and the database file is not current anymore delete it.


		// for read/write transactions serialize into a new UUID.randomUUID() folder and set the id in the lock file. then release the write lock on the lock file (the other lock file?).


	}

	@Override
	public void rollback() throws EccoException {
		// do not serialize. deserialize the old (i.e. still current) file again and set this.database


	}


	public static Object deserialize(String fileName) throws IOException, ClassNotFoundException {
		FileInputStream fis = new FileInputStream(fileName);
		BufferedInputStream bis = new BufferedInputStream(fis);
		ObjectInputStream ois = new ObjectInputStream(bis);
		Object obj = ois.readObject();
		ois.close();
		return obj;
	}

	public static void serialize(Object obj, String fileName) throws IOException {
		FileOutputStream fos = new FileOutputStream(fileName);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(obj);
		oos.close();
	}

}
