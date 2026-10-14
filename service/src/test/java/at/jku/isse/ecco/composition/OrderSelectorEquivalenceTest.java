package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultOrderSelector} must select exactly the order it selected before it looked children up
 * by their artifacts' hash instead of scanning all children for every node of the order graph -
 * which was quadratic in a file's lines (a checkout of a 340,000-line C file took 19 minutes). The
 * implementation from before is kept here as the reference. Random histories of text and C files,
 * built from few distinct lines ("}", "", "x++;" ...) so that many children are equal but for their
 * sequence numbers, are composed from all their associations and from random subsets - with order
 * warnings - once with each selector; every node's children must be the same, in the same order,
 * and the same nodes must be uncertain.
 */
public class OrderSelectorEquivalenceTest {

    /** DefaultOrderSelector.select as it was, scanning the children for every order graph node. */
    private static final class ReferenceSelector implements OrderSelector {
        private final Collection<Node> uncertainOrder = new ArrayList<>();

        @Override
        public Collection<Node> getUncertainOrders() {
            return this.uncertainOrder;
        }

        @Override
        public List<Node> select(Node node) {
            List<Node> orderedChildren = new ArrayList<>();
            PartialOrderGraph graph = node.getArtifact().getPartialOrderGraph();
            boolean uncertainOrder = false;
            Map<PartialOrderGraph.Node, Integer> pogNodesCounter = new HashMap<>();
            Stack<PartialOrderGraph.Node> stack = new Stack<>();
            stack.push(graph.getHead());
            while (!stack.isEmpty()) {
                PartialOrderGraph.Node pogNode = stack.pop();
                if (pogNode.getNext().size() > 1)
                    uncertainOrder = true;
                for (Node childNode : node.getChildren()) {
                    if (childNode.getArtifact().equals(pogNode.getArtifact())) {
                        orderedChildren.add(childNode);
                        break;
                    }
                }
                for (PartialOrderGraph.Node child : pogNode.getNext()) {
                    pogNodesCounter.putIfAbsent(child, 0);
                    int counter = pogNodesCounter.computeIfPresent(child, (op, integer) -> integer + 1);
                    if (counter >= child.getPrevious().size()) {
                        pogNodesCounter.remove(child);
                        stack.push(child);
                    }
                }
            }
            if (uncertainOrder)
                this.uncertainOrder.add(node);
            return orderedChildren;
        }
    }

    private static final String[] COMMON = {"}", "", "{", "x++;", "return x;", "}", "", "    }", "break;"};

    private static List<String> randomLines(Random random, int n) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < n; i++)
            lines.add(random.nextInt(3) == 0 ? "line " + random.nextInt(n) : COMMON[random.nextInt(COMMON.length)]);
        return lines;
    }

    /** One random edit: insert, delete, move or duplicate a few lines. */
    private static List<String> edit(Random random, List<String> lines) {
        List<String> result = new ArrayList<>(lines);
        for (int edits = 1 + random.nextInt(4); edits > 0 && !result.isEmpty(); edits--) {
            int at = random.nextInt(result.size());
            switch (random.nextInt(4)) {
                case 0 -> result.addAll(at, randomLines(random, 1 + random.nextInt(5)));
                case 1 -> result.subList(at, Math.min(result.size(), at + 1 + random.nextInt(4))).clear();
                case 2 -> {
                    String moved = result.remove(at);
                    result.add(random.nextInt(result.size() + 1), moved);
                }
                default -> result.add(at, result.get(random.nextInt(result.size())));
            }
        }
        return result;
    }

    private static String c(List<String> lines) {
        StringBuilder text = new StringBuilder("#include <stdio.h>\n\nint f(int x) {\n");
        for (String line : lines)
            text.append("    ").append(line).append('\n');
        return text.append("    return x;\n}\n\nint g(void) {\n    return 0;\n}\n").toString();
    }

    private static void assertSameOrder(Node expected, Node actual, String where) {
        assertSame(expected.getArtifact(), actual.getArtifact(), where);
        List<? extends Node> e = expected.getChildren();
        List<? extends Node> a = actual.getChildren();
        assertEquals(e.size(), a.size(), where + ": number of children");
        for (int i = 0; i < e.size(); i++)
            assertSameOrder(e.get(i), a.get(i), where + "/" + i);
    }

    private static int touchAll(Node node) {
        int count = 1;
        for (Node child : node.getChildren())
            count += touchAll(child);
        return count;
    }

    @Test
    @Timeout(600)
    public void selectsTheSameOrdersAsBefore(@TempDir Path tmp) throws Exception {
        int compared = 0;
        int uncertain = 0;
        for (int seed = 0; seed < 12; seed++) {
            Random random = new Random(seed);
            Path work = Files.createDirectories(tmp.resolve("repo" + seed));
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(work.resolve(".ecco"));
                service.init();
                List<String> text = randomLines(random, 20 + random.nextInt(60));
                List<String> code = randomLines(random, 10 + random.nextInt(40));
                int commits = 3 + random.nextInt(4);
                for (int i = 0; i < commits; i++) {
                    Path variant = Files.createDirectories(work.resolve("v" + i));
                    Files.writeString(variant.resolve("notes.txt"), String.join("\n", text) + "\n");
                    Files.writeString(variant.resolve("main.c"), c(code));
                    service.setBaseDir(variant);
                    String features = "BASE" + (i % 2 == 0 ? ", EVEN" : "") + ", F" + i;
                    service.commit("v" + i, features);
                    text = edit(random, text);
                    code = edit(random, code);
                }

                List<Association> associations = new ArrayList<>(service.getRepository().getAssociations());
                List<List<Association>> selections = new ArrayList<>();
                selections.add(associations);
                for (int s = 0; s < 6; s++) {
                    List<Association> subset = new ArrayList<>();
                    for (Association association : associations)
                        if (random.nextBoolean())
                            subset.add(association);
                    selections.add(subset);
                }
                for (List<Association> selection : selections) {
                    ReferenceSelector referenceSelector = new ReferenceSelector();
                    DefaultOrderSelector selector = new DefaultOrderSelector();
                    LazyCompositionRootNode expected = new LazyCompositionRootNode(referenceSelector);
                    LazyCompositionRootNode actual = new LazyCompositionRootNode(selector);
                    for (Association association : selection) {
                        expected.addOrigNode(association.getRootNode());
                        actual.addOrigNode(association.getRootNode());
                    }
                    int nodes = touchAll(expected);
                    assertEquals(nodes, touchAll(actual), "seed " + seed);
                    assertSameOrder(expected, actual, "seed " + seed);
                    List<Object> expectedUncertain = referenceSelector.getUncertainOrders().stream().map(Node::getArtifact).map(Object.class::cast).toList();
                    List<Object> actualUncertain = selector.getUncertainOrders().stream().map(Node::getArtifact).map(Object.class::cast).toList();
                    assertEquals(expectedUncertain.size(), actualUncertain.size(), "seed " + seed + ": uncertain orders");
                    for (int i = 0; i < expectedUncertain.size(); i++)
                        assertSame(expectedUncertain.get(i), actualUncertain.get(i), "seed " + seed + ": uncertain order " + i);
                    compared += nodes;
                    uncertain += expectedUncertain.size();
                }
            }
        }
        assertTrue(compared > 5000, "compared " + compared + " nodes");
        assertTrue(uncertain > 0, "some orders are uncertain, so ambiguity is compared too");
    }
}
