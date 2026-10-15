package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Association files store the counters in compact form (see {@link CompactCounters}): reopening a
 * repository must give exactly the counters it had - every module and module revision with its
 * counts, on the repository's own module instances - and a counter in the old, plain form (files
 * written before, remote synchronization) must still be read.
 */
public class CompactCounterPersistenceTest {

    /** Every association's counter as text: module -> count, and each revision -> count. */
    private static Map<String, String> counters(EccoService service) {
        Map<String, String> result = new TreeMap<>();
        for (Association association : service.getRepository().getAssociations()) {
            Association.Op op = (Association.Op) association;
            Map<String, String> modules = new TreeMap<>();
            for (ModuleCounter moduleCounter : op.getCounter().getChildren()) {
                Map<String, Integer> revisions = new TreeMap<>();
                for (ModuleRevisionCounter revisionCounter : moduleCounter.getChildren())
                    revisions.put(revisionCounter.getObject().toString(), revisionCounter.getCount());
                modules.put(moduleCounter.getObject().toString(), moduleCounter.getCount() + " " + revisions);
            }
            result.put(association.getId(), op.getCounter().getCount() + " " + modules + " | " + association.computeCondition().toLogicString());
        }
        return result;
    }

    private static Path commitSome(Path workDir) throws Exception {
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            for (int i = 0; i < 4; i++)
                Files.writeString(content.resolve("base" + i + ".txt"), "base " + i + "\n");
            String[] configurations = {"A", "A, B", "B, C", "A.2, C", "A.2, B, D", "C, D"};
            for (int c = 0; c < configurations.length; c++) {
                Files.writeString(content.resolve("base" + (c % 4) + ".txt"), "commit " + c + "\n", StandardOpenOption.APPEND);
                Files.writeString(content.resolve("only" + c + ".txt"), "only " + c + "\n");
                service.commit("c" + c, configurations[c]);
            }
        }
        return workDir;
    }

    @Test
    @Timeout(120)
    public void reopeningGivesTheSameCountersOnTheRepositorysOwnModules() throws Exception {
        Path workDir = commitSome(Files.createTempDirectory("compact-counters"));
        Map<String, String> before;
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.open();
            before = counters(service);
            // and once more through a commit on the reopened repository
            Files.writeString(workDir.resolve("content").resolve("more.txt"), "more\n");
            service.setBaseDir(workDir.resolve("content"));
            service.commit("more", "B, D");
            before = counters(service);
        }
        assertFalse(before.isEmpty());

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.open();
            assertEquals(before, counters(service));

            SerRepository repository = (SerRepository) service.getRepository();
            for (Association association : repository.getAssociations())
                for (ModuleCounter moduleCounter : ((Association.Op) association).getCounter().getChildren()) {
                    Module module = moduleCounter.getObject();
                    assertSame(repository.getModule(module.getPos(), module.getNeg()), module, "counted module must be the repository's own");
                    for (ModuleRevisionCounter revisionCounter : moduleCounter.getChildren())
                        assertSame(module.getRevision(revisionCounter.getObject().getPos(), revisionCounter.getObject().getNeg()), revisionCounter.getObject());
                }
        }
    }

    @Test
    @Timeout(120)
    public void associationFilesHoldTheCompactForm() throws Exception {
        Path workDir = commitSome(Files.createTempDirectory("compact-counters-files"));
        try (Stream<Path> files = Files.list(workDir.resolve(".ecco").resolve("associations"))) {
            for (Path file : files.toList()) {
                String content;
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile());
                     java.io.InputStream in = zip.getInputStream(zip.entries().nextElement())) {
                    content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                }
                assertTrue(content.contains("compactChildren"), file + " has no compact counters");
                assertFalse(content.contains("SerModuleRevisionCounter"), file + " still holds serialized counter objects");
            }
        }
    }

    @Test
    @Timeout(120)
    public void aCounterInThePlainFormIsStillRead() throws Exception {
        Path workDir = commitSome(Files.createTempDirectory("compact-counters-plain"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.open();
            for (Association association : service.getRepository().getAssociations()) {
                Association.Op op = (Association.Op) association;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                    out.writeObject(op);
                }
                Association.Op copy;
                try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                    copy = (Association.Op) in.readObject();
                }
                assertEquals(op.getCounter().getChildren().size(), copy.getCounter().getChildren().size());
                assertEquals(op.getCounter().getCount(), copy.getCounter().getCount());
                assertEquals(association.computeCondition().toLogicString(), copy.computeCondition().toLogicString());
            }
        }
    }

    /**
     * Version 2 refers to the repository's list of counted module revisions instead of writing each
     * one's features again: it decodes to the same counters as version 1 (which files written
     * before hold, and which is still read), and is smaller.
     */
    @Test
    @Timeout(120)
    public void bothVersionsDecodeToTheSameCountersTheIndexedOneSmaller() throws Exception {
        Path workDir = commitSome(Files.createTempDirectory("compact-counters-versions"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.open();
            at.jku.isse.ecco.storage.ser.repository.SerRepository repository = (at.jku.isse.ecco.storage.ser.repository.SerRepository) service.getRepository();
            long version1 = 0, version2 = 0;
            for (Association association : repository.getAssociations()) {
                java.util.Collection<SerModuleCounter> children = ((Association.Op) association).getCounter().getChildren().stream().map(c -> (SerModuleCounter) c).toList();
                byte[] v1 = CompactCounters.encode(children), v2 = CompactCounters.encode(children, repository);
                version1 += v1.length;
                version2 += v2.length;
                assertEquals(text(CompactCounters.decode(v1, repository, association.getId())), text(CompactCounters.decode(v2, repository, association.getId())), association.getId());
                assertEquals(text(((Association.Op) association).getCounter().getChildren()), text(CompactCounters.decode(v2, repository, association.getId())));
            }
            assertTrue(version2 < version1, "version 2 " + version2 + " bytes, version 1 " + version1);
        }
    }

    private static String text(java.util.Map<at.jku.isse.ecco.module.Module, SerModuleCounter> children) {
        return text(children.values());
    }

    private static String text(java.util.Collection<? extends ModuleCounter> children) {
        Map<String, String> modules = new TreeMap<>();
        for (ModuleCounter moduleCounter : children) {
            Map<String, Integer> revisions = new TreeMap<>();
            for (ModuleRevisionCounter revisionCounter : moduleCounter.getChildren())
                revisions.put(revisionCounter.getObject().toString(), revisionCounter.getCount());
            modules.put(moduleCounter.getObject().toString(), moduleCounter.getCount() + " " + revisions);
        }
        return modules.toString();
    }
}
