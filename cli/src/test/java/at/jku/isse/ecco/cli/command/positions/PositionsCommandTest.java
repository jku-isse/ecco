package at.jku.isse.ecco.cli.command.positions;

import at.jku.isse.ecco.cli.MainTestAccess;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PositionsCommandTest {

    @Test
    @Timeout(120)
    public void printsTheChangedPositionsOfEachCommit(@TempDir Path tmp) throws Exception {
        Path clone = Files.createDirectories(tmp.resolve("clone"));
        String first, second;
        try (Git git = Git.init().setDirectory(clone.toFile()).call()) {
            Files.writeString(clone.resolve("README.md"), "# Doc\n\n## Usage\n\nuse it\n");
            git.add().addFilepattern(".").call();
            first = git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call().getId().name();
            Files.writeString(clone.resolve("README.md"), "# Doc\n\n## Usage\n\nuse it \"well\"\n");
            git.add().addFilepattern(".").call();
            second = git.commit().setMessage("second").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call().getId().name();
        }
        Path home = Files.createDirectories(tmp.resolve("home"));
        assertEquals(0, MainTestAccess.run(new String[]{"init"}, home));

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true));
        try {
            assertEquals(0, MainTestAccess.run(new String[]{"positions", "../clone", first, second}, home));
        } finally {
            System.setOut(original);
        }

        assertEquals("{\"commit\":\"" + first + "\",\"files\":{\"README.md\":{\"added\":5,\"removed\":0,\"positions\":[\"Doc\",\"Doc > Usage\"]}}}\n"
                + "{\"commit\":\"" + second + "\",\"files\":{\"README.md\":{\"added\":1,\"removed\":1,\"positions\":[\"Doc > Usage\"]}}}\n", captured.toString().replace("\r\n", "\n"));
    }

    @Test
    public void jsonEscapesQuotes() {
        assertEquals("{\"commit\":\"c\",\"files\":{\"a \\\"b\\\".md\":{\"added\":1,\"removed\":2,\"positions\":[\"x\\\\y\"]}}}",
                PositionsCommand.json("c", Map.of("a \"b\".md", new at.jku.isse.ecco.service.git.GitHistoryReader.ChangedFile("a \"b\".md", 1, 2, Set.of("x\\y")))));
    }
}
