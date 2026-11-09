package at.jku.isse.ecco.cli.command.gitimport;

import at.jku.isse.ecco.cli.command.CliException;
import at.jku.isse.ecco.cli.MainTestAccess;
import at.jku.isse.ecco.cli.command.ProgramConstants;
import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.gitimport.GitImport;
import at.jku.isse.ecco.service.llm.FeatureSuggestionLog;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import net.sourceforge.argparse4j.inf.Namespace;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code ecco git-import}: a Git clone's history imported unattended, without the GUI. */
public class GitImportCommandTest {

    private final Map<String, String> properties = new HashMap<>();

    @BeforeEach
    public void ownSuggestionLogAndModel(@TempDir Path tmp) {
        for (String key : List.of(FeatureSuggestionLog.LOCATION_PROPERTY, LlmPreferences.MODEL_NAME_PROPERTY, LlmPreferences.ENDPOINT_URL_PROPERTY))
            this.properties.put(key, System.getProperty(key));
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, tmp.resolve("suggestions.jsonl").toString());
    }

    @AfterEach
    public void restoreProperties() {
        this.properties.forEach((key, value) -> {
            if (value == null)
                System.clearProperty(key);
            else
                System.setProperty(key, value);
        });
    }

    /** Without an LLM every commit has the features of the first, BASE; a second import goes on from the repository's latest commit. */
    @Test
    @Timeout(120)
    public void importsARangeAndGoesOnLater(@TempDir Path tmp) throws Exception {
        Map<String, String> ids = gitHistory(tmp.resolve("clone"), Map.of("second", "v2"), "first", "second", "third");
        Path home = Files.createDirectories(tmp.resolve("home"));
        assertEquals(0, MainTestAccess.run(new String[]{"init"}, home));

        assertEquals(0, MainTestAccess.run(new String[]{"git-import", "../clone", "--to", "v2"}, home));
        assertEquals(List.of("first [BASE]", "second [BASE]"), commits(home));

        // the repository has commits: the run starts from the latest one's features - it started from
        // none, and the commit was refused for having no feature
        assertEquals(0, MainTestAccess.run(new String[]{"git-import", "../clone", "--from", ids.get("third").substring(0, 7)}, home));
        assertEquals(List.of("first [BASE]", "second [BASE]", "third [BASE]"), commits(home));
    }

    /** Only the tagged commits are committed; those before the first are part of BASE, those between go into the next tagged one. */
    @Test
    @Timeout(120)
    public void importsTheTaggedCommitsOnly(@TempDir Path tmp) throws Exception {
        gitHistory(tmp.resolve("clone"), Map.of("second", "v1", "fourth", "v2"), "first", "second", "third", "fourth", "fifth");
        Path home = Files.createDirectories(tmp.resolve("home"));
        assertEquals(0, MainTestAccess.run(new String[]{"init"}, home));

        assertEquals(0, MainTestAccess.run(new String[]{"git-import", "../clone", "--tagged-only"}, home));

        assertEquals(List.of("second [BASE]", "fourth [BASE]"), commits(home), "fifth, after the last tag, left out");
    }

    @Test
    @Timeout(120)
    public void eachCommitGetsItsSuggestion(@TempDir Path tmp) throws Exception {
        gitHistory(tmp.resolve("clone"), Map.of(), "first", "second", "third");
        Path home = repository(tmp);
        StringWriter out = new StringWriter();

        command(home, out, commit -> suggesting(commit.getMessage().toUpperCase())).run(arguments(home, "stop"));

        assertEquals(List.of("first [BASE]", "second [BASE, SECOND]", "third [BASE, SECOND, THIRD]"), commits(home));
        assertEquals("Imported 3 of 3 commits.", out.getLines().getLast());
    }

    /** A failed suggestion, where the GUI stops for review, ends the import - what was imported before stays. */
    @Test
    @Timeout(120)
    public void aFailedSuggestionStopsTheImport(@TempDir Path tmp) throws Exception {
        gitHistory(tmp.resolve("clone"), Map.of(), "first", "second", "third");
        Path home = repository(tmp);
        StringWriter out = new StringWriter();

        CliException stopped = assertThrows(CliException.class, () -> command(home, out, failingOn("second")).run(arguments(home, "stop")));

        assertTrue(stopped.getMessage().startsWith("Stopped at "), stopped.getMessage());
        assertTrue(stopped.getMessage().contains("(second), proposed [BASE]: its suggestion failed."), stopped.getMessage());
        assertTrue(stopped.getMessage().contains("--from "), "says how to go on: " + stopped.getMessage());
        assertEquals(List.of("first [BASE]"), commits(home));
        assertTrue(String.join("\n", out.getLines()).contains("Imported 1 of 3 commits. Stopped before the end."), String.join("\n", out.getLines()));
    }

    @Test
    @Timeout(120)
    public void aFailedSuggestionIsSkipped(@TempDir Path tmp) throws Exception {
        gitHistory(tmp.resolve("clone"), Map.of(), "first", "second", "third");
        Path home = repository(tmp);
        StringWriter out = new StringWriter();

        command(home, out, failingOn("second")).run(arguments(home, "skip"));

        assertEquals(List.of("first [BASE]", "third [BASE, THIRD]"), commits(home));
        assertTrue(out.getLines().getLast().startsWith("Imported 2 of 3 commits, skipped 1."), out.getLines().getLast());
    }

    @Test
    @Timeout(120)
    public void aFailedSuggestionIsImportedAsProposed(@TempDir Path tmp) throws Exception {
        gitHistory(tmp.resolve("clone"), Map.of(), "first", "second", "third");
        Path home = repository(tmp);

        command(home, new StringWriter(), failingOn("second")).run(arguments(home, "import"));

        assertEquals(List.of("first [BASE]", "second [BASE]", "third [BASE, THIRD]"), commits(home));
    }

    @Test
    @Timeout(120)
    public void refusesWhatItCannotImport(@TempDir Path tmp) throws Exception {
        Map<String, String> ids = gitHistory(tmp.resolve("clone"), Map.of("first", "v1"), "first", "second");
        Path home = Files.createDirectories(tmp.resolve("home"));
        assertEquals(0, MainTestAccess.run(new String[]{"init"}, home));

        assertEquals(1, MainTestAccess.run(new String[]{"git-import", "../clone", "--from", "nosuch"}, home));
        assertEquals(1, MainTestAccess.run(new String[]{"git-import", "../clone", "--from", ids.get("second"), "--to", "v1"}, home));
        assertEquals(1, MainTestAccess.run(new String[]{"git-import", "../clone", "--tagged-only", "--from", ids.get("second")}, home));
        assertEquals(1, MainTestAccess.run(new String[]{"git-import", "home"}, tmp), "not a clone");
        System.setProperty(LlmPreferences.MODEL_NAME_PROPERTY, "");
        assertEquals(1, MainTestAccess.run(new String[]{"git-import", "../clone", "--llm"}, home), "no model to ask");
        assertEquals(2, MainTestAccess.run(new String[]{"git-import", "../clone", "--on-review", "ask"}, home));
        assertEquals(List.of(), commits(home));
    }

    private static GitImportCommand command(Path home, StringWriter out, Function<GitCommitInfo, GitImport.Suggested> suggester) {
        EccoService service = new EccoService();
        service.setBaseDir(home);
        service.setRepositoryDir(home.resolve(EccoService.REPOSITORY_DIR_NAME));
        return new GitImportCommand(service, out, run -> suggester);
    }

    /** The arguments of "git-import ../clone --llm --llm-model stub --on-review [onReview]", as parsed. */
    private static Namespace arguments(Path home, String onReview) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(GitImportCommand.CLONE_KEY, "../clone");
        arguments.put(GitImportCommand.stripDashes(GitImportCommand.FLAG_LLM), true);
        arguments.put(GitImportCommand.stripDashes(GitImportCommand.FLAG_LLM_MODEL), "stub");
        arguments.put(GitImportCommand.stripDashes(GitImportCommand.FLAG_TAGGED_ONLY), false);
        arguments.put(GitImportCommand.stripDashes(GitImportCommand.FLAG_ON_REVIEW), onReview);
        arguments.put(ProgramConstants.START_DIR, home);
        return new Namespace(arguments);
    }

    /** Suggests the commit's message as a new feature, but for [message]'s commit: that suggestion fails. */
    private static Function<GitCommitInfo, GitImport.Suggested> failingOn(String message) {
        return commit -> commit.getMessage().equals(message)
                ? new GitImport.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(List.of(""), "no model", List.of(), List.of()), "stub", 1)
                : suggesting(commit.getMessage().toUpperCase());
    }

    private static GitImport.Suggested suggesting(String feature) {
        return new GitImport.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(List.of(feature), null, List.of(), List.of(Map.of(feature, "BASE"))), "stub", 1);
    }

    private static Path repository(Path tmp) throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home"));
        assertEquals(0, MainTestAccess.run(new String[]{"init"}, home));
        return home;
    }

    /** "message [features]" of each commit of the repository in [home], oldest first. */
    private static List<String> commits(Path home) {
        EccoService service = new EccoService();
        service.setRepositoryDir(home.resolve(EccoService.REPOSITORY_DIR_NAME));
        service.open();
        try {
            List<Commit> commits = new ArrayList<>(service.getCommits());
            commits.sort(Comparator.comparing(Commit::getDate));
            return commits.stream().map(commit -> commit.getCommitMessage() + " " + Arrays.stream(commit.getConfiguration().getFeatureRevisions())
                    .map(FeatureRevision::getFeature).map(feature -> feature.getName())
                    .collect(Collectors.toCollection(TreeSet::new))).toList();
        } finally {
            service.close();
        }
    }

    /** A clone with a commit per message, each adding a file; [tags] by message. Returns the commits' ids by message. */
    private static Map<String, String> gitHistory(Path dir, Map<String, String> tags, String... messages) throws Exception {
        Files.createDirectories(dir);
        Map<String, String> ids = new LinkedHashMap<>();
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            for (String message : messages) {
                Files.writeString(dir.resolve(message + ".txt"), message + "\n");
                git.add().addFilepattern(".").call();
                ids.put(message, git.commit().setMessage(message).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call().getId().name());
                if (tags.containsKey(message))
                    git.tag().setName(tags.get(message)).call();
            }
        }
        return ids;
    }
}
