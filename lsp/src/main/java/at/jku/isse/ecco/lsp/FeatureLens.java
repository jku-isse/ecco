package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.view.RenderedSource;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.pog.OrderRegions;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.ArtifactDiagnostics;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Which features each line of a file in a working directory is traced to: the directory's checkout
 * (its {@code .config}) is composed again in memory, the file's content - as an editor holds it,
 * saved or not - is read with its adapter, and its nodes are matched to the composed ones by
 * content, as {@code OrderService#recordOrderOfFiles} does. A matched node has the condition of the
 * association it comes from; one without a match is new or changed since the checkout.
 * <p>
 * The line numbers come from reading the content, not from the composed tree: there, a node keeps
 * the lines it had in the variant that committed it.
 * <p>
 * Not thread-safe: the language server calls it from one thread.
 */
public final class FeatureLens implements AutoCloseable {


    /**
     * One node of the file: lines 1-based, inclusive; condition null where it is not committed;
     * details (Markdown, or null) - the descriptions of its features and the commits it came with.
     */
    public record Span(int startLine, int endLine, String condition, int depth, String details) {
        public Span(int startLine, int endLine, String condition, int depth) {
            this(startLine, endLine, condition, depth, null);
        }

        boolean contains(int line) {
            return this.startLine <= line && line <= this.endLine;
        }

        boolean committed() {
            return this.condition != null;
        }
    }

    /** An ORDER warning of the checkout at lines of this file (1-based, inclusive): the children whose order is undecided. */
    public record OrderWarning(int startLine, int endLine, String message) {
    }

    /**
     * The trace of one file. {@code problem} says why there is none, or why it is incomplete;
     * {@code fileCondition} is the condition of the file itself, for adapters without line positions.
     */
    public record FileTrace(String configuration, String fileCondition, List<Span> spans,
                            List<OrderWarning> orderWarnings, String problem) {

        static FileTrace problem(String configuration, String problem) {
            return new FileTrace(configuration, null, List.of(), List.of(), problem);
        }

        /** The innermost span at [line] (1-based), or null. */
        public Span at(int line) {
            Span best = null;
            for (Span span : this.spans)
                if (span.contains(line) && (best == null || span.endLine - span.startLine < best.endLine - best.startLine
                        || span.endLine - span.startLine == best.endLine - best.startLine && span.depth > best.depth))
                    best = span;
            return best;
        }
    }

    private final Path workingDir;
    private final EccoService service;
    private final Path scratch;

    private String composedFor;
    private Checkout checkout;
    private final Map<Association, String> conditions = new IdentityHashMap<>();
    private final Map<Association, String> details = new IdentityHashMap<>();
    /** The commits each association came with, by its id - read in a transaction of their own - found once per composed checkout (null until asked). */
    private Map<String, List<Commit>> commitsOf;

    /** Opens the repository of [workingDir] (its .ecco directory). */
    public FeatureLens(Path workingDir) throws IOException {
        this.workingDir = workingDir.toAbsolutePath().normalize();
        this.service = new EccoService();
        this.service.setBaseDir(this.workingDir);
        this.service.setRepositoryDir(this.workingDir.resolve(EccoService.REPOSITORY_DIR_NAME));
        this.service.open();
        this.scratch = Files.createTempDirectory("ecco-lsp");
    }

    /** The service of the working directory's repository - for the commands that change it (EccoLanguageServer). */
    EccoService service() {
        return this.service;
    }

    public Path workingDir() {
        return this.workingDir;
    }

    /** The working directory holding [file], walking up from it, or null. */
    public static Path findWorkingDir(Path file) {
        for (Path dir = file.toAbsolutePath().normalize(); dir != null; dir = dir.getParent())
            if (Files.isDirectory(dir.resolve(EccoService.REPOSITORY_DIR_NAME)))
                return dir;
        return null;
    }

    /** Traces [content], the content of [file] (absolute, or relative to the working directory). */
    public FileTrace trace(Path file, String content) {
        Path relative = this.workingDir.relativize(this.workingDir.resolve(file).normalize());
        if (relative.startsWith("..") || relative.startsWith(EccoService.REPOSITORY_DIR_NAME))
            return FileTrace.problem(null, file + " is not in the working directory " + this.workingDir + ".");
        String configuration = this.service.getConfigStringFromFile(this.workingDir).trim();
        if (configuration.isEmpty())
            return FileTrace.problem(null, "The working directory holds no checkout: " + EccoService.CONFIG_FILE_NAME + " is missing or empty.");
        Checkout checkout;
        try {
            checkout = this.checkout(configuration);
        } catch (EccoException e) {
            return FileTrace.problem(configuration, "Could not compose " + configuration + ": " + e.getMessage());
        }
        Node composed = findFile(checkout.getNode(), relative);
        if (composed == null)
            return FileTrace.problem(configuration, relative + " is not part of the checkout of " + configuration + ".");
        Node read;
        try {
            read = this.read(relative, content);
        } catch (IOException | RuntimeException e) {
            return FileTrace.problem(configuration, "Could not read " + relative + ": " + e.getMessage());
        }
        if (read == null)
            return FileTrace.problem(configuration, "Could not read " + relative + ".");
        String composedPlugin = ((PluginArtifactData) composed.getArtifact().getData()).getPluginId();
        String readPlugin = ((PluginArtifactData) read.getArtifact().getData()).getPluginId();
        if (!composedPlugin.equals(readPlugin))
            // e.g. Java that does not parse while it is edited, read by the text adapter instead
            return FileTrace.problem(configuration, relative + " was read by " + readPlugin + ", not by " + composedPlugin
                    + " it was committed with - it may not parse as it is now.");

        // where the nodes are: their own properties, else the adapter's rendering of what was read
        NodeLines lines = NodeLines.ofProperties();
        String noLines = null;
        if (!NodeLines.anyProperties(read)) {
            RenderedSource rendered = this.service.getReader().render(read);
            NodeLines fromRendering = rendered == null ? null : NodeLines.ofRendering(rendered, content);
            if (fromRendering != null)
                lines = fromRendering;
            else
                noLines = rendered == null
                        ? "The " + composedPlugin + " adapter records no line positions: only the file's condition is known."
                        : "The " + composedPlugin + " adapter writes this file differently than it is now, so its lines are not known: only the file's condition is.";
        }

        List<Span> spans = new ArrayList<>();
        Map<Node, Node> readOf = new IdentityHashMap<>();
        this.match(composed, read, 0, spans, readOf, lines);
        List<OrderWarning> warnings = new ArrayList<>();
        for (ArtifactDiagnostics.OrderWarning warning : ArtifactDiagnostics.describeOrderWarnings(checkout.getOrderWarnings())) {
            if (!readOf.containsKey(warning.node()))
                continue; // another file's
            String message = "Order undecided between variants: " + String.join("; ", warning.undecided())
                    + (warning.suggestedFix() == null ? "" : " - " + warning.suggestedFix());
            // at the children whose order is undecided, where they are in this file
            List<? extends Node> children = warning.node().getChildren();
            PartialOrderGraph graph = warning.node().getArtifact() == null ? null : warning.node().getArtifact().getPartialOrderGraph();
            List<OrderRegions.Range> ranges = graph != null ? OrderRegions.undecided(graph, warning.node())
                    : List.of(new OrderRegions.Range(0, children.size() - 1));
            for (OrderRegions.Range range : ranges) {
                int first = Integer.MAX_VALUE, last = -1;
                for (Node child : children.subList(range.from(), range.to() + 1)) {
                    Node readChild = readOf.get(child);
                    if (readChild == null)
                        continue;
                    first = Math.min(first, lines.first(readChild));
                    last = Math.max(last, lines.last(readChild));
                }
                if (last > 0)
                    warnings.add(new OrderWarning(first, last, message));
            }
        }
        String fileCondition = this.condition(composed);
        if (noLines == null && spans.isEmpty() && !read.getChildren().isEmpty())
            noLines = "The " + composedPlugin + " adapter records no line positions: only the file's condition is known.";
        return new FileTrace(configuration, fileCondition, List.copyOf(spans), List.copyOf(warnings), noLines);
    }

    /** The checkout of [configuration], composed again where the repository or the configuration changed. */
    private Checkout checkout(String configuration) {
        String key = configuration + "\n" + this.repositoryStamp();
        if (this.checkout == null || !key.equals(this.composedFor)) {
            this.checkout = this.service.compose(this.service.parseConfigurationString(configuration));
            this.composedFor = key;
            this.conditions.clear();
            this.details.clear();
            this.commitsOf = null;
        }
        return this.checkout;
    }

    /** Changes when a commit - in this process or another - changes the repository's top-level files. */
    private String repositoryStamp() {
        StringBuilder stamp = new StringBuilder();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(this.workingDir.resolve(EccoService.REPOSITORY_DIR_NAME))) {
            List<String> entries = new ArrayList<>();
            for (Path file : files)
                entries.add(file.getFileName() + "@" + Files.getLastModifiedTime(file).toMillis() + ":" + (Files.isRegularFile(file) ? Files.size(file) : 0));
            Collections.sort(entries);
            entries.forEach(e -> stamp.append(e).append(';'));
        } catch (IOException e) {
            stamp.append(System.nanoTime()); // compose again
        }
        return stamp.toString();
    }

    /** [content] read with the adapter the repository maps [relative] to: the file's node. */
    private Node read(Path relative, String content) throws IOException {
        Path copy = this.scratch.resolve(relative);
        Files.createDirectories(copy.getParent() == null ? this.scratch : copy.getParent());
        Files.writeString(copy, content);
        try {
            for (Node root : this.service.getReader().readSpecificFiles(this.scratch, new Path[]{relative})) {
                Node found = findFile(root, relative);
                if (found != null)
                    return found;
            }
            return null;
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    /**
     * Matches the children of [read] to those of [composed] by content, in order; a match has the
     * condition of its association, the rest of the read tree none.
     */
    private void match(Node composed, Node read, int depth, List<Span> spans, Map<Node, Node> readOf, NodeLines lines) {
        readOf.put(composed, read);
        List<? extends Node> composedChildren = composed.getChildren();
        Set<Node> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node readChild : read.getChildren()) {
            Node match = null;
            for (Node composedChild : composedChildren)
                if (!used.contains(composedChild) && composedChild.getArtifact() != null && composedChild.getArtifact().equals(readChild.getArtifact())) {
                    match = composedChild;
                    break;
                }
            if (match == null) {
                uncommitted(readChild, depth + 1, spans, lines);
                continue;
            }
            used.add(match);
            Association association = association(match);
            span(readChild, this.condition(association), this.details(association), depth + 1, lines).ifPresent(spans::add);
            this.match(match, readChild, depth + 1, spans, readOf, lines);
        }
    }

    private static void uncommitted(Node read, int depth, List<Span> spans, NodeLines lines) {
        span(read, null, null, depth, lines).ifPresent(spans::add);
        for (Node child : read.getChildren())
            uncommitted(child, depth + 1, spans, lines);
    }

    private static Optional<Span> span(Node read, String condition, String details, int depth, NodeLines lines) {
        int[] at = lines.of(read);
        return at == null ? Optional.empty() : Optional.of(new Span(at[0], at[1], condition, depth, details));
    }

    /** The association [node] of the composed tree comes from, or null. */
    private static Association association(Node node) {
        Artifact<?> artifact = node.getArtifact();
        Node containing = artifact == null ? null : artifact.getContainingNode();
        return containing == null ? null : containing.getContainingAssociation();
    }

    /** The condition of the association [node] of the composed tree comes from, by features. */
    private String condition(Node node) {
        return this.condition(association(node));
    }

    private String condition(Association association) {
        if (association == null)
            return at.jku.isse.ecco.module.Condition.NOT_TRACED;
        return this.conditions.computeIfAbsent(association, FeatureLens::describe);
    }

    /** Characters of a commit message quoted, as the GUI's ArtifactOrigins. */
    static final int MESSAGE_TEXT = 40;

    /**
     * The descriptions of the features [association]'s condition names, where they have one, and the
     * commits it came with: "3 commits, the last 2026-11-02 'Add search'" - for untraced code all
     * there is to say where it comes from. Markdown; null for none.
     */
    private String details(Association association) {
        if (association == null)
            return null;
        return this.details.computeIfAbsent(association, a -> {
            List<String> parts = new ArrayList<>();
            Map<String, String> descriptions = new TreeMap<>();
            for (ModuleRevision moduleRevision : a.computeCondition().getAbsorbedModuleRevisions())
                for (FeatureRevision revision : moduleRevision.getPos()) {
                    String description = revision.getFeature().getDescription();
                    if (description == null || description.isBlank())
                        description = revision.getDescription();
                    if (description != null && !description.isBlank())
                        descriptions.putIfAbsent(revision.getFeature().getName(), description.trim());
                }
            descriptions.forEach((feature, description) -> parts.add("**" + feature + "**: " + description));
            List<Commit> commits = this.commitsOf().getOrDefault(a.getId(), List.of());
            if (!commits.isEmpty()) {
                Commit last = commits.get(0);
                for (Commit commit : commits)
                    if (commit.getDate() != null && (last.getDate() == null || commit.getDate().after(last.getDate())))
                        last = commit;
                String message = last.getCommitMessage() == null ? "" : last.getCommitMessage().lines().findFirst().orElse("").trim();
                if (message.length() > MESSAGE_TEXT)
                    message = message.substring(0, MESSAGE_TEXT - 1) + "\u2026";
                parts.add((commits.size() == 1 ? "1 commit" : commits.size() + " commits, the last")
                        + (last.getDate() == null ? "" : " " + java.time.LocalDate.ofInstant(last.getDate().toInstant(), java.time.ZoneId.systemDefault()))
                        + (message.isEmpty() ? "" : " '" + message + "'"));
            }
            return parts.isEmpty() ? null : String.join("\n\n", parts);
        });
    }

    private Map<String, List<Commit>> commitsOf() {
        if (this.commitsOf == null) {
            Map<String, List<Commit>> commitsOf = new HashMap<>();
            try {
                for (Commit commit : this.service.getCommits())
                    for (Association association : commit.getAssociations())
                        commitsOf.computeIfAbsent(association.getId(), k -> new ArrayList<>()).add(commit);
            } catch (RuntimeException e) {
                // details only: the hover says what it can without them
                java.util.logging.Logger.getLogger(FeatureLens.class.getName()).log(java.util.logging.Level.FINE, "Could not read the commits", e);
            }
            this.commitsOf = commitsOf;
        }
        return this.commitsOf;
    }

    /** As the GUI's artifact views say it (ArtifactOrigins#condition): the minimized condition where stored. */
    static String describe(Association association) {
        if (association instanceof Association.Op op && op.getMinimizedCondition() != null) {
            String minimized = op.getMinimizedCondition().trim();
            if (!minimized.isEmpty() && !minimized.equals("false"))
                return minimized;
        }
        return association.computeCondition().toDisplayString(false);
    }

    /** The node of [file] (relative to the working directory) below [node]. */
    static Node findFile(Node node, Path file) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData data && file.normalize().equals(data.getPath().normalize()))
            return node;
        for (Node child : node.getChildren()) {
            Node found = findFile(child, file);
            if (found != null)
                return found;
        }
        return null;
    }

    @Override
    public void close() {
        this.service.close();
        try (Stream<Path> walk = Files.walk(this.scratch)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // a temporary directory
        }
    }
}
