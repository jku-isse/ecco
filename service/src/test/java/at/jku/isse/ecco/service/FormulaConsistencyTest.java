package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.logic.LogicUtils;
import at.jku.isse.ecco.module.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The forms a condition and a configuration take agree with each other: an association's condition
 * holds for a configuration exactly when its logic string - what is stored on its nodes and what a
 * checkout evaluates - is true for the configuration's assignment; and a configuration printed (as
 * the .config file and the views show it) parses back to the same feature revisions.
 */
public class FormulaConsistencyTest {

    private static final String[] FEATURES = {"A", "B", "C", "D"};

    @Test
    @Timeout(300)
    public void holdsAgreesWithTheLogicStringForEveryConfiguration(@TempDir Path tmp) throws Exception {
        for (int seed = 0; seed < 10; seed++) {
            Path work = Files.createDirectories(tmp.resolve("seed" + seed));
            try (EccoService service = history(work, new Random(seed))) {
                for (String configuration : configurations(service)) {
                    Configuration parsed = service.parseConfigurationString(configuration);
                    for (Association association : service.getRepository().getAssociations()) {
                        Condition condition = association.computeCondition();
                        boolean holds = condition.holds(parsed);
                        boolean evaluates = LogicUtils.parseString(condition.toLogicString()).evaluate(parsed.toAssignment());
                        assertEquals(holds, evaluates, "seed " + seed + ", [" + configuration + "], " + condition.toLogicString());
                    }
                }
            }
        }
    }

    @Test
    @Timeout(300)
    public void aPrintedConfigurationParsesBackToTheSameRevisions(@TempDir Path tmp) throws Exception {
        for (int seed = 0; seed < 5; seed++) {
            Path work = Files.createDirectories(tmp.resolve("seed" + seed));
            try (EccoService service = history(work, new Random(seed))) {
                List<Configuration> printed = new ArrayList<>();
                for (Commit commit : service.getRepository().getCommits())
                    printed.add(commit.getConfiguration());
                service.getRepository().getVariants().forEach(v -> printed.add(v.getConfiguration()));
                for (Configuration configuration : printed) {
                    String text = configuration.toString();
                    assertEquals(ids(configuration), ids(service.parseConfigurationString(text)), "seed " + seed + ": " + text);
                }
            }
        }
    }

    /**
     * A configuration with a revision of a negated feature the condition was not stored with - "B'",
     * a new revision - is not selected (holds() false), and the logic string agrees: it negates the
     * feature, not the revisions it knew (formula review 2026-10-15 #1, decided for holds()).
     */
    @Test
    @Timeout(60)
    public void aNewRevisionOfANegatedFeature(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            commit(service, tmp.resolve("v1"), "A", "a\nwithout b\n"); // "without b": A and not B
            commit(service, tmp.resolve("v2"), "A, B", "a\nb\n");
            Configuration newB = service.parseConfigurationString("A, B'");
            for (Association association : service.getRepository().getAssociations()) {
                Condition condition = association.computeCondition();
                assertEquals(condition.holds(newB), LogicUtils.parseString(condition.toLogicString()).evaluate(newB.toAssignment()), condition.toLogicString());
            }
        }
    }

    /** Six commits of random configurations over A-D, each with its own text. */
    private static EccoService history(Path work, Random random) throws Exception {
        EccoService service = new EccoService();
        service.setRepositoryDir(work.resolve(".ecco"));
        service.init();
        List<String> committed = new ArrayList<>();
        for (int i = 0; committed.size() < 6; i++) {
            List<String> features = new ArrayList<>();
            for (String feature : FEATURES)
                if (random.nextBoolean())
                    features.add(feature);
            if (features.isEmpty() || committed.contains(String.join(", ", features)))
                continue;
            String configuration = String.join(", ", features);
            StringBuilder text = new StringBuilder("common\n");
            for (String feature : features)
                text.append(feature.toLowerCase()).append(random.nextInt(2)).append('\n');
            commit(service, work.resolve("v" + i), configuration, text.toString());
            committed.add(configuration);
        }
        return service;
    }

    /** Every combination of the features, each at its latest revision, and every committed configuration. */
    private static List<String> configurations(EccoService service) {
        List<String> configurations = new ArrayList<>();
        for (int mask = 1; mask < 1 << FEATURES.length; mask++) {
            List<String> features = new ArrayList<>();
            for (int f = 0; f < FEATURES.length; f++)
                if ((mask & 1 << f) != 0)
                    features.add(FEATURES[f]);
            configurations.add(String.join(", ", features));
        }
        for (Commit commit : service.getRepository().getCommits())
            configurations.add(commit.getConfiguration().toString());
        return configurations;
    }

    private static void commit(EccoService service, Path dir, String configuration, String text) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), text);
        service.setBaseDir(dir);
        service.commit(configuration, configuration);
    }

    private static List<String> ids(Configuration configuration) {
        return Arrays.stream(configuration.getFeatureRevisions()).map(FeatureRevision::getId).sorted().toList();
    }
}
