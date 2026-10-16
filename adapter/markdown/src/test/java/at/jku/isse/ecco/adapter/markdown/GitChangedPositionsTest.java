package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The positions a Git commit changed, as the repository's adapters name them. */
public class GitChangedPositionsTest {

	@Test
	@Timeout(60)
	public void changedLinesAreLocatedInTheirSections(@TempDir Path tmp) throws Exception {
		Path clone = Files.createDirectories(tmp.resolve("clone"));
		String second;
		try (Git git = Git.init().setDirectory(clone.toFile()).call()) {
			Files.writeString(clone.resolve("README.md"), "# Doc\n\n## Install\n\nrun it\n\n## Usage\n\nuse it\n");
			Files.writeString(clone.resolve("notes.txt"), "a\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
			// a line of Usage changed, a section added; Install untouched
			Files.writeString(clone.resolve("README.md"), "# Doc\n\n## Install\n\nrun it\n\n## Usage\n\nuse it well\n\n## FAQ\n\nask\n");
			Files.writeString(clone.resolve("notes.txt"), "b\n");
			git.add().addFilepattern(".").call();
			second = git.commit().setMessage("second").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call().getId().name();
		}
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(Files.createDirectories(tmp.resolve("repo")).resolve(".ecco"));
			service.init();

			assertEquals(Map.of("README.md", new GitHistoryReader.ChangedFile("README.md", 5, 1, Set.of("Doc > Usage", "Doc > FAQ")),
							"notes.txt", new GitHistoryReader.ChangedFile("notes.txt", 1, 1, Set.of())),
					new GitHistoryReader().getChangedPositions(clone, second, service.getReader()));
		}
	}
}
