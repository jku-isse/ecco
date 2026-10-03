package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.service.OrderResolutionCharacterizationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * EccoService.recordOrder resolves an ambiguous order without a commit (Known gap #23): only the
 * order graph changes, so the configuration is not recorded as a variant and its MISSING and SURPLUS
 * warnings stay. Same two variants as OrderResolutionCharacterizationTest.
 */
public class RecordOrderTest {

    @Test
    @Timeout(60)
    public void recordingAnOrderResolvesItWithoutACommit(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Map<String, String> conditionsBefore = conditions(service);
            Checkout checkout = checkout(service, Files.createDirectories(tmp.resolve("out")), "BASE, A, B");
            Node file = checkout.getOrderWarnings().iterator().next();

            service.recordOrder(file, inOrder(file, "a", "b", "c"));

            assertEquals(2, service.getCommits().size());
            assertEquals(2, service.getRepository().getVariants().size());
            assertEquals(conditionsBefore, conditions(service));

            Path again = Files.createDirectories(tmp.resolve("again"));
            Checkout after = checkout(service, again, "BASE, A, B");
            assertEquals(Map.of("f.txt", "a\nb\nc\n", "g.txt", "x\ny\n"), files(again));
            assertTrue(after.getOrderWarnings().isEmpty());
            assertEquals(1, after.getMissing().size(), "A + B is still reported missing");
            assertEquals(2, after.getSurplusModules().size());

            assertEquals(Map.of("f.txt", "a\nb\n", "g.txt", "x\n"), files(checkoutInto(service, tmp, "BASE, A")));
            assertEquals(Map.of("f.txt", "a\nc\n", "g.txt", "x\ny\n"), files(checkoutInto(service, tmp, "BASE, B")));
        }
    }

    @Test
    @Timeout(60)
    public void theRecordedOrderSurvivesReopeningForkingAndLaterCommits(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        try (EccoService service = twoVariants(tmp)) {
            Checkout checkout = checkout(service, Files.createDirectories(tmp.resolve("out")), "BASE, A, B");
            Node file = checkout.getOrderWarnings().iterator().next();
            // the order the checkout did not pick
            service.recordOrder(file, inOrder(file, "a", "b", "c"));
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo.resolve(".ecco"));
            service.open();
            Path dir = checkoutInto(service, tmp, "BASE, A, B");
            assertEquals("a\nb\nc\n", Files.readString(dir.resolve("f.txt")));
            assertEquals("", Files.readString(dir.resolve(".warnings")).lines().filter(l -> l.startsWith("ORDER")).reduce("", String::concat));

            // a later commit of a variant that agrees with the order keeps it
            commit(service, tmp.resolve("v3"), "a\nb\n", "x\n", "BASE, A");
            assertEquals("a\nb\nc\n", Files.readString(checkoutInto(service, tmp, "BASE, A, B").resolve("f.txt")));
        }

        Path fork = Files.createDirectories(tmp.resolve("fork"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.fork(repo.resolve(".ecco"));
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.open();
            Path dir = checkoutInto(service, tmp, "BASE, A, B");
            assertEquals("a\nb\nc\n", Files.readString(dir.resolve("f.txt")));
        }
    }

    @Test
    @Timeout(60)
    public void anOrderOfSomeChildrenLeavesTheOthersOpen(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            commit(service, tmp.resolve("v3"), "a\nd\n", "x\n", "BASE, C");
            Checkout checkout = checkout(service, Files.createDirectories(tmp.resolve("out")), "BASE, A, B");
            Node file = checkout.getOrderWarnings().iterator().next();
            service.recordOrder(file, inOrder(file, "a", "b", "c"));

            Path all = Files.createDirectories(tmp.resolve("all"));
            Checkout withC = checkout(service, all, "BASE, A, B, C");
            List<String> lines = Files.readString(all.resolve("f.txt")).lines().toList();
            assertEquals("a", lines.get(0));
            assertTrue(lines.indexOf("b") < lines.indexOf("c"), lines.toString());
            assertEquals(1, withC.getOrderWarnings().size(), "where d goes is still open");
        }
    }

    @Test
    @Timeout(60)
    public void anOrderContradictingTheHistoryIsRefusedAndChangesNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Checkout checkout = checkout(service, Files.createDirectories(tmp.resolve("out")), "BASE, A, B");
            Node file = checkout.getOrderWarnings().iterator().next();

            // every commit had a first
            EccoException e = assertThrows(EccoException.class, () -> service.recordOrder(file, inOrder(file, "b", "a", "c")));
            assertTrue(String.valueOf(e.getCause()).contains("contradicts"), String.valueOf(e.getCause()));

            Path again = Files.createDirectories(tmp.resolve("again"));
            Checkout after = checkout(service, again, "BASE, A, B");
            assertEquals(Map.of("f.txt", "a\nc\nb\n", "g.txt", "x\ny\n"), files(again));
            assertEquals(1, after.getOrderWarnings().size());
        }
    }

    @Test
    @Timeout(60)
    public void onlyAnOrderedArtifactOfThisRepositoryTakesAnOrder(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoVariants(tmp)) {
            Checkout checkout = checkout(service, Files.createDirectories(tmp.resolve("out")), "BASE, A, B");
            Node file = checkout.getOrderWarnings().iterator().next();
            Node line = file.getChildren().get(0);
            assertThrows(EccoException.class, () -> service.recordOrder(line, List.of()));
        }
    }

    /** the children of {@code parent} in the given order of their text */
    private static List<Node> inOrder(Node parent, String... texts) {
        return java.util.Arrays.stream(texts)
                .map(text -> parent.getChildren().stream().filter(child -> String.valueOf(child.getArtifact()).equals(text)).findFirst().orElseThrow())
                .map(child -> (Node) child)
                .toList();
    }
}
