package at.jku.isse.ecco.storage.xml.impl;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.CompactWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.util.Objects.requireNonNull;

@Singleton
public class XmlTransactionStrategy implements TransactionStrategy {

    private static final String JSON_DB_NAME = "ecco.db.xml.zip";
    public static final String RENAMED_JSON_DB_NAME = JSON_DB_NAME + ".backup";
    private static final String TEMP_JSON_DB_NAME = JSON_DB_NAME + ".lock";


    public Path getRepoPath() {
        return repositoryDir.resolve(JSON_DB_NAME);
    }

    public Path getTempRepoPath() {
        return repositoryDir.resolve(TEMP_JSON_DB_NAME);
    }


    private transient final Path repositoryDir;

    @Inject
    public XmlTransactionStrategy(@Named("repositoryDir") Path repositoryDir) {
        this.repositoryDir = requireNonNull(repositoryDir);
    }


    public static XmlRepository loadFromDisk(Path storedRepo) throws IOException {
        if (!Files.exists(storedRepo))
            throw new FileNotFoundException("No repository can be found at '" + storedRepo + '\'');

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(storedRepo));
             BufferedReader repoStream = new BufferedReader(new InputStreamReader(zis))) {
            boolean found = false;
            ZipEntry cur = null;
            while (!found && (cur = zis.getNextEntry()) != null)
                found = ZIP_NAME.equals(cur.getName());
            if (cur == null)
                throw new UnsupportedOperationException("Unable to find the database in the ZIP file");

            final XmlRepository loaded = (XmlRepository) getSerializer().fromXML(repoStream);
            return loaded;
        }
    }

    private static final String ZIP_NAME = "ecco.xml";

    public void storeRepo(Path storageFile, XmlRepository repo) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(storageFile, StandardOpenOption.CREATE_NEW));
             BufferedWriter repoStorage = new BufferedWriter(new OutputStreamWriter(zipOut, StandardCharsets.UTF_8))) {
            zipOut.putNextEntry(new ZipEntry(ZIP_NAME));
            getSerializer().marshal(repo, new CompactWriter(repoStorage));
        }
    }


    private static XStream getSerializer() {
        XStream xStream = new XStream();
        XStream.setupDefaultSecurity(xStream);
        xStream.allowTypesByWildcard(new String[]{
                "**"
        });
        return xStream;
    }

    //Only one transaction at a time

    @Override
    public void open() throws EccoException {
    }

    @Override
    public void close() throws EccoException {
        try {
            Files.deleteIfExists(getTempRepoPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void begin(TRANSACTION transaction) {

    }

    @Override
    public void end() throws EccoException {
        if (curRepo != null)
            store(curRepo);
    }

    @Override
    public void rollback() throws EccoException {
        curRepo = null;
        load();
    }


    public XmlRepository load() {
        if (curRepo != null)
            return curRepo;
        final Path repoPath = getRepoPath();
        if (!Files.exists(repoPath))
            return curRepo = new XmlRepository();
        try {
            return curRepo = loadFromDisk(repoPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private XmlRepository curRepo = null;


    public void store(Repository.Op repository) {
        if (repository instanceof XmlRepository) {
            XmlRepository jsonRepository = (XmlRepository) repository;
            try {
                storeToFile(jsonRepository);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else
            throw new IllegalStateException("Repository of type '" +
                    (repository == null ? null : repository.getClass()
                            + "' is not supported!"));
    }

    private void storeToFile(XmlRepository repo) throws IOException {
        requireNonNull(repo);
        final Path tempFileName = getTempRepoPath();
        final Path mainFileName = getRepoPath();
        final Path renamedMainFileName = mainFileName.getParent().resolve(RENAMED_JSON_DB_NAME);

        if (!Files.exists(mainFileName)) {
            //Easy: Try to write to main file or fail!
            try {
                storeRepo(mainFileName, repo);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assert Files.exists(mainFileName);
            return;
        }
        //Rename main file, rename temp file, rename the renamed temp file to the main file name
        assert !Files.exists(tempFileName);
        assert !Files.exists(renamedMainFileName);
        assert Files.exists(mainFileName);

        try {
            //Rename main file to backup
            Files.move(mainFileName, renamedMainFileName);
        } catch (IOException e) {
            throw new EccoException("Could not commit repo!", e);
        }

        try {
            //Create temp file
            storeRepo(tempFileName, repo);
            assert Files.exists(tempFileName);
            //Rename temp to main
            Files.move(tempFileName, mainFileName);
        } catch (IOException e) {
            //Try to move the backup back to main file
            try {
                Files.move(renamedMainFileName, mainFileName);
            } catch (IOException e1) {
                //silent
            }
            throw new EccoException("Could not move '" + tempFileName + "' to '" + mainFileName + "... Please do that manually!");
        }

        assert !Files.exists(tempFileName);
        assert Files.exists(renamedMainFileName);
        assert Files.exists(mainFileName);

        try {
            Files.delete(renamedMainFileName);
        } catch (IOException e) {
            throw new EccoException("Could not delete '" + renamedMainFileName + "'. Please delete this file manually and restart the program. No data has been lost!");
        }

        assert !Files.exists(tempFileName);
        assert !Files.exists(renamedMainFileName);
        assert Files.exists(mainFileName);
    }
}