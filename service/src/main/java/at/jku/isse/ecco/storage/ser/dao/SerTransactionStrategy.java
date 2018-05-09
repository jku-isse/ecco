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
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class SerTransactionStrategy implements TransactionStrategy {

	private static final boolean MULTIUSER_MODE = false;
	private static final boolean DELETE_OLD_DB_FILES = true;

	private static final String ID_FILENAME = "id";
	private static final String WRITELOCK_FILENAME = "write";
	private static final String DB_FILE_SUFFIX = ".ser.zip";
	private static final String DB_FILENAME = "ecco" + DB_FILE_SUFFIX;

	// repository directory
	protected final Path repositoryDir;
	// file containing the current database id
	protected final Path idFile;
	// lock file for making sure there is onyl one write transaction going on at a time
	protected final Path writeLockFile;

	// id of currently loaded database file
	protected String id;
	// database file
	protected Path dbFile;
	// currently loaded database object
	protected Database database;
	// type of current transaction
	protected TRANSACTION transaction;
	// number of begin transaction calls
	protected int transactionCounter;
	// write file channel
	protected FileChannel writeFileChannel;
	// write file lock
	protected FileLock writeFileLock;


	@Inject
	public SerTransactionStrategy(@Named("repositoryDir") final Path repositoryDir) {
		checkNotNull(repositoryDir);
		this.repositoryDir = repositoryDir;
		this.idFile = repositoryDir.resolve(ID_FILENAME);
		this.writeLockFile = repositoryDir.resolve(WRITELOCK_FILENAME);
		this.reset();
	}


	protected Database getDatabase() {
		return this.database;
	}


	protected void reset() {
		this.id = null;
		this.dbFile = null;
		this.database = null;
		this.transaction = null;
		this.transactionCounter = 0;
		this.writeFileChannel = null;
		this.writeFileLock = null;
	}


	@Override
	public void open() {
		this.reset();
	}

	@Override
	public void close() {
		if (this.transactionCounter != 0)
			throw new EccoException("Error closing connection: Not all transactions have been ended.");
		this.reset();
	}

	@Override
	public void rollback() {
		this.reset();
	}


	/**
	 * Begins a read/write transaction.
	 */
	@Deprecated
	@Override
	public void begin() {
		this.begin(TRANSACTION.READ_WRITE);
	}

	@Override
	public void begin(TRANSACTION transaction) {
		try {
			if (transaction == TRANSACTION.READ_ONLY)
				this.beginReadOnly();
			else if (transaction == TRANSACTION.READ_WRITE)
				this.beginReadWrite();
			this.transactionCounter++;
		} catch (IOException | ClassNotFoundException e) {
			throw new EccoException("Error beginning transaction.", e);
		}
	}


	/**
	 * Ends a transaction.
	 */
	@Override
	public void end() {
		this.transactionCounter--;
		if (this.transactionCounter == 0) {
			try {
				if (this.transaction == TRANSACTION.READ_ONLY)
					this.endReadOnly();
				else if (this.transaction == TRANSACTION.READ_WRITE)
					this.endReadWrite();
				this.transaction = null;
			} catch (IOException e) {
				throw new EccoException("Error ending transaction.", e);
			}
		}
	}


	protected void beginReadOnly() throws IOException, ClassNotFoundException {
		if (this.transaction == TRANSACTION.READ_ONLY) // nothing to do, we already have a read transaction going
			return;

		if (this.transaction == null)
			this.transaction = TRANSACTION.READ_ONLY;

		// check if id file exists and if not the repository is empty
		if (Files.exists(this.idFile)) {
			String id = this.readCurrentId();
			// check if this.id has changed or if this.dbFile has already been loaded before. if it has then do not load it again and just reuse this.database.)
			if (this.id != null && this.id.equals(id))
				return;
			this.id = id;

			Path dbFile = this.repositoryDir.resolve(this.id + DB_FILE_SUFFIX);
			if (Files.exists(this.dbFile)) {
				this.dbFile = dbFile;
				try (FileChannel dbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.READ); FileLock dbFileLock = dbFileChannel.lock(0, Long.MAX_VALUE, true)) {
					if (!dbFileLock.isValid())
						throw new EccoException("Could not obtain shared lock on DB file.");

					this.database = (Database) deserialize(this.dbFile);
				}
				this.checkAndDeleteUnusedDatabaseFile(this.dbFile);
			} else {
				throw new EccoException("DB file does not exist: " + this.dbFile);
			}
		} else {
			this.database = new Database();
		}
	}

	protected void endReadOnly() {
	}


	protected void beginReadWrite() throws IOException, ClassNotFoundException {
		if (this.transaction == TRANSACTION.READ_ONLY)
			throw new EccoException("Cannot elevate a read only transaction to a read write transaction.");

		if (this.transaction == TRANSACTION.READ_WRITE) // nothing to do, we already have a read/write transaction going
			return;

		// obtain exclusive write lock
		this.writeFileChannel = FileChannel.open(this.writeLockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		this.writeFileLock = this.writeFileChannel.lock(0, Long.MAX_VALUE, false);
		if (!this.writeFileLock.isValid())
			throw new EccoException("Could not obtain exclusive lock on WRITE file.");

		this.transaction = TRANSACTION.READ_WRITE;

		// check if id file exists and if not create it and write new id
		if (Files.exists(this.idFile)) {
			String id = this.readCurrentId();
			// check if this.id has changed or if this.dbFile has already been loaded before. if it has then do not load it again and just reuse this.database.)
			if (this.id != null && this.id.equals(id))
				return;
			this.id = id;

			Path dbFile = this.repositoryDir.resolve(this.id + DB_FILE_SUFFIX);
			if (Files.exists(dbFile)) {
				this.dbFile = dbFile;
				try (FileChannel dbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.READ); FileLock dbFileLock = dbFileChannel.lock(0, Long.MAX_VALUE, true)) {
					if (!dbFileLock.isValid())
						throw new EccoException("Could not obtain shared lock on DB file.");

					this.database = (Database) deserialize(this.dbFile);
				}
				this.checkAndDeleteUnusedDatabaseFile(this.dbFile);
			} else {
				throw new EccoException("DB file does not exist: " + this.dbFile);
			}
		} else {
			this.database = new Database();
		}
	}

	protected void endReadWrite() throws IOException {
		// check if we still have exclusive write lock and take it
		if (!this.writeFileLock.isValid())
			throw new EccoException("Lost exclusive lock on WRITE file.");

		// compute new random id
		String newId = UUID.randomUUID().toString();
		// serialize to new db file
		Path newDbFile = this.repositoryDir.resolve(newId + DB_FILE_SUFFIX);
		serialize(this.database, newDbFile);

		// obtain exclusive lock on id file, write new id, update current id and db file, release lock
		try (FileChannel idFileChannel = FileChannel.open(this.idFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE); FileLock idFileLock = idFileChannel.lock(0, Long.MAX_VALUE, false)) {
			if (!idFileLock.isValid())
				throw new EccoException("Could not obtain exclusive lock on ID file.");

			// write new id to id file
			idFileChannel.write(ByteBuffer.wrap(newId.getBytes(Charset.defaultCharset())));

			// delete old db file if nobody has a shared lock anymore (i.e. if we can get an exclusive lock on it)
			if (this.dbFile != null) {
				try (FileChannel oldDbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.WRITE); FileLock oldDbFileLock = oldDbFileChannel.lock(0, Long.MAX_VALUE, false)) {
					if (oldDbFileLock.isValid())
						Files.delete(this.dbFile);
				}
			}

			// update id and db file
			this.id = newId;
			this.dbFile = newDbFile;

			// release exclusive id lock automatically when exiting try block
		}

		// release exclusive write lock automatically after try block
		this.writeFileLock.close();
		this.writeFileChannel.close();
	}


	protected String readCurrentId() throws IOException {
		// get shared lock on id file, read id, release lock, return it
		try (RandomAccessFile ras = new RandomAccessFile(this.idFile.toFile(), "r"); FileChannel fileChannel = ras.getChannel(); FileLock fileLock = fileChannel.lock(0, Long.MAX_VALUE, true)) {
			if (!fileLock.isValid())
				throw new EccoException("Could not obtain shared lock on ID file.");

			return ras.readLine();
		}
	}

	/**
	 * Delete db file if we can get exclusive lock and it does not match id file.
	 *
	 * @param dbFile The db file to check and delete.
	 * @throws IOException When obtaining file lock or reading ID file or deleting DB file fails.
	 */
	private void checkAndDeleteUnusedDatabaseFile(Path dbFile) throws IOException {
		if (!DELETE_OLD_DB_FILES)
			return;

		if (dbFile == null)
			return;

		String currentId = this.readCurrentId();
		Path currentDbFile = this.repositoryDir.resolve(currentId + DB_FILE_SUFFIX);

		if (!currentDbFile.equals(dbFile)) {
			// try to delete db file
			try (FileChannel dbFileChannel = FileChannel.open(dbFile, StandardOpenOption.WRITE); FileLock dbFileLock = dbFileChannel.lock(0, Long.MAX_VALUE, false)) {
				if (dbFileLock.isValid())
					Files.delete(dbFile);
			}
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
