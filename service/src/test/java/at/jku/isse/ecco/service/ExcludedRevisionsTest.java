package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fork, pull and push can leave feature revisions out (FR-D4a): a feature left without revisions is
 * dropped, and so is what only the left-out revisions explain, while the rest checks out as before.
 * A selection that leaves an association without an artifact it depends on is refused (FR-D4b) -
 * but subset() copies each association's tree whole, so only a cross reference between artifacts
 * could be left dangling, and no adapter in use records one: leaving out the feature that brought a
 * file keeps the other features' lines of it.
 */
public class ExcludedRevisionsTest {

    /**
     * commit 1 "A": a.txt "a"; commit 2 "A, B": adds line "b" to a.txt and b.txt; commit 3 "A', B":
     * a.txt's first line is "a2"; commit 4 "A.2" alone: a.txt "a2" - a file whose lines and order
     * several associations hold
     */
    private record Origin(Path dir, String firstRevisionOfA, String secondRevisionOfA, String revisionOfB, String allRevisionsOfA) {
    }

    private static Origin origin(Path tmp) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(origin.resolve(".ecco"));
            service.init();
            service.setBaseDir(origin);
            Files.writeString(origin.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            String firstRevisionOfA = latestRevision(service, "A");
            Files.writeString(origin.resolve("a.txt"), "a\nb\n");
            Files.writeString(origin.resolve("b.txt"), "b\n");
            service.commit("a and b", "A, B");
            Files.writeString(origin.resolve("a.txt"), "a2\nb\n");
            service.commit("a2 and b", "A', B");
            String secondRevisionOfA = latestRevision(service, "A");
            Files.writeString(origin.resolve("a.txt"), "a2\n");
            Files.delete(origin.resolve("b.txt"));
            service.commit("a2", secondRevisionOfA);
            String allRevisionsOfA = revisions(service).get("A").stream().map(r -> "A." + r).collect(Collectors.joining(", "));
            return new Origin(origin, firstRevisionOfA, secondRevisionOfA, latestRevision(service, "B"), allRevisionsOfA);
        }
    }

    @Test
    @Timeout(120)
    public void forkPullAndPushLeaveOutTheExcludedRevisions(@TempDir Path tmp) throws Exception {
        Origin o = origin(tmp);
        Path origin = o.dir();
        String revisionOfB = o.revisionOfB();
        String a1 = o.firstRevisionOfA();
        String a2 = o.secondRevisionOfA();

        // fork without B: B is dropped, both revisions of A stay, and A's first revision checks out as committed
        Path forked = Files.createDirectories(tmp.resolve("forked"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(forked.resolve(".ecco"));
            service.fork(origin, revisionOfB);
        }
        // reopened: the subset must have been stored whole, not only be whole in memory
        try (EccoService service = open(forked)) {
            assertEquals(Set.of("A"), revisions(service).keySet());
            assertEquals(2, revisions(service).get("A").size());
            assertEquals(Map.of("a.txt", "a\n"), checkout(service, a1, tmp.resolve("forked-a1")));
            assertEquals(Map.of("a.txt", "a2\n"), checkout(service, a2, tmp.resolve("forked-a2")));
        }

        // pull without B
        Path pulled = Files.createDirectories(tmp.resolve("pulled"));
        try (EccoService service = init(pulled)) {
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            service.pull("origin", revisionOfB);
        }
        try (EccoService service = open(pulled)) {
            assertEquals(Set.of("A"), revisions(service).keySet());
            assertEquals(Map.of("a.txt", "a\n"), checkout(service, a1, tmp.resolve("pulled-a1")));
        }

        // pull without B into a repository with work of its own: both stay
        Path own = Files.createDirectories(tmp.resolve("own"));
        try (EccoService service = init(own)) {
            Files.writeString(own.resolve("own.txt"), "mine\n");
            service.commit("own", "Own");
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            service.pull("origin", revisionOfB);
        }
        try (EccoService service = open(own)) {
            assertEquals(Set.of("A", "Own"), revisions(service).keySet());
            assertEquals(Map.of("own.txt", "mine\n"), checkout(service, "Own", tmp.resolve("own-own")));
            assertEquals(Map.of("a.txt", "a2\n"), checkout(service, a2, tmp.resolve("own-a2")));
        }

        // push without B, into an empty repository
        Path pushed = Files.createDirectories(tmp.resolve("pushed"));
        try (EccoService service = init(pushed)) {
            // closed again: the push opens it itself
        }
        try (EccoService service = open(origin)) {
            service.addRemote("pushed", pushed.toString(), Remote.Type.LOCAL);
            service.push("pushed", revisionOfB);
        }
        try (EccoService service = open(pushed)) {
            assertEquals(Set.of("A"), revisions(service).keySet());
            assertEquals(Map.of("a.txt", "a\n"), checkout(service, a1, tmp.resolve("pushed-a1")));
        }
    }

    @Test
    @Timeout(120)
    public void leavingOutTheFeatureThatBroughtAFileKeepsTheOtherFeaturesLinesOfIt(@TempDir Path tmp) throws Exception {
        Origin o = origin(tmp);
        Map<String, String> originBefore = DirectoryFingerprint.of(o.dir().resolve(".ecco"));

        Path forked = Files.createDirectories(tmp.resolve("forked"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(forked.resolve(".ecco"));
            service.fork(o.dir(), o.allRevisionsOfA());
        }
        try (EccoService service = open(forked)) {
            assertEquals(Set.of("B"), revisions(service).keySet());
            // a.txt keeps B's line; its first line and its final newline were A's
            Map<String, String> files = checkout(service, o.revisionOfB(), tmp.resolve("forked-b"));
            assertEquals(Set.of("a.txt", "b.txt"), files.keySet());
            assertEquals("b", files.get("a.txt"));
            assertEquals("b\n", files.get("b.txt"));
        }

        assertEquals(originBefore, DirectoryFingerprint.of(o.dir().resolve(".ecco")));
    }

    /**
     * Five commits, four features, one file: 1 "A" a; 2 "A, B" a b; 3 "A', B, C" a2 b c; 4 "A, B', C"
     * a2 b2 c; 5 "A, B, C, D" x a2 b2 c. Leaving out the first revision of a feature, the last commit's
     * feature, or two revisions at once: each fork is reopened and the commits it still explains check
     * out as committed.
     */
    @Test
    @Timeout(180)
    public void leavingOutFirstLastAndSeveralRevisions(@TempDir Path tmp) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        String a1, a2, b1, b2, c1, d1;
        try (EccoService service = init(origin)) {
            Files.writeString(origin.resolve("a.txt"), "a\n");
            service.commit("1", "A");
            a1 = latestRevision(service, "A");
            Files.writeString(origin.resolve("a.txt"), "a\nb\n");
            service.commit("2", "A, B");
            b1 = latestRevision(service, "B");
            Files.writeString(origin.resolve("a.txt"), "a2\nb\nc\n");
            service.commit("3", "A', B, C");
            a2 = latestRevision(service, "A");
            c1 = latestRevision(service, "C");
            Files.writeString(origin.resolve("a.txt"), "a2\nb2\nc\n");
            service.commit("4", "A, B', C");
            b2 = latestRevision(service, "B");
            Files.writeString(origin.resolve("a.txt"), "x\na2\nb2\nc\n");
            service.commit("5", "A, B, C, D");
            d1 = latestRevision(service, "D");
        }
        String commit2 = a1 + ", " + b1, commit3 = a2 + ", " + b1 + ", " + c1, commit4 = a2 + ", " + b2 + ", " + c1;

        // the first revision of A
        Path forked = fork(origin, tmp.resolve("without-a1"), a1);
        try (EccoService service = open(forked)) {
            assertEquals(Set.of("A", "B", "C", "D"), revisions(service).keySet());
            assertEquals(1, revisions(service).get("A").size());
            assertEquals(Map.of("a.txt", "a2\nb\nc\n"), checkout(service, commit3, tmp.resolve("without-a1-3")));
            assertEquals(Map.of("a.txt", "a2\nb2\nc\n"), checkout(service, commit4, tmp.resolve("without-a1-4")));
        }

        // the first revision of B
        forked = fork(origin, tmp.resolve("without-b1"), b1);
        try (EccoService service = open(forked)) {
            assertEquals(1, revisions(service).get("B").size());
            assertEquals(Map.of("a.txt", "a2\nb2\nc\n"), checkout(service, commit4, tmp.resolve("without-b1-4")));
            assertEquals(Map.of("a.txt", "a\n"), checkout(service, a1, tmp.resolve("without-b1-1")));
        }

        // the last commit (its only feature D)
        forked = fork(origin, tmp.resolve("without-d1"), d1);
        try (EccoService service = open(forked)) {
            assertEquals(Set.of("A", "B", "C"), revisions(service).keySet());
            assertEquals(Map.of("a.txt", "a\n"), checkout(service, a1, tmp.resolve("without-d1-1")));
            assertEquals(Map.of("a.txt", "a\nb\n"), checkout(service, commit2, tmp.resolve("without-d1-2")));
            assertEquals(Map.of("a.txt", "a2\nb\nc\n"), checkout(service, commit3, tmp.resolve("without-d1-3")));
            assertEquals(Map.of("a.txt", "a2\nb2\nc\n"), checkout(service, commit4, tmp.resolve("without-d1-4")));
        }

        // two revisions of different features at once
        forked = fork(origin, tmp.resolve("without-a1-b2"), a1 + ", " + b2);
        try (EccoService service = open(forked)) {
            assertEquals(1, revisions(service).get("A").size());
            assertEquals(1, revisions(service).get("B").size());
            assertEquals(Map.of("a.txt", "a2\nb\nc\n"), checkout(service, commit3, tmp.resolve("without-a1-b2-3")));
        }

        // two features at once, the last two that came
        forked = fork(origin, tmp.resolve("without-c1-d1"), c1 + ", " + d1);
        try (EccoService service = open(forked)) {
            assertEquals(Set.of("A", "B"), revisions(service).keySet());
            assertEquals(Map.of("a.txt", "a\n"), checkout(service, a1, tmp.resolve("without-c1-d1-1")));
            assertEquals(Map.of("a.txt", "a\nb\n"), checkout(service, commit2, tmp.resolve("without-c1-d1-2")));
        }
    }

    private static Path fork(Path origin, Path dir, String excluded) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.fork(origin, excluded);
        }
        return dir;
    }

    private static String latestRevision(EccoService service, String feature) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals(feature))
                .findFirst().orElseThrow().getLatestRevision().toString();
    }

    private static EccoService init(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        service.setBaseDir(dir);
        return service;
    }

    private static EccoService open(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.open();
        service.setBaseDir(dir);
        return service;
    }

    /** feature name -> its revision ids */
    private static Map<String, Set<String>> revisions(EccoService service) {
        Map<String, Set<String>> result = new TreeMap<>();
        for (Feature feature : service.getRepository().getFeatures())
            result.put(feature.getName(), feature.getRevisions().stream().map(FeatureRevision::getId).collect(Collectors.toCollection(TreeSet::new)));
        return result;
    }

    /** the files a checkout of the configuration writes, by name, without .config/.warnings/.hashes */
    private static Map<String, String> checkout(EccoService service, String configuration, Path dir) throws Exception {
        Files.createDirectories(dir);
        service.setBaseDir(dir);
        service.checkout(configuration);
        Map<String, String> files = new TreeMap<>();
        try (var paths = Files.list(dir)) {
            for (Path p : paths.toList()) {
                String name = p.getFileName().toString();
                if (!name.startsWith("."))
                    files.put(name, Files.readString(p));
            }
        }
        return files;
    }
}
