package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How an ambiguous order arises and how it can be resolved by committing, pinned down before order
 * resolution got a way of its own (Known gap #23). Two variants each add a line after "a" in f.txt;
 * checking out both features leaves b and c in an undetermined order.
 * <ul>
 * <li>Committing the whole reordered checkout fixes the order, but also records the configuration as
 * a variant, so its MISSING and SURPLUS warnings disappear without anything being checked.</li>
 * <li>Committing only the reordered file is worse: a commit is always a whole variant, so the files
 * left out disappear from later checkouts of that configuration.</li>
 * </ul>
 */
public class OrderResolutionCharacterizationTest {

    /** commits BASE,A {f: a b; g: x} and BASE,B {f: a c; g: x y} */
    static EccoService twoVariants(Path tmp) throws Exception {
        Path repo = Files.createDirectories(tmp.resolve("repo"));
        EccoService service = new EccoService();
        service.setRepositoryDir(repo.resolve(".ecco"));
        service.init();
        commit(service, tmp.resolve("v1"), "a\nb\n", "x\n", "BASE, A");
        commit(service, tmp.resolve("v2"), "a\nc\n", "x\ny\n", "BASE, B");
        return service;
    }

    @Test
    @Timeout(60)
    public void checkingOutBothFeaturesLeavesTheOrderOfTheirLinesOpen(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Path out = Files.createDirectories(tmp.resolve("out"));
            Checkout checkout = checkout(service, out, "BASE, A, B");

            assertEquals(Map.of("f.txt", "a\nc\nb\n", "g.txt", "x\ny\n"), files(out));
            assertEquals(1, checkout.getOrderWarnings().size());
            Node node = checkout.getOrderWarnings().iterator().next();
            assertTrue(node.getArtifact().toString().startsWith("f.txt"));
            assertTrue(node.getArtifact().getPartialOrderGraph().hasUnresolvedOrder(node));
            assertEquals(1, checkout.getMissing().size());
            assertEquals(2, checkout.getSurplusModules().size());
        }
    }

    @Test
    @Timeout(60)
    public void committingTheWholeReorderedCheckoutFixesTheOrderAndHidesTheOtherWarnings(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Map<String, String> conditionsBefore = conditions(service);
            Path out = Files.createDirectories(tmp.resolve("out"));
            checkout(service, out, "BASE, A, B");

            // what the GUI's fix did: reorder the file in the checkout, commit the checkout directory
            Files.writeString(out.resolve("f.txt"), "a\nb\nc\n");
            service.setBaseDir(out);
            service.commit("fix", "BASE, A, B");

            assertEquals(3, service.getCommits().size());
            assertEquals(3, service.getRepository().getVariants().size(), "the configuration is now a committed variant");
            assertEquals(conditionsBefore, conditions(service));

            Path again = Files.createDirectories(tmp.resolve("again"));
            Checkout checkout = checkout(service, again, "BASE, A, B");
            assertEquals(Map.of("f.txt", "a\nb\nc\n", "g.txt", "x\ny\n"), files(again));
            assertTrue(checkout.getOrderWarnings().isEmpty());
            assertTrue(checkout.getMissing().isEmpty(), "A + B is no longer reported missing, though nothing was added for it");
            assertTrue(checkout.getSurplusModules().isEmpty());

            assertEquals(Map.of("f.txt", "a\nb\n", "g.txt", "x\n"), files(checkoutInto(service, tmp, "BASE, A")));
            assertEquals(Map.of("f.txt", "a\nc\n", "g.txt", "x\ny\n"), files(checkoutInto(service, tmp, "BASE, B")));
        }
    }

    @Test
    @Timeout(60)
    public void committingOnlyTheReorderedFileDropsTheOtherFilesFromThatConfiguration(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Path fix = Files.createDirectories(tmp.resolve("fix"));
            Files.writeString(fix.resolve("f.txt"), "a\nb\nc\n");
            service.setBaseDir(fix);
            service.commit("fix", "BASE, A, B");

            assertEquals(Map.of("f.txt", "a\nb\nc\n"), files(checkoutInto(service, tmp, "BASE, A, B")), "g.txt is gone");
            assertEquals(Map.of("f.txt", "a\nb\n", "g.txt", "x\n"), files(checkoutInto(service, tmp, "BASE, A")));
        }
    }

    static Path checkoutInto(EccoService service, Path tmp, String config) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("checkout-" + config.replace(", ", "-") + "-" + System.nanoTime()));
        checkout(service, dir, config);
        return dir;
    }

    static Map<String, String> conditions(EccoService service) {
        Map<String, String> result = new TreeMap<>();
        for (Association a : service.getRepository().getAssociations())
            result.put(content(a.getRootNode()), a.computeCondition().getSimpleModuleRevisionConditionString());
        return result;
    }

    static String content(Node node) {
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private static void collect(Node node, StringBuilder sb) {
        if (node.getArtifact() != null && node.isUnique()) sb.append(node.getArtifact()).append(';');
        for (Node child : node.getChildren()) collect(child, sb);
    }

    static void commit(EccoService service, Path dir, String f, String g, String config) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), f);
        Files.writeString(dir.resolve("g.txt"), g);
        service.setBaseDir(dir);
        service.commit("m", config);
    }

    static Checkout checkout(EccoService service, Path dir, String config) {
        service.setBaseDir(dir);
        return service.checkout(config);
    }

    static Map<String, String> files(Path dir) throws Exception {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(p -> !p.getFileName().toString().startsWith("."))
                    .collect(Collectors.toMap(p -> p.getFileName().toString(), p -> {
                        try { return Files.readString(p); } catch (Exception e) { throw new RuntimeException(e); }
                    }, (a, b) -> a, TreeMap::new));
        }
    }
}
