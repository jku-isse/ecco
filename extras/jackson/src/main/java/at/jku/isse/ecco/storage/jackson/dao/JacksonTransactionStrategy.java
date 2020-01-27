package at.jku.isse.ecco.storage.jackson.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
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
public class JacksonTransactionStrategy implements TransactionStrategy {

	private static final boolean DELETE_OLD_DB_FILES = true;
	private static final boolean REUSE_DB_ACROSS_TRANSACTIONS = true;

	private static final String ID_FILENAME = "id";
	private static final String WRITELOCK_FILENAME = "write";
	private static final String DB_FILE_SUFFIX = ".json.zip";

	// repository directory
	private final Path repositoryDir;
	// file containing the current database id
	private final Path idFile;
	// lock file for making sure there is onyl one write transaction going on at a time
	private final Path writeLockFile;

	// id of currently loaded database file
	private String id;
	// database file
	private Path dbFile;
	// currently loaded database object
	private Database database;
	// type of current transaction
	private TRANSACTION transaction;
	// number of begin transaction calls
	private int transactionCounter;
	// write file channel
	private FileChannel writeFileChannel;
	// write file lock
	private FileLock writeFileLock;
	// jackson object mapper
	private ObjectMapper objectMapper;


	@Inject
	public JacksonTransactionStrategy(@Named("repositoryDir") final Path repositoryDir) {
		checkNotNull(repositoryDir);
		this.repositoryDir = repositoryDir;
		this.idFile = repositoryDir.resolve(ID_FILENAME);
		this.writeLockFile = repositoryDir.resolve(WRITELOCK_FILENAME);

		this.objectMapper = new ObjectMapper(new SmileFactory());
		//this.objectMapper = new ObjectMapper(new CBORFactory());
		this.objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
		this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		this.objectMapper.registerModule(new AfterburnerModule());

		this.reset();
	}


	public Database getDatabase() {
		return this.database;
	}

	public TRANSACTION getTransaction() {
		return this.transaction;
	}


	@Override
	public synchronized void open() {
		this.reset();
	}

	@Override
	public synchronized void close() {
		if (this.transaction != null || this.transactionCounter != 0)
			throw new EccoException("Error closing connection: Not all transactions have been ended.");
		this.reset();
	}

	@Override
	public synchronized void rollback() {
		if (this.transaction == null && this.transactionCounter == 0)
			throw new EccoException("Error rolling back transaction: No transaction active.");
		this.reset();
	}


	@Override
	public synchronized void begin(TRANSACTION transaction) {
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
	public synchronized void end() {
		if (this.transaction == null || this.transactionCounter <= 0)
			throw new EccoException("There is no active transaction.");

		this.transactionCounter--;
		if (this.transactionCounter == 0) {
			try {
				if (this.transaction == TRANSACTION.READ_ONLY)
					this.endReadOnly();
				else if (this.transaction == TRANSACTION.READ_WRITE)
					this.endReadWrite();
			} catch (IOException e) {
				throw new EccoException("Error ending transaction.", e);
			}
		}
	}


	private void beginReadOnly() throws IOException, ClassNotFoundException {
		if (this.transaction == TRANSACTION.READ_ONLY) // nothing to do, we already have a read transaction going
			return;

		if (this.transaction == null)
			this.transaction = TRANSACTION.READ_ONLY;

		this.loadDatabase();
	}

	private void endReadOnly() {
		this.transaction = null;
	}


	private void beginReadWrite() throws IOException, ClassNotFoundException {
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

		this.loadDatabase();
	}

	private void endReadWrite() throws IOException {
		// check if we still have exclusive write lock and take it
		if (!this.writeFileLock.isValid())
			throw new EccoException("Lost exclusive lock on WRITE file.");

		// compute new random id
		String newId = UUID.randomUUID().toString();
		// serialize to new db file
		Path newDbFile = this.repositoryDir.resolve(newId + DB_FILE_SUFFIX);

		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(newDbFile, StandardOpenOption.CREATE))) {
			zos.putNextEntry(new ZipEntry("ecco.json"));
//			ObjectMapper objectMapper = new ObjectMapper(new SmileFactory());
//			objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
//			objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
//			objectMapper.writeValue(zos, this.database);
			this.objectMapper.writeValue(zos, this.database);
		}

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

		this.transaction = null;
	}


	private void reset() {
		this.id = null;
		this.dbFile = null;
		this.database = null;
		this.transaction = null;
		this.transactionCounter = 0;
		this.writeFileChannel = null;
		this.writeFileLock = null;
	}

	private String readCurrentId() throws IOException {
		// get shared lock on id file, read id, release lock, return it
		try (RandomAccessFile ras = new RandomAccessFile(this.idFile.toFile(), "r"); FileChannel fileChannel = ras.getChannel(); FileLock fileLock = fileChannel.lock(0, Long.MAX_VALUE, true)) {
			if (!fileLock.isValid())
				throw new EccoException("Could not obtain shared lock on ID file.");

			return ras.readLine();
		}
	}

	private void loadDatabase() throws IOException, ClassNotFoundException {
		// check if id file exists
		if (Files.exists(this.idFile)) {
			String id = this.readCurrentId();
			// check if this.id has changed or if this.dbFile has already been loaded before. if it has then do not load it again and just reuse this.database.)
			if (REUSE_DB_ACROSS_TRANSACTIONS && this.id != null && this.id.equals(id))
				return;
			this.id = id;

			Path dbFile = this.repositoryDir.resolve(this.id + DB_FILE_SUFFIX);
			if (Files.exists(dbFile)) {
				this.dbFile = dbFile;
				try (FileChannel dbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.READ); FileLock dbFileLock = dbFileChannel.lock(0, Long.MAX_VALUE, true)) {
					if (!dbFileLock.isValid())
						throw new EccoException("Could not obtain shared lock on DB file.");

					InputStream is = Channels.newInputStream(dbFileChannel);
					ZipInputStream zis = new ZipInputStream(is);
					ZipEntry e;
					while ((e = zis.getNextEntry()) != null) {
						if (e.getName().equals("ecco.json")) {
//							ObjectMapper objectMapper = new ObjectMapper(new SmileFactory());
//							this.database = objectMapper.readValue(zis, Database.class);
							this.database = this.objectMapper.readValue(zis, Database.class);
							break;
						}
					}
				}

				// delete db file if we can get exclusive lock and it does not match id file
				if (DELETE_OLD_DB_FILES) {
					String currentId = this.readCurrentId();
					Path currentDbFile = this.repositoryDir.resolve(currentId + DB_FILE_SUFFIX);
					if (!currentDbFile.equals(dbFile)) {
						// try to delete db file
						try (FileChannel oldDbFileChannel = FileChannel.open(dbFile, StandardOpenOption.WRITE); FileLock oldDbFileLock = oldDbFileChannel.lock(0, Long.MAX_VALUE, false)) {
							if (oldDbFileLock.isValid())
								Files.delete(dbFile);
						}
					}
				}
			} else {
				throw new EccoException("DB file does not exist: " + this.dbFile);
			}
		} else {
			this.database = new Database();
		}
	}

}
