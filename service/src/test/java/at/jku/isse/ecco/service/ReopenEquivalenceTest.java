package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A repository reopened is the repository it was: random commit histories, checked out in the
 * session that committed them and again after reopening, give the same files - and every unique
 * node keeps the retroactive condition it had. A commit changes the conditions of associations it
 * does not touch (they depend on how often each feature combination was committed); those once
 * stayed unwritten, and checkouts after reopening went by their old conditions (57 of 180
 * checkouts differed).
 */
public class ReopenEquivalenceTest {

    private static final String[] FEATURES = {"A", "B", "C", "D"};
    private static final String[] FILES = {"a.txt", "b.txt", "c.txt", "d.txt", "e.txt"};

    @Test
    @Timeout(300)
    public void checkoutsAndConditionsAreTheSameAfterReopening(@TempDir Path tmp) throws Exception {
        for (int seed = 0; seed < 30; seed++)
            assertReopenEquivalent(Files.createDirectories(tmp.resolve("seed" + seed)), seed);
    }

    private static void assertReopenEquivalent(Path work, int seed) throws Exception {
        Random random = new Random(seed);
        Path repo = work.resolve(".ecco");
        List<String> configurations = new ArrayList<>();
        Map<String, String> conditionsInSession;
        Map<String, Map<String, String>> inSession = new TreeMap<>();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            for (int i = 0; configurations.size() < 6; i++) {
                List<String> features = new ArrayList<>();
                for (String feature : FEATURES)
                    if (random.nextBoolean())
                        features.add(feature);
                if (features.isEmpty())
                    features.add("A");
                String configuration = String.join(", ", features);
                if (configurations.contains(configuration))
                    continue;
                Path variant = Files.createDirectories(work.resolve("v" + i));
                for (String file : FILES)
                    if (random.nextInt(3) > 0)
                        Files.writeString(variant.resolve(file), file + " line1\n" + (random.nextBoolean() ? "shared\n" : "") + file + " line " + random.nextInt(2) + "\n");
                if (Files.list(variant).findAny().isEmpty())
                    Files.writeString(variant.resolve("a.txt"), "a\n");
                service.setBaseDir(variant);
                service.commit("c" + i, configuration);
                configurations.add(configuration);
            }
            conditionsInSession = conditions(service);
            for (String configuration : configurations)
                inSession.put(configuration, checkout(service, work.resolve("in-session"), configuration));
        }

        Map<String, String> conditionsReopened;
        Map<String, Map<String, String>> reopened = new TreeMap<>();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            conditionsReopened = conditions(service);
            for (String configuration : configurations)
                reopened.put(configuration, checkout(service, work.resolve("reopened"), configuration));
        }

        assertEquals(inSession, reopened, "seed " + seed + ": the checkouts");
        assertEquals(conditionsInSession, conditionsReopened, "seed " + seed + ": the unique nodes' retroactive conditions");
    }

    /** Per association: its condition, and the retroactive conditions of its unique nodes. */
    private static Map<String, String> conditions(EccoService service) {
        Map<String, String> conditions = new TreeMap<>();
        for (Association association : service.getRepository().getAssociations()) {
            Set<String> retroactive = new TreeSet<>();
            collect((Node.Op) association.getRootNode(), retroactive);
            conditions.put(association.getId(), association.computeCondition().toLogicString() + " " + retroactive);
        }
        return conditions;
    }

    private static void collect(Node.Op node, Set<String> retroactive) {
        if (node.isUnique() && node.getFeatureTrace() != null)
            retroactive.add(String.valueOf(node.getFeatureTrace().getRetroactiveConditionString()));
        for (Node.Op child : node.getChildren())
            collect(child, retroactive);
    }

    private static Map<String, String> checkout(EccoService service, Path root, String configuration) throws Exception {
        Path out = Files.createDirectories(root.resolve(configuration.replaceAll("[^A-Za-z]", "_")));
        service.setBaseDir(out);
        service.checkout(configuration);
        Map<String, String> files = new LinkedHashMap<>();
        try (var walk = Files.walk(out)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                String relative = out.relativize(path).toString();
                if (!relative.startsWith("."))
                    files.put(relative, Files.readString(path));
            }
        }
        return files;
    }
}
