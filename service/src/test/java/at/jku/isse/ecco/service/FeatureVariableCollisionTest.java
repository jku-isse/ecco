package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conditions name features and revisions by logic variables with "." and "-" read as "_" (see
 * FeatureLiterals), so "shopping-cart" and "shopping_cart" were one variable - and a checkout of one
 * left out what the other's absence decided, without a warning (formula review 2026-10-15 #2). Names
 * that would share a variable are refused where they enter a repository: a commit, a pull.
 */
public class FeatureVariableCollisionTest {

    /** The case that checked out an empty file: "x" is in shopping-cart without shopping_cart. */
    @Test
    @Timeout(60)
    public void aFeatureWhoseVariableIsTakenIsRefused(@TempDir Path tmp) throws Exception {
        try (EccoService service = init(tmp)) {
            commit(service, tmp.resolve("v1"), "shopping-cart", "x\n");
            EccoException e = assertThrows(EccoException.class, () -> commit(service, tmp.resolve("v2"), "shopping-cart, shopping_cart", "y\n"));
            String messages = messages(e);
            assertTrue(messages.contains("\"shopping_cart\"") && messages.contains("\"shopping-cart\"") && messages.contains("Rename"), messages);
            assertEquals("shopping-cart", featureNames(service));
            assertEquals("x\n", checkout(service, tmp.resolve("out"), "shopping-cart"));
        }
    }

    @Test
    @Timeout(60)
    public void twoNewFeaturesSharingAVariableAreRefused(@TempDir Path tmp) throws Exception {
        try (EccoService service = init(tmp)) {
            assertThrows(EccoException.class, () -> commit(service, tmp.resolve("v1"), "shopping-cart, shopping_cart", "x\n"));
            assertEquals("", featureNames(service));
        }
    }

    /** "A_B.1" and "A.B_1" are both the variable A_B_1: either checkout had both lines. */
    @Test
    @Timeout(60)
    public void revisionsSharingAVariableAreRefused(@TempDir Path tmp) throws Exception {
        try (EccoService service = init(tmp)) {
            commit(service, tmp.resolve("v1"), "A_B.1", "a\n");
            assertThrows(EccoException.class, () -> commit(service, tmp.resolve("v2"), "A.B_1", "b\n"));
            assertEquals("A_B", featureNames(service));
        }
    }

    /** The feature A_1 and the revision A.1 are both the variable A_1. */
    @Test
    @Timeout(60)
    public void aFeatureNamedLikeARevisionIsRefused(@TempDir Path tmp) throws Exception {
        try (EccoService service = init(tmp)) {
            commit(service, tmp.resolve("v1"), "A.1", "a\n");
            assertThrows(EccoException.class, () -> commit(service, tmp.resolve("v2"), "A_1", "b\n"));
        }
        try (EccoService service = init(tmp.resolve("reverse"))) {
            commit(service, tmp.resolve("w1"), "A_1", "b\n");
            assertThrows(EccoException.class, () -> commit(service, tmp.resolve("w2"), "A.1", "a\n"));
        }
    }

    /** What was refused on commit is refused on a pull too: the other repository's features are merged in. */
    @Test
    @Timeout(60)
    public void aPullBringingAFeatureWhoseVariableIsTakenIsRefused(@TempDir Path tmp) throws Exception {
        try (EccoService a = init(tmp.resolve("a")); EccoService b = init(tmp.resolve("b"))) {
            commit(a, tmp.resolve("va"), "shopping-cart", "x\n");
            commit(b, tmp.resolve("vb"), "shopping_cart", "y\n");
            Repository.Op merged = ((Repository.Op) a.getRepository()).copy(((Repository.Op) a.getRepository()).getEntityFactory());
            assertThrows(EccoException.class, () -> merged.merge((Repository.Op) b.getRepository()));
        }
    }

    /** Names that only look alike, new revisions and recommits are not refused. */
    @Test
    @Timeout(60)
    public void distinctVariablesAreAccepted(@TempDir Path tmp) throws Exception {
        try (EccoService service = init(tmp)) {
            commit(service, tmp.resolve("v1"), "car-a", "x\n");
            commit(service, tmp.resolve("v2"), "car-a, car_b", "y\n");
            commit(service, tmp.resolve("v3"), "car-a'", "x\nz\n");
            commit(service, tmp.resolve("v4"), "car-a, car_b", "y\n");
            assertEquals("car-a car_b", featureNames(service));
            assertEquals("x\n", checkout(service, tmp.resolve("out"), firstRevision(service, "car-a")));
        }
    }

    private static String firstRevision(EccoService service, String feature) {
        Feature f = service.getRepository().getFeatures().stream().filter(x -> x.getName().equals(feature)).findFirst().orElseThrow();
        return feature + "." + service.getRepository().getCommits().stream()
                .flatMap(c -> java.util.Arrays.stream(c.getConfiguration().getFeatureRevisions()))
                .filter(r -> r.getFeature().equals(f)).findFirst().orElseThrow().getId();
    }

    /** The commit reports the reason as the cause of "Error during commit." - as the command line and the GUI show it. */
    private static String messages(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause())
            messages.append(t.getMessage()).append(" | ");
        return messages.toString();
    }

    private static EccoService init(Path dir) throws Exception {
        EccoService service = new EccoService();
        service.setRepositoryDir(Files.createDirectories(dir).resolve(".ecco"));
        service.init();
        return service;
    }

    private static void commit(EccoService service, Path dir, String configuration, String text) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), text);
        service.setBaseDir(dir);
        service.commit(configuration, configuration);
    }

    private static String checkout(EccoService service, Path dir, String configuration) throws Exception {
        service.setBaseDir(Files.createDirectories(dir));
        service.checkout(configuration);
        return Files.readString(dir.resolve("f.txt"));
    }

    private static String featureNames(EccoService service) {
        return service.getRepository().getFeatures().stream().map(Feature::getName).sorted().collect(Collectors.joining(" "));
    }
}
