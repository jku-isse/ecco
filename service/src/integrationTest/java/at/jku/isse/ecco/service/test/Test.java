package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.ser.dao.SerTransactionStrategy;
import at.jku.isse.ecco.tree.RootNode;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class Test {


	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void FileLockTest2() throws IOException {
		SerTransactionStrategy ts = new SerTransactionStrategy(Paths.get("C:\\Users\\user\\Desktop\\ECCO_TEST\\repo"));

		ts.open();

		ts.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

		ts.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

		ts.end();

		ts.end();

		ts.close();
	}

	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void FileLockTest() throws IOException {
		Path idFile = Paths.get("C:/Users/user/Desktop/ECCO_TEST/idfile");
		Path writeLockFile = Paths.get("C:/Users/user/Desktop/ECCO_TEST/writelockfile");

		// check if write lock file exists and if not create it
		if (!Files.exists(writeLockFile)) {
			Files.createFile(writeLockFile);
		}

		// obtain exclusive write lock
		{
			FileChannel writeFileChannel = FileChannel.open(writeLockFile, StandardOpenOption.WRITE);
			FileLock writeFileLock = writeFileChannel.lock(0, Long.MAX_VALUE, false);
			if (!writeFileLock.isValid())
				throw new EccoException("Could not obtain exclusive lock on WRITE file.");
			System.out.println("AAA: " + writeFileLock.isShared());
			writeFileChannel.lock(0, Long.MAX_VALUE, true);
			System.out.println("AAA: " + writeFileLock.isShared());
		}
		{
			FileChannel writeFileChannel = FileChannel.open(writeLockFile, StandardOpenOption.WRITE);
			FileLock writeFileLock = writeFileChannel.lock(0, Long.MAX_VALUE, false);
			if (!writeFileLock.isValid())
				throw new EccoException("Could not obtain exclusive lock on WRITE file.");
			System.out.println("AAA: " + writeFileLock.isShared());
		}

		// check if id file exists and if not create it and write new id
		if (!Files.exists(idFile)) {
			Files.write(idFile, UUID.randomUUID().toString().getBytes(Charset.defaultCharset()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		}

		// get shared lock on id file, read id, set it as current, release lock
		try (FileChannel idFileChannel = FileChannel.open(idFile, StandardOpenOption.READ); FileLock idFileLock = idFileChannel.lock(0, Long.MAX_VALUE, true)) {
			if (!idFileLock.isValid())
				throw new EccoException("Could not obtain shared lock on ID file.");
		}
	}


	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void Test() throws EccoException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("/home/user/Desktop/ECCO_TEST/REPO/.ecco"));
		service.init();
		System.out.println("Repository initialized.");

		// commit variants to the new repository
		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V1_purpleshirt"));
		service.commit();
//		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V2_stripedshirt"));
//		service.commit();
//		service.setBaseDir(Paths.get("/home/user/Desktop/ECCO_TEST/image_variants/V3_purpleshirt_jacket"));
//		service.commit();

		System.out.println("Commits done.");

		Collection<Path> paths = new ArrayList<>();
		paths.add(Paths.get("person.png"));
		RootNode mapped = service.map(paths);

		System.out.println("Map done.");

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void Test2() throws EccoException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\ECCO_TEST\\.ecco"));
		service.init();
		System.out.println("Repository initialized.");

		// commit variants to the new repository
		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\map_test_variants\\v1"));
		service.commit();
		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\map_test_variants\\v2"));
		service.commit();
//		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\map_test_variants\\v3"));
//		service.commit();

		System.out.println("Commits done.");

		Collection<Path> paths = new ArrayList<>();
		paths.add(Paths.get("t.txt"));
		RootNode mapped = service.map(paths);

		System.out.println("Map done.");

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

	@org.testng.annotations.Test(groups = {"integration", "base", "service"})
	public void Test3() throws EccoException, IOException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\ECCO_TEST\\.ecco"));
		service.init();
		System.out.println("Repository initialized.");

		// commit variants to the new repository
		service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\ecco_map_base"));
		service.commit();

		System.out.println("Commits done.");

		//Collection<Path> paths = new ArrayList<>();
		Collection<Path> paths = Files.walk(service.getBaseDir()).filter(p -> {
			return !p.getFileName().toString().startsWith(".");
		}).filter(p -> !p.equals(service.getBaseDir())).collect(Collectors.toList());
		paths = paths.stream().map(p -> service.getBaseDir().relativize(p)).collect(Collectors.toList());

		RootNode mapped = service.map(paths);

		System.out.println("Map done.");

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

}
