package at.jku.isse.ecco.lsp;

import com.google.gson.JsonObject;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Commit and checkout from the editor: a commit's code is traced to its configuration at once; a
 * checkout replaces the last checkout's files in place - never a file not from it, nor .git, and a
 * changed file only when told to - and refuses a configuration it cannot check out before deleting
 * anything.
 */
@Timeout(120)
public class EditorCommandsTest {

    private static JsonObject arguments(String... pairs) {
        JsonObject object = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2)
            object.addProperty(pairs[i], pairs[i + 1]);
        return object;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(FeatureLens lens, String command, JsonObject arguments) {
        return (Map<String, Object>) EditorCommands.run(command, lens, arguments);
    }

    private static Map<String, Object> checkout(FeatureLens lens, String configuration, boolean force) {
        JsonObject arguments = arguments("configuration", configuration);
        arguments.addProperty("force", force);
        return run(lens, EditorCommands.CHECKOUT, arguments);
    }

    @Test
    public void aCommitIsTracedToItsConfigurationAtOnce(@TempDir Path tmp) throws Exception {
        Path work = FeatureLensTest.checkout(tmp, "BASE, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            Files.writeString(work.resolve("notes.txt"), "base1\nbase2\nb1\nc1\n");
            assertFalse(lens.trace(Path.of("notes.txt"), "base1\nbase2\nb1\nc1\n").at(4).committed());
            Map<String, Object> committed = run(lens, EditorCommands.COMMIT, arguments("configuration", "BASE, B, C", "message", "c"));
            assertTrue(((String) committed.get("configuration")).contains("C."), committed.toString());
            assertTrue(Files.readString(work.resolve(".config")).contains("C."));
            assertFalse(Files.exists(work.resolve(".warnings")));
            assertEquals("C", lens.trace(Path.of("notes.txt"), "base1\nbase2\nb1\nc1\n").at(4).condition());
            // its files are the checkout's now: replaced without asking
            assertTrue(WorkingDirectory.since(work).unchanged());
            assertNull(checkout(lens, "BASE, A", false).get("refused"));
            assertEquals(FeatureLensTest.TEXT.get("BASE, A"), Files.readString(work.resolve("notes.txt")));
        }
    }

    @Test
    public void aCheckoutReplacesTheLastOneInPlace(@TempDir Path tmp) throws Exception {
        Path work = FeatureLensTest.checkout(tmp, "BASE, B");
        Files.writeString(work.resolve(".DS_Store"), "finder");
        try (FeatureLens lens = new FeatureLens(work)) {
            Map<String, Object> result = checkout(lens, "BASE, A", false);
            assertNull(result.get("refused"), result.toString());
            assertEquals(FeatureLensTest.TEXT.get("BASE, A"), Files.readString(work.resolve("notes.txt")));
            assertTrue(Files.readString(work.resolve("Foo.java")).contains("int a()"));
            assertTrue(Files.readString(work.resolve(".config")).startsWith("A."), Files.readString(work.resolve(".config")));
            assertTrue(Files.isDirectory(work.resolve(".ecco")));
            assertFalse(Files.exists(work.resolve(".DS_Store")));
            assertEquals("A", lens.trace(Path.of("notes.txt"), Files.readString(work.resolve("notes.txt"))).at(2).condition());
        }
    }

    @Test
    public void aChangedFileIsReplacedOnlyWhenForced(@TempDir Path tmp) throws Exception {
        Path work = FeatureLensTest.checkout(tmp, "BASE, B");
        Files.writeString(work.resolve("notes.txt"), "edited\n");
        Files.delete(work.resolve("Foo.java"));
        try (FeatureLens lens = new FeatureLens(work)) {
            Map<String, Object> refused = checkout(lens, "BASE, A", false);
            assertEquals("changed", refused.get("refused"));
            assertEquals(List.of("notes.txt"), refused.get("changed"));
            assertEquals(List.of("Foo.java"), refused.get("removed"));
            assertEquals("edited\n", Files.readString(work.resolve("notes.txt")), "nothing touched");

            assertNull(checkout(lens, "BASE, A", true).get("refused"));
            assertEquals(FeatureLensTest.TEXT.get("BASE, A"), Files.readString(work.resolve("notes.txt")));
        }
    }

    @Test
    public void filesNotFromTheCheckoutAndGitAreNeverDeleted(@TempDir Path tmp) throws Exception {
        Path work = FeatureLensTest.checkout(tmp, "BASE, B");
        Files.writeString(work.resolve("new.txt"), "mine\n");
        Files.createDirectories(work.resolve(".git"));
        Files.writeString(work.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        try (FeatureLens lens = new FeatureLens(work)) {
            for (boolean force : new boolean[]{false, true}) {
                Map<String, Object> refused = checkout(lens, "BASE, A", force);
                assertEquals("foreign", refused.get("refused"), "force " + force);
                assertEquals(List.of(".git/", "new.txt"), ((List<?>) refused.get("foreign")).stream().sorted().toList());
                assertEquals("mine\n", Files.readString(work.resolve("new.txt")));
                assertTrue(Files.exists(work.resolve(".git/HEAD")));
                assertEquals(FeatureLensTest.TEXT.get("BASE, B"), Files.readString(work.resolve("notes.txt")));
            }
        }
    }

    @Test
    public void aConfigurationThatCannotBeCheckedOutDeletesNothing(@TempDir Path tmp) throws Exception {
        Path work = FeatureLensTest.checkout(tmp, "BASE, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            assertThrows(ResponseErrorException.class, () -> checkout(lens, "BASE, Z", true));
            assertEquals(FeatureLensTest.TEXT.get("BASE, B"), Files.readString(work.resolve("notes.txt")));
            Files.delete(work.resolve(".hashes"));
            ResponseErrorException noRecord = assertThrows(ResponseErrorException.class, () -> checkout(lens, "BASE, A", true));
            assertTrue(noRecord.getMessage().contains("no record"), noRecord.getMessage());
            assertTrue(Files.exists(work.resolve("notes.txt")));
        }
    }

    @Test
    public void infoSuggestsFeaturesAndCommittedConfigurations(@TempDir Path tmp) throws Exception {
        Path work = FeatureLensTest.checkout(tmp, "BASE, B");
        try (FeatureLens lens = new FeatureLens(work)) {
            Map<String, Object> info = run(lens, EditorCommands.INFO, new JsonObject());
            assertEquals(List.of("A", "B", "BASE"), info.get("features"));
            assertEquals(3, ((List<?>) info.get("configurations")).size());
            assertTrue(((String) info.get("configuration")).contains("B."));
            assertEquals(List.of(), EditorCommands.run(EditorCommands.CONSTRAINT_VIOLATIONS, lens, arguments("configuration", "BASE, A")));
        }
    }
}
