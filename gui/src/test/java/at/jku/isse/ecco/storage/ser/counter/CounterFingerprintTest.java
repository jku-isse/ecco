package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fingerprint of everything the association counters hold and derive, to compare a change of how
 * they are kept in memory against: for each association its count, each module and module revision
 * counted with its count and the module revision's global count, and the condition computed from
 * them - and whether each counter refers to the repository's own module and module revision (the
 * conditions read the global count off that instance).
 * <p>
 * Runs only when asked: {@code -Decco.fingerprint.repos=<working folder>[:<working folder>...]}
 * {@code -Decco.fingerprint.out=<file>} writes one line per association and a total per repository,
 * to compare between builds. In the GUI's tests because they have every adapter: a repository's
 * artifacts are read with the classes of the adapters that wrote them.
 */
public class CounterFingerprintTest {

    @Test
    @EnabledIfSystemProperty(named = "ecco.fingerprint.repos", matches = ".+")
    public void fingerprintTheRepositories() throws Exception {
        List<String> out = new ArrayList<>();
        for (String folder : System.getProperty("ecco.fingerprint.repos").split(File.pathSeparator)) {
            Path work = Path.of(folder);
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(work.resolve(".ecco"));
                service.setBaseDir(work);
                service.open();
                long start = System.nanoTime();
                SerRepository repository = (SerRepository) service.getRepository();
                long loadMs = (System.nanoTime() - start) / 1_000_000;
                MessageDigest total = MessageDigest.getInstance("SHA-256");
                List<Association.Op> associations = new ArrayList<>();
                for (Association association : repository.getAssociations())
                    associations.add((Association.Op) association);
                associations.sort(Comparator.comparing(Association::getId));
                int foreign = 0;
                for (Association.Op association : associations) {
                    List<String> lines = new ArrayList<>();
                    lines.add("count " + association.getCounter().getCount());
                    for (ModuleCounter moduleCounter : association.getCounter().getChildren()) {
                        Module module = moduleCounter.getObject();
                        if (module != repository.getModule(module.getPos(), module.getNeg()))
                            foreign++;
                        lines.add("M " + module + " " + moduleCounter.getCount() + " global " + module.getCount());
                        for (ModuleRevisionCounter revisionCounter : moduleCounter.getChildren()) {
                            ModuleRevision revision = revisionCounter.getObject();
                            if (revision != module.getRevision(revision.getPos(), revision.getNeg()))
                                foreign++;
                            lines.add("R " + module + " " + revision + " " + revisionCounter.getCount() + " global " + revision.getCount());
                        }
                    }
                    Condition condition = association.computeCondition();
                    for (Map.Entry<Module, ? extends java.util.Collection<ModuleRevision>> entry : condition.getModules().entrySet())
                        for (ModuleRevision revision : entry.getValue())
                            lines.add("C " + condition.getType() + " " + entry.getKey() + " " + revision);
                    lines.sort(null);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    for (String line : lines)
                        digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
                    String hash = HexFormat.of().formatHex(digest.digest());
                    total.update((association.getId() + " " + hash + "\n").getBytes(StandardCharsets.UTF_8));
                    out.add(work.getFileName() + " " + association.getId() + " " + (lines.size() - 1) + " " + hash);
                }
                String summary = work.getFileName() + " TOTAL associations " + associations.size() + " " + HexFormat.of().formatHex(total.digest());
                out.add(summary);
                System.out.println("FINGERPRINT " + summary + " (loaded in " + loadMs + " ms, heap "
                        + heapMb() + " MB, foreign references " + foreign + ")");
                assertEquals(0, foreign, "counters referring to copies of the repository's modules");
            }
        }
        String file = System.getProperty("ecco.fingerprint.out");
        if (file != null)
            Files.write(Path.of(file), out);
    }

    private static long heapMb() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) >> 20;
    }
}
