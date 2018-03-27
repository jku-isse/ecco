package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.mem.dao.Database;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class SerTransactionStrategy implements TransactionStrategy {

	private static final String ID_FILENAME = "id";
	private static final String WRITELOCK_FILENAME = "write";
	private static final String DB_FILENAME = "ecco.ser.zip";

	// repository directory
	protected final Path repositoryDir;
	// file containing the current database id
	protected final Path idFile;
	// lock file for making sure there is onyl one write transaction going on at a time
	protected final Path writeLockFile;
	// database file
	protected final Path dbFile;
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
		this.dbFile = repositoryDir.resolve(DB_FILENAME);
		this.database = null;
		this.id = null;
	}

	protected Database getDatabase() {
		return this.database;
	}


	@Override
	public void open() {
		// read current (according to ID_FILENAME) serialized file (store this.id) and set this.database


		// TEMP: check if the serialization file exists. if it does load it. if not create a new database object.
		if (Files.exists(this.dbFile) && Files.isRegularFile(this.dbFile)) {
			try {
				this.database = (Database) deserialize(this.dbFile);
			} catch (IOException | ClassNotFoundException e) {
				throw new EccoException("Error during database open.", e);
			}
		} else {
			this.database = new Database();
		}
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


		// TEMP: nothing to do here
	}

	protected void beginReadOnly() {

	}

	protected void beginReadWrite() {

	}


	@Override
	public void end() throws EccoException {
		// for read only transactions simply release the read lock on the database file. if there are no more locks and the database file is not current anymore delete it.


		// for read/write transactions serialize into a new UUID.randomUUID() folder and set the id in the lock file. then release the write lock on the lock file (the other lock file?).


		// TEMP: write the serialization file
		try {
			serialize(this.database, this.dbFile);
		} catch (IOException e) {
			throw new EccoException("Error during transaction end.", e);
		}
	}


	@Override
	public void rollback() throws EccoException {
		// do not serialize. deserialize the old (i.e. still current) file again and set this.database


		// TEMP: just reread the serialization file
		if (Files.exists(this.dbFile) && Files.isRegularFile(this.dbFile)) {
			try {
				this.database = (Database) deserialize(this.dbFile);
			} catch (IOException | ClassNotFoundException e) {
				throw new EccoException("Error during database rollback.", e);
			}
		} else {
			this.database = new Database();
		}
	}


	private static Object deserialize(Path file) throws IOException, ClassNotFoundException {
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file))) {
			ZipEntry e = null;
			while ((e = zis.getNextEntry()) != null) {
				if (e.getName().equals("ecco.ser")) {
					try (ObjectInputStream ois = new ObjectInputStream(zis)) {
						return ois.readObject();
					}
				}
			}
		}
		return null;
	}

	private static void serialize(Object object, Path file) throws IOException {
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE))) {
			zos.putNextEntry(new ZipEntry("ecco.ser"));
			try (ObjectOutputStream oos = new ObjectOutputStream(zos)) {
				oos.writeObject(object);
			}
		}
	}

}
