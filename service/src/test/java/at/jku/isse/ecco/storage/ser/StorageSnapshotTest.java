package at.jku.isse.ecco.storage.ser;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a repository holds, pinned: a fixed commit history is dumped as text - features and
 * revisions, commits, and every association with its condition, counters and artifact tree (unique
 * flags, artifacts, properties, the retroactive conditions stored on unique nodes) - with every id
 * numbered by first appearance, since ids are random. The dump must be the same before and after
 * reopening, and the same as src/test/resources/storage-snapshot.txt: a refactoring of the storage
 * that changes what is stored shows up as a difference. When a change is meant to, delete the
 * snapshot, run the test (it writes a new one) and review the diff.
 */
public class StorageSnapshotTest {

    private static final Path SNAPSHOT = Paths.get("src/test/resources/storage-snapshot.txt");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}[-_][0-9a-f]{4}[-_][0-9a-f]{4}[-_][0-9a-f]{4}[-_][0-9a-f]{12}");

    /** Configuration and the files of each commit, in order. */
    private static final String[][] HISTORY = {
            {"BASE", "main.txt", "begin\ncore\nend\n"},
            {"BASE, A", "main.txt", "begin\ncore\na1\nend\n", "a.txt", "only a\n"},
            {"BASE, B", "main.txt", "begin\nb1\ncore\nend\n", "b.md", "# B\n\nText of b.\n"},
            {"BASE, A, B", "main.txt", "begin\nb1\ncore\na1\nend\n", "a.txt", "only a\n", "b.md", "# B\n\nText of b.\n"},
            {"BASE, A'", "main.txt", "begin\ncore\na2\nend\n", "a.txt", "only a, again\n"},
    };

    @Test
    @Timeout(120)
    public void whatIsStoredIsTheSnapshot(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve(".ecco");
        String inSession;
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            int i = 0;
            for (String[] commit : HISTORY) {
                Path variant = Files.createDirectories(tmp.resolve("v" + i++));
                for (int f = 1; f < commit.length; f += 2)
                    Files.writeString(variant.resolve(commit[f]), commit[f + 1]);
                service.setBaseDir(variant);
                service.commit("commit " + i, commit[0]);
            }
            inSession = dump(service);
        }
        String reopened;
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            reopened = dump(service);
        }
        assertEquals(inSession, reopened, "what is stored is what was in memory");

        if (!Files.exists(SNAPSHOT)) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, reopened);
        }
        if (!Files.readString(SNAPSHOT).equals(reopened))
            Files.writeString(Files.createDirectories(Paths.get("build")).resolve("storage-snapshot.actual.txt"), reopened);
        assertEquals(Files.readString(SNAPSHOT), reopened, "the stored content changed (what it is now: build/storage-snapshot.actual.txt) - if that is meant, update the snapshot (see the class comment)");
    }

    /** The repository as text, ids numbered by first appearance. */
    static String dump(EccoService service) {
        StringBuilder out = new StringBuilder();
        Map<String, String> revisions = new HashMap<>(); // revision id -> name@index
        Map<String, String> literals = new HashMap<>(); // a revision's logic variable -> name@index
        List<Feature> features = new ArrayList<>(service.getRepository().getFeatures());
        features.sort(Comparator.comparing(Feature::getName));
        out.append("features\n");
        for (Feature feature : features) {
            List<? extends FeatureRevision> list = new ArrayList<>(feature.getRevisions());
            for (int r = 0; r < list.size(); r++)
            {
                revisions.put(list.get(r).getId(), feature.getName() + "@" + r);
                literals.put((feature.getName() + "." + list.get(r).getId()).replace('.', '_').replace('-', '_'), feature.getName() + "@" + r);
            }
            out.append("  ").append(feature.getName()).append(": ").append(list.size()).append(" revision(s)\n");
        }

        out.append("commits\n");
        for (Commit commit : service.getRepository().getCommits())
            out.append("  ").append(commit.getCommitMessage()).append(": ")
                    .append(Arrays.stream(commit.getConfiguration().getFeatureRevisions()).map(r -> revisions.get(r.getId())).sorted().collect(Collectors.joining(", ")))
                    .append("\n");

        // associations in an order of their content (their ids are random)
        TreeMap<String, String> associations = new TreeMap<>();
        for (Association a0 : service.getRepository().getAssociations()) {
            Association.Op association = (Association.Op) a0;
            StringBuilder a = new StringBuilder("association ").append(association.getId()).append('\n');
            a.append("  condition ").append(condition(association.computeCondition().toLogicString(), literals)).append('\n');
            a.append("  counter ").append(association.getCounter().getCount()).append('\n');
            List<String> modules = new ArrayList<>();
            for (ModuleCounter module : association.getCounter().getChildren())
                for (ModuleRevisionCounter revision : module.getChildren())
                    modules.add("    " + render(revision.getObject(), revisions) + " " + revision.getCount() + "\n");
            modules.sort(null);
            modules.forEach(a::append);
            tree((Node.Op) association.getRootNode(), "  ", a, literals);
            String text = a.toString();
            associations.put(UUID.matcher(text).replaceAll("#") + text, text);
        }
        associations.values().forEach(out::append);
        return numberIds(out.toString());
    }

    private static String render(ModuleRevision revision, Map<String, String> revisions) {
        List<String> literals = new ArrayList<>();
        Arrays.stream(revision.getPos()).map(r -> revisions.get(r.getId())).sorted().forEach(literals::add);
        Arrays.stream(revision.getNeg()).map(f -> "!" + f.getName()).sorted().forEach(literals::add);
        return "[" + String.join(", ", literals) + "]";
    }

    /**
     * A logic string with its revision variables as name@index and its terms in order: toLogicString()
     * orders them by their variables, which hold the random revision ids.
     */
    private static String condition(String logic, Map<String, String> literals) {
        for (Map.Entry<String, String> literal : literals.entrySet())
            logic = logic.replace(literal.getKey(), literal.getValue());
        return Arrays.stream(logic.split(" \\| "))
                .map(term -> Arrays.stream(term.replaceAll("[()]", "").split(" & ")).sorted().collect(Collectors.joining(" & ")))
                .sorted().collect(Collectors.joining(" | "));
    }

    private static void tree(Node.Op node, String indent, StringBuilder out, Map<String, String> literals) {
        out.append(indent).append(node.isUnique() ? "* " : "  ").append(node.getArtifact() == null ? "(root)" : String.valueOf(node.getArtifact()).replace("\n", "\\n"));
        if (node.getArtifact() != null && node.getArtifact().isOrdered())
            out.append(" [ordered]");
        if (!node.getProperties().isEmpty())
            out.append(" ").append(new TreeMap<>(node.getProperties()));
        if (node.isUnique() && node.getFeatureTrace() != null && node.getFeatureTrace().getRetroactiveConditionString() != null)
            out.append(" | ").append(condition(node.getFeatureTrace().getRetroactiveConditionString(), literals));
        out.append('\n');
        for (Node.Op child : node.getChildren())
            tree(child, indent + "  ", out, literals);
    }

    private static String numberIds(String text) {
        Map<String, String> numbers = new HashMap<>();
        Matcher matcher = UUID.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String id = matcher.group().replace('_', '-');
            matcher.appendReplacement(out, numbers.computeIfAbsent(id, k -> "id" + (numbers.size() + 1)));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
