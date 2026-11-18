package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.*;

/**
 * Whether a commit would commit a configuration again with other content (FR-M4d). A configuration
 * determines its variant: committed a second time with lines changed, the lines either commit had
 * only can no longer be told apart by it, and its checkout keeps only what both share - the changed
 * lines of neither, without a warning. A Git import that kept the configuration over hundreds of
 * commits checked out a file's header comment for its newest one. A changed feature is committed as a
 * new revision (F') instead; the front ends warn with this before they commit.
 */
final class RecommitCheck {

    private final ServiceState state;
    private final CheckoutService checkoutService;
    private final CommitService commitService;

    RecommitCheck(ServiceState state, CheckoutService checkoutService, CommitService commitService) {
        this.state = state;
        this.checkoutService = checkoutService;
        this.commitService = commitService;
    }

    /**
     * The files of [directory] (relative paths, sorted) whose content differs from what checking out
     * [configuration] gives - also those only one of them has - when [configuration] was committed
     * before; empty when it was not, or nothing differs. The order of lines is not compared: a new
     * order is an ORDER warning, not lost lines.
     */
    List<String> differences(Configuration configuration, Path directory) {
        if (!this.committedBefore(configuration))
            return List.of();
        Map<String, Node> composed = new TreeMap<>();
        files(this.checkoutService.compose(configuration).getNode(), composed);
        Map<String, Node> read = new TreeMap<>();
        for (Node root : this.commitService.readFiles(directory))
            files(root, read);
        SortedSet<String> differ = new TreeSet<>();
        for (String file : composed.keySet())
            if (!read.containsKey(file) || !sameContent(composed.get(file), read.get(file)))
                differ.add(file);
        for (String file : read.keySet())
            if (!composed.containsKey(file))
                differ.add(file);
        return List.copyOf(differ);
    }

    /** Whether a commit has [configuration] - cheap: nothing is composed. */
    boolean committedBefore(Configuration configuration) {
        RepositoryContext context = this.state.context();
        return context.read(null, () -> {
            for (Commit commit : context.repositoryDao.load().getCommits())
                if (commit.getConfiguration().equals(configuration))
                    return true;
            return false;
        });
    }

    /** The warning for [files] differing from [configuration]'s checkout, or "" for none. */
    static String describe(String configuration, List<String> files) {
        if (files.isEmpty())
            return "";
        int shown = Math.min(files.size(), 5);
        return configuration + " was committed before with other content: " + (files.size() == 1 ? "1 file differs" : files.size() + " files differ")
                + " (" + String.join(", ", files.subList(0, shown)) + (files.size() > shown ? ", ..." : "") + ")."
                + " Committed again, the lines that differ check out for it no more - neither its old nor its new ones."
                + " Commit the features you changed as new revisions (F') instead.";
    }

    private static void files(Node node, Map<String, Node> files) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data) {
            files.put(data.getPath().toString(), node);
            return;
        }
        for (Node child : node.getChildren())
            files(child, files);
    }

    /** The same children, each with the same content, whatever their order. */
    private static boolean sameContent(Node a, Node b) {
        List<? extends Node> left = a.getChildren(), right = b.getChildren();
        if (left.size() != right.size())
            return false;
        boolean[] used = new boolean[right.size()];
        for (Node child : left) {
            boolean found = false;
            for (int i = 0; i < right.size() && !found; i++)
                if (!used[i] && Objects.equals(child.getArtifact(), right.get(i).getArtifact()) && sameContent(child, right.get(i))) {
                    used[i] = true;
                    found = true;
                }
            if (!found)
                return false;
        }
        return true;
    }
}
