package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.ArtifactDiagnostics;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Commit and checkout from the editor, as {@code workspace/executeCommand}s of the language server -
 * one JSON object argument, {@code uri} a file of the working directory - and what the client asks
 * first: the repository's features and committed configurations to suggest, a configuration's
 * constraint violations (advisory, as in the GUI and the command line), what a checkout would
 * replace.
 * <p>
 * A commit commits the working directory as the command line does, then writes the committed
 * configuration into .config - the folder is that variant now, and its lines are traced to it - and
 * records its files in .hashes, so a later checkout may replace them. A checkout replaces the
 * files of the last checkout in place ({@link WorkingDirectory}): refused while anything else is
 * there, and, where files changed since, only with {@code force}.
 */
final class EditorCommands {

    static final String INFO = "ecco.server.info";
    static final String CONSTRAINT_VIOLATIONS = "ecco.server.constraintViolations";
    static final String COMMIT = "ecco.server.commit";
    static final String CHANGES = "ecco.server.changes";
    static final String CHECKOUT = "ecco.server.checkout";
    /** The files differing from the configuration's earlier commit, and the warning for them (FR-M4d). */
    static final String RECOMMIT = "ecco.server.recommitDifferences";
    /** Keeps the order of the file as it is on disk: recorded in the repository (the quick fix of an ORDER warning). */
    static final String RECORD_ORDER = "ecco.server.recordOrder";
    static final List<String> ALL = List.of(INFO, CONSTRAINT_VIOLATIONS, RECOMMIT, COMMIT, CHANGES, CHECKOUT, RECORD_ORDER);
    /** After these, what the open files are traced to changed. */
    static final Set<String> CHANGING = Set.of(COMMIT, CHECKOUT, RECORD_ORDER);

    private EditorCommands() {
    }

    static Object run(String command, FeatureLens lens, JsonObject arguments) {
        EccoService service = lens.service();
        Path dir = lens.workingDir();
        try {
            return switch (command) {
                case INFO -> info(service, dir);
                case CONSTRAINT_VIOLATIONS -> service.checkConstraintViolations(service.parseConfigurationString(string(arguments, "configuration")));
                case RECOMMIT -> recommit(service, string(arguments, "configuration"));
                case COMMIT -> commit(service, dir, string(arguments, "configuration"), string(arguments, "message"));
                case CHANGES -> changes(WorkingDirectory.since(dir));
                case CHECKOUT -> checkout(service, dir, string(arguments, "configuration"),
                        arguments.has("force") && arguments.get("force").getAsBoolean());
                case RECORD_ORDER -> recordOrder(service, dir, string(arguments, "uri"));
                default -> throw EccoLanguageServer.failed("Unknown command: " + command);
            };
        } catch (EccoException | IOException e) {
            StringBuilder message = new StringBuilder(e.getMessage() == null ? e.toString() : e.getMessage());
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause())
                if (cause.getMessage() != null && !message.toString().contains(cause.getMessage()))
                    message.append(" - ").append(cause.getMessage());
            throw EccoLanguageServer.failed(message.toString());
        }
    }

    private static String string(JsonObject arguments, String name) {
        if (!arguments.has(name) || arguments.get(name).isJsonNull())
            throw EccoLanguageServer.failed("Missing argument: " + name);
        return arguments.get(name).getAsString();
    }

    /** The working directory, its configuration, the repository's features and its committed configurations, newest first. */
    private static Map<String, Object> info(EccoService service, Path dir) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("workingDir", dir.toString());
        info.put("configuration", service.getConfigStringFromFile(dir).trim());
        info.put("features", service.getRepository().getFeatures().stream().map(Feature::getName).sorted().toList());
        List<Commit> commits = new ArrayList<>(service.getCommits());
        commits.sort(Comparator.comparing((Commit c) -> c.getDate() == null ? new Date(0) : c.getDate()).reversed());
        Set<String> configurations = new LinkedHashSet<>();
        for (Commit commit : commits)
            configurations.add(commit.getConfiguration().getConfigurationString());
        info.put("configurations", List.copyOf(configurations));
        return info;
    }

    private static Map<String, Object> recommit(EccoService service, String configuration) {
        List<String> differences = service.recommitDifferences(configuration);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("differences", differences);
        result.put("warning", EccoService.describeRecommit(configuration, differences));
        return result;
    }

    private static Map<String, Object> commit(EccoService service, Path dir, String configuration, String message) throws IOException {
        Commit commit = service.commit(message, configuration);
        // the folder is the committed variant now
        Files.writeString(dir.resolve(EccoService.CONFIG_FILE_NAME), commit.getConfiguration().toString());
        Files.deleteIfExists(dir.resolve(EccoService.WARNINGS_FILE_NAME));
        WorkingDirectory.recordCommitted(dir, service.getReader());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commit", commit.getId());
        result.put("configuration", commit.getConfiguration().getConfigurationString());
        return result;
    }

    private static Map<String, Object> changes(WorkingDirectory.Changes changes) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changed", changes.changed());
        result.put("removed", changes.removed());
        result.put("foreign", changes.foreign());
        if (changes.problem() != null)
            result.put("problem", changes.problem());
        return result;
    }

    /**
     * Records the order of the file [uri] as it is in the working directory - only reordered against
     * the checkout, else refused - so the ORDER warnings of its checkout are decided as the file has
     * them, as {@code ecco order} records it: {"added": precedences added, "message": what was done}.
     */
    private static Map<String, Object> recordOrder(EccoService service, Path dir, String uri) {
        Path file = EccoLanguageServer.path(uri);
        if (file == null)
            throw EccoLanguageServer.failed("Not a file: " + uri);
        Path relative = realPath(dir).relativize(realPath(file));
        int added = service.recordOrderOfFiles(List.of(relative));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.put("message", added == 0
                ? "The order of " + relative + " was already recorded."
                : "Recorded the order of " + relative + " (" + added + " new precedence" + (added == 1 ? "" : "s") + ").");
        return result;
    }

    /** [path] with its links resolved: macOS's /var -> /private/var would put every file outside. */
    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /**
     * Checks [configuration] out in place of the last checkout: {"refused": ...} with what stops it,
     * or the configuration checked out and its warnings.
     */
    private static Map<String, Object> checkout(EccoService service, Path dir, String configuration, boolean force) throws IOException {
        // refused before anything is deleted: a typo or a feature never committed
        service.checkCheckoutConfiguration(configuration);
        WorkingDirectory.Changes changes = WorkingDirectory.since(dir);
        if (changes.problem() != null)
            throw EccoLanguageServer.failed(changes.problem());
        if (!changes.foreign().isEmpty() || !changes.unchanged() && !force) {
            Map<String, Object> refused = changes(changes);
            refused.put("refused", changes.foreign().isEmpty() ? "changed" : "foreign");
            return refused;
        }
        WorkingDirectory.clear(dir, changes);
        Checkout checkout = service.checkout(configuration);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configuration", checkout.getConfiguration().getConfigurationString());
        List<String> warnings = new ArrayList<>();
        if (!checkout.getMissing().isEmpty())
            warnings.add(checkout.getMissing().size() + " missing");
        if (!checkout.getSurplusModules().isEmpty())
            warnings.add(checkout.getSurplusModules().size() + " surplus");
        int order = ArtifactDiagnostics.describeOrderWarnings(checkout.getOrderWarnings()).size();
        if (order > 0)
            warnings.add(order + " order");
        if (!checkout.getConflicts().isEmpty())
            warnings.add(checkout.getConflicts().size() + " conflicts");
        result.put("warnings", String.join(", ", warnings));
        return result;
    }
}
