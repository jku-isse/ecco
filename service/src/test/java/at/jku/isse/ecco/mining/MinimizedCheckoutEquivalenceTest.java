package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checking out with minimized conditions (EccoService#setMinimizedConditionsInCheckout) must write
 * exactly the files a normal checkout writes, for every configuration the trusted accepted
 * constraints allow - including an older revision of a feature. PresenceConditionMinimizerCheckout-
 * EquivalenceTest only compared which associations are selected; the files come from the main
 * tree's node conditions, which is what is replaced here, so this compares the files themselves.
 */
public class MinimizedCheckoutEquivalenceTest {

    // feature -> lines of main.txt it adds; "A&C" is an interaction
    private static final String[][] MAIN = {{"start", null}, {"a-line", "A"}, {"b-line", "B"}, {"ac-line", "A&C"}, {"c-line", "C"}, {"d-line", "D"}, {"end", null}};

    @Test
    @Timeout(300)
    public void minimizedCheckoutsWriteTheSameFiles(@TempDir Path tmp) throws IOException {
        Path wd = Files.createDirectories(tmp.resolve("wd"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            service.setBaseDir(wd);

            // B requires A (4 witnesses), C and D exclude each other, BASE is in every variant
            String[][] variants = {{"BASE"}, {"BASE", "A"}, {"BASE", "A", "B"}, {"BASE", "A", "C"}, {"BASE", "A", "B", "C"}, {"BASE", "C"},
                    {"BASE", "D"}, {"BASE", "A", "D"}, {"BASE", "A", "B", "D"}, {"BASE", "A", "B", "D"}, {"BASE", "C"}, {"BASE", "A", "C"}};
            for (String[] variant : variants)
                commitVariant(service, wd, Set.of(variant), false, String.join(", ", variant));
            String oldA = feature(service, "A").getLatestRevision().toString();
            // a second revision of A, which changes a.txt
            commitVariant(service, wd, Set.of("BASE", "A", "B"), true, "BASE, A', B");
            String newA = feature(service, "A").getLatestRevision().toString();
            assertNotEquals(oldA, newA);

            List<ConstraintMiner.Suggestion> hard = new ConstraintMiner(4, 1.0, null).mine(ConfigurationBridge.readConfigurations(service))
                    .stream().filter(ConstraintMiner.Suggestion::isHard).toList();
            service.acceptConstraints(hard);
            assertFalse(service.acceptedSuggestions(service.getRepository()).isEmpty(), "the test needs trusted constraints");

            assertEquals(service.getRepository().getAssociations().size(), service.minimizeConditionsForCheckout());
            Map<String, String> valid = service.validCheckoutConditions();
            assertEquals(service.getRepository().getAssociations().size(), valid.size());
            assertTrue(service.getRepository().getAssociations().stream().anyMatch(a -> valid.get(a.getId()).length() < a.computeCondition().toLogicString().length()),
                    "at least one condition must actually get simpler, or the comparison proves nothing");

            // every model-consistent combination, with either revision of A, plus the committed ones
            Set<String> configurations = new LinkedHashSet<>();
            for (int mask = 0; mask < 16; mask++) {
                boolean a = (mask & 1) != 0, b = (mask & 2) != 0, c = (mask & 4) != 0, d = (mask & 8) != 0;
                if ((b && !a) || (c && d)) continue;
                for (String aRevision : a ? List.of(oldA, newA) : List.of("")) {
                    List<String> features = new ArrayList<>(List.of("BASE"));
                    if (a) features.add(aRevision);
                    if (b) features.add("B");
                    if (c) features.add("C");
                    if (d) features.add("D");
                    configurations.add(String.join(", ", features));
                }
            }

            int index = 0;
            for (String configuration : configurations) {
                service.setMinimizedConditionsInCheckout(false);
                Map<String, String> normal = checkout(service, tmp.resolve("normal-" + index), configuration);
                service.setMinimizedConditionsInCheckout(true);
                Map<String, String> minimized = checkout(service, tmp.resolve("minimized-" + index), configuration);
                assertEquals(normal, minimized, configuration);
                index++;
            }
            assertTrue(index >= 15, "configurations checked: " + index);

            // a new, distinct configuration: nothing stored is trusted any more, so checkout uses the
            // associations' own conditions again
            commitVariant(service, wd, Set.of("BASE", "B"), false, "BASE, B");
            assertTrue(service.validCheckoutConditions().isEmpty());
        }
    }

    private static void commitVariant(EccoService service, Path wd, Set<String> features, boolean secondRevisionOfA, String configuration) throws IOException {
        try (Stream<Path> paths = Files.list(wd)) {
            for (Path p : paths.toList()) Files.delete(p);
        }
        Files.write(wd.resolve("base.txt"), List.of("base1", "base2"));
        List<String> main = new ArrayList<>();
        for (String[] line : MAIN) {
            String condition = line[1];
            boolean present = condition == null || Arrays.stream(condition.split("&")).allMatch(features::contains);
            if (present) main.add(line[0]);
        }
        Files.write(wd.resolve("main.txt"), main);
        if (features.contains("A"))
            Files.write(wd.resolve("a.txt"), secondRevisionOfA ? List.of("a1", "a2-new") : List.of("a1"));
        service.commit(configuration, configuration);
    }

    private static Map<String, String> checkout(EccoService service, Path dir, String configuration) throws IOException {
        Files.createDirectories(dir);
        service.setBaseDir(dir);
        service.checkout(configuration);
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            // .hashes records absolute paths and the time, so it differs between any two checkouts
            for (Path p : paths.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals(".hashes")).toList())
                files.put(dir.relativize(p).toString(), Files.readString(p));
        }
        return files;
    }

    private static Feature feature(EccoService service, String name) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
    }
}
