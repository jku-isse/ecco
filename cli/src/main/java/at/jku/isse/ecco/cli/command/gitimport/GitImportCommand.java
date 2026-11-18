package at.jku.isse.ecco.cli.command.gitimport;

import at.jku.isse.ecco.cli.command.CliException;
import at.jku.isse.ecco.cli.command.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.gitimport.GitImport;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Imports the history of a local Git clone into the repository, one ECCO commit per Git commit
 * (first parents, oldest first), as Import from Git does unattended: each commit with the features
 * imported so far and, with --llm, those the LLM suggests (the first into an empty repository as
 * BASE); an LLM server not on this machine only with --llm-allow-remote (or once confirmed in the
 * GUI). Nobody reviews here: a commit the dialog would stop at - its suggestion failed or is
 * uncertain - ends the run (--on-review stop, the default), is left out (skip), or is imported with
 * the configuration proposed (import) - but not one whose configuration was imported before (FR-G3f).
 * A person names the features of such commits in a file (--configurations), as on the GUI's review
 * screen: a line "COMMIT: CONFIGURATION" per commit, which is imported with that configuration and
 * not asked about. The import log is printed as it goes, the summary at the end.
 */
public class GitImportCommand implements Command {
    public static final String GIT_IMPORT = "git-import";
    public static final String CLONE_KEY = "clone";
    public static final String FLAG_FROM = "--from";
    public static final String FLAG_TO = "--to";
    public static final String FLAG_TAGGED_ONLY = "--tagged-only";
    public static final String FLAG_LLM = "--llm";
    public static final String FLAG_LLM_ENDPOINT = "--llm-endpoint";
    public static final String FLAG_LLM_MODEL = "--llm-model";
    public static final String FLAG_LLM_ALLOW_REMOTE = "--llm-allow-remote";
    public static final String FLAG_LLM_REASONING = "--llm-reasoning";
    public static final String FLAG_ON_REVIEW = "--on-review";
    public static final String FLAG_CONFIGURATIONS = "--configurations";
    public static final String ON_REVIEW_STOP = "stop";
    public static final String ON_REVIEW_SKIP = "skip";
    public static final String ON_REVIEW_IMPORT = "import";

    private final EccoService eccoService;
    private final OutWriter writer;
    /** Asks for a commit's suggestion; replaced by tests. */
    private final Function<GitImport, Function<GitCommitInfo, GitImport.Suggested>> suggester;

    public GitImportCommand(EccoService eccoService, OutWriter writer, Function<GitImport, Function<GitCommitInfo, GitImport.Suggested>> suggester) {
        this.eccoService = eccoService;
        this.writer = writer;
        this.suggester = suggester;
    }

    public GitImportCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter(), run -> run::requestSuggestion);
    }

    @Override
    public void run(Namespace namespace) {
        Object startDir = namespace.get(ProgramConstants.START_DIR);
        Path clone = (startDir instanceof Path path ? path : Path.of("")).resolve(namespace.getString(CLONE_KEY)).toAbsolutePath().normalize();
        if (!Files.isDirectory(clone.resolve(".git")))
            throw new CliException("Not a Git clone: " + clone);
        boolean useLlm = Boolean.TRUE.equals(namespace.getBoolean(stripDashes(FLAG_LLM)));
        String endpoint = namespace.getString(stripDashes(FLAG_LLM_ENDPOINT));
        String model = namespace.getString(stripDashes(FLAG_LLM_MODEL));
        if (endpoint != null)
            System.setProperty(LlmPreferences.ENDPOINT_URL_PROPERTY, endpoint);
        if (model != null)
            System.setProperty(LlmPreferences.MODEL_NAME_PROPERTY, model);
        if (Boolean.TRUE.equals(namespace.getBoolean(stripDashes(FLAG_LLM_REASONING))))
            System.setProperty(LlmPreferences.REASONING_PROPERTY, "true");
        if (useLlm && LlmPreferences.getModelName().isBlank())
            throw new CliException("No LLM model to ask: give one with " + FLAG_LLM_MODEL + " (or in the GUI's Preferences, LLM).");
        // code, diffs and commit messages go to the server: another machine's only once agreed to
        // (here, or confirmed in the GUI) - nobody is asked in an unattended run
        if (useLlm && !LlmPreferences.mayReceiveCode(LlmPreferences.getEndpointUrl())
                && !Boolean.TRUE.equals(namespace.getBoolean(stripDashes(FLAG_LLM_ALLOW_REMOTE))))
            throw new CliException(LlmPreferences.remoteEndpointWarning(LlmPreferences.getEndpointUrl())
                    + " To send them, give " + FLAG_LLM_ALLOW_REMOTE + ".");
        boolean taggedOnly = Boolean.TRUE.equals(namespace.getBoolean(stripDashes(FLAG_TAGGED_ONLY)));
        String onReview = namespace.getString(stripDashes(FLAG_ON_REVIEW));

        GitHistoryReader reader = new GitHistoryReader();
        Map<String, List<String>> tags = reader.listTags(clone);
        List<GitCommitInfo> commits = range(reader.listCommits(clone), tags, namespace.getString(stripDashes(FLAG_FROM)), namespace.getString(stripDashes(FLAG_TO)));
        if (taggedOnly) {
            int last = GitImport.lastTagged(commits, tags);
            if (last < 0)
                throw new CliException("The commits to import have no tag: import a range with a tagged commit, or leave out " + FLAG_TAGGED_ONLY + ".");
            // those after the last tag would only be suggested, for no commit
            commits = commits.subList(0, last + 1);
        }
        List<GitCommitInfo> toImport = commits;
        String file = namespace.getString(stripDashes(FLAG_CONFIGURATIONS));
        Map<Integer, String> named = file == null ? Map.of()
                : configurations((startDir instanceof Path path ? path : Path.of("")).resolve(file), reader.listCommits(clone), toImport, tags);
        Command.withOpenRepository(this.eccoService, () -> this.importCommits(clone, reader, tags, taggedOnly, toImport, useLlm, onReview, named));
    }

    /**
     * The configurations [file] names, by the index in [toImport] of their commit: a line
     * "COMMIT: CONFIGURATION" each - COMMIT as {@link #FLAG_FROM} takes it, an id, its beginning or a
     * tag (Git allows no ":" in one) - blank lines and those starting with "#" left out. A commit of
     * the clone outside the range is left out too; one the clone does not have, named twice, or a line
     * without a configuration, is refused before anything is imported.
     */
    static Map<Integer, String> configurations(Path file, List<GitCommitInfo> newestFirst, List<GitCommitInfo> toImport, Map<String, List<String>> tags) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new CliException(FLAG_CONFIGURATIONS + " " + file + ": cannot be read - " + e.getMessage());
        }
        List<GitCommitInfo> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        Map<Integer, String> named = new HashMap<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).strip();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            String where = FLAG_CONFIGURATIONS + " " + file.getFileName() + ", line " + (n + 1);
            int colon = line.indexOf(':');
            if (colon <= 0 || line.substring(colon + 1).isBlank())
                throw new CliException(where + ": not \"COMMIT: CONFIGURATION\" - " + line);
            String id = oldestFirst.get(indexOf(oldestFirst, tags, line.substring(0, colon).strip(), where + ":")).getId();
            for (int i = 0; i < toImport.size(); i++)
                if (toImport.get(i).getId().equals(id) && named.put(i, line.substring(colon + 1).strip()) != null)
                    throw new CliException(where + ": " + toImport.get(i).getShortId() + " is named twice.");
        }
        return named;
    }

    private void importCommits(Path clone, GitHistoryReader reader, Map<String, List<String>> tags, boolean taggedOnly,
                               List<GitCommitInfo> commits, boolean useLlm, String onReview, Map<Integer, String> named) {
        // nobody reviews: each commit unattended, stopping (as --on-review says) where the dialog would
        GitImport run = new GitImport(this.eccoService, reader, clone, tags, taggedOnly, commits, useLlm, false, true, line -> this.writer.printf("%s", line));
        Function<GitCommitInfo, GitImport.Suggested> suggest = this.suggester.apply(run);
        run.start();
        run.continueFromLatestCommit();
        GitImport.Step step = run.next(0);
        while (!(step instanceof GitImport.Finished)) {
            Integer index = switch (step) {
                case GitImport.Import anImport -> anImport.index();
                case GitImport.Unattended unattended -> unattended.index();
                case GitImport.Review review -> review.index();
                default -> null;
            };
            if (index != null && named.containsKey(index)) {
                // the person's configuration: imported as reviewed, the LLM not asked
                step = this.importNamed(run, index, named.get(index));
                continue;
            }
            step = switch (step) {
                case GitImport.Import anImport -> this.importOne(run, anImport.index(), anImport.configuration());
                case GitImport.Unattended unattended -> this.unattended(run, suggest, unattended.index());
                case GitImport.Review review -> this.review(run, review, onReview);
                // not given without reviewing each commit
                case GitImport.SuggestForReview suggestForReview -> throw new IllegalStateException("A review of each commit was not asked for.");
                case GitImport.Finished finished -> finished;
            };
        }
        this.writer.println(run.summary(false));
    }

    private GitImport.Step unattended(GitImport run, Function<GitCommitInfo, GitImport.Suggested> suggest, int index) {
        if (!run.useLlm())
            return run.unattendedWithoutSuggestion(index);
        this.writer.println("Asking the LLM about " + run.commit(index).getShortId() + " (" + run.commit(index).getMessage() + ")...");
        GitImport.Suggested suggested;
        try {
            suggested = suggest.apply(run.commit(index));
        } catch (RuntimeException e) {
            return run.afterUnattendedSuggestionFailed(index, e);
        }
        return run.afterUnattendedSuggestion(index, suggested);
    }

    /**
     * Commit [index] with the [configuration] the file named for it, as reviewed - unless it was
     * imported before (FR-G3f): the file is to name the features this commit changes.
     */
    private GitImport.Step importNamed(GitImport run, int index, String configuration) {
        GitCommitInfo commit = run.commit(index);
        String repeated = run.folds(commit) ? null : run.repeats(index, configuration);
        if (repeated != null) {
            this.writer.println(run.summary(true));
            throw new CliException("Stopped at " + commit.getShortId() + " (" + commit.getMessage() + "), named in " + FLAG_CONFIGURATIONS + ": " + repeated
                    + resumeHint(run, index));
        }
        return this.importOne(run, index, configuration);
    }

    /** Commit [index], with [configuration]: committed, or - between tagged ones - kept for the next tagged one. */
    private GitImport.Step importOne(GitImport run, int index, String configuration) {
        if (run.folds(run.commit(index))) {
            run.folded(index, configuration, run.foldChanges(index, configuration));
            return run.next(index + 1);
        }
        GitImport.Imported imported;
        try {
            imported = run.commit(index, configuration);
        } catch (Exception e) {
            String reason = GitImport.describeFailure(e);
            this.writer.println("*** IMPORT FAILED for " + run.commit(index).getShortId() + ": " + reason);
            this.writer.println(run.summary(true));
            throw new CliException("Could not import " + run.commit(index).getShortId() + " (" + run.commit(index).getMessage() + ") with [" + configuration + "]: "
                    + reason + resumeHint(run, index));
        }
        run.imported(index, configuration, imported);
        return run.next(index + 1);
    }

    private GitImport.Step review(GitImport run, GitImport.Review review, String onReview) {
        GitCommitInfo commit = run.commit(review.index());
        String why = (review.suggestionFailure() != null ? "its suggestion failed." : review.repeated() == null ? "its suggestion is uncertain." : "")
                + (review.repeated() == null ? "" : (review.suggestionFailure() != null ? " " : "") + review.repeated());
        if (review.repeated() != null && ON_REVIEW_IMPORT.equals(onReview)) {
            // imported as proposed, its changed lines would be lost (FR-M4d): only a person can name its features
            this.writer.println(run.summary(true));
            throw new CliException("Stopped at " + commit.getShortId() + " (" + commit.getMessage() + "): " + why
                    + " " + FLAG_ON_REVIEW + " " + ON_REVIEW_IMPORT + " does not import such a commit: " + nameItsFeatures(commit, review.configuration())
                    + " or give " + FLAG_ON_REVIEW + " " + ON_REVIEW_SKIP + " to leave it out." + resumeHint(run, review.index()));
        }
        switch (onReview) {
            case ON_REVIEW_SKIP -> {
                this.writer.println("Skipped " + commit.getShortId() + " (" + commit.getMessage() + "): " + why);
                run.skip(review.index());
                return run.next(review.index() + 1);
            }
            case ON_REVIEW_IMPORT -> {
                return this.importOne(run, review.index(), review.configuration());
            }
            default -> {
                this.writer.println(run.summary(true));
                throw new CliException("Stopped at " + commit.getShortId() + " (" + commit.getMessage() + "), proposed [" + review.configuration() + "]: " + why
                        + " " + (review.repeated() != null
                        ? nameItsFeatures(commit, review.configuration()) + " " + FLAG_ON_REVIEW + " " + ON_REVIEW_SKIP + " leaves such commits out."
                        : FLAG_ON_REVIEW + " " + ON_REVIEW_SKIP + " leaves such commits out, " + FLAG_ON_REVIEW + " " + ON_REVIEW_IMPORT + " imports them as proposed.")
                        + resumeHint(run, review.index()));
            }
        }
    }

    /** How to name the features of [commit], proposed [configuration] imported before. */
    private static String nameItsFeatures(GitCommitInfo commit, String configuration) {
        return "Give them in a file with " + FLAG_CONFIGURATIONS + ", a line \"" + commit.getShortId() + ": " + configuration
                + ", <a new feature or A'>\".";
    }

    /** How to go on from commit [index], once something was imported. */
    private static String resumeHint(GitImport run, int index) {
        return run.importedCount() == 0 ? "" : " To go on from it: " + FLAG_FROM + " " + run.commit(index).getShortId() + ".";
    }

    /**
     * The commits of [newestFirst] from [from] to [to], oldest first - each a commit id, or its
     * beginning, or a tag; null for the first or last commit.
     */
    static List<GitCommitInfo> range(List<GitCommitInfo> newestFirst, Map<String, List<String>> tags, String from, String to) {
        List<GitCommitInfo> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        if (oldestFirst.isEmpty())
            throw new CliException("The clone has no commits.");
        int first = from == null ? 0 : indexOf(oldestFirst, tags, from, FLAG_FROM);
        int last = to == null ? oldestFirst.size() - 1 : indexOf(oldestFirst, tags, to, FLAG_TO);
        if (first > last)
            throw new CliException(FLAG_FROM + " " + from + " comes after " + FLAG_TO + " " + to + ".");
        return List.copyOf(oldestFirst.subList(first, last + 1));
    }

    private static int indexOf(List<GitCommitInfo> commits, Map<String, List<String>> tags, String revision, String flag) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < commits.size(); i++) {
            String id = commits.get(i).getId();
            if (id.startsWith(revision) || tags.getOrDefault(id, List.of()).contains(revision))
                found.add(i);
        }
        if (found.isEmpty())
            throw new CliException(flag + " " + revision + ": no such commit or tag on the clone's first-parent history.");
        if (found.size() > 1)
            throw new CliException(flag + " " + revision + ": names " + found.size() + " commits - give more of the id.");
        return found.get(0);
    }

    /** Where argparse4j keeps the value of [flag]: "--tagged-only" in "tagged_only". */
    static String stripDashes(String flag) {
        return flag.substring(2).replace('-', '_');
    }
}
