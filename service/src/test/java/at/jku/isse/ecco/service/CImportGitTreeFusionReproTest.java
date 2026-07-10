package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.file.FileArtifactData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterizes and fixes a real "Import from Git" crash reported against a real repository
 * (kilo): {@code EccoException: An equivalent child is already contained}, thrown from {@code
 * SerNode.addChild()} inside {@code Trees.treeFusion()} while building the cached mainTree for
 * the SECOND commit - reproduced here even with just one small, completely unchanged file (e.g.
 * a LICENSE) committed twice, with no C content involved at all.
 * <p>
 * Root cause: {@link FileArtifactData#hashCode()} used {@code Objects.hash(this.checksum)} on a
 * raw {@code byte[]} field. {@code Objects.hash(Object...)} boxes a single array argument as ONE
 * opaque element rather than spreading its contents, and arrays don't override {@code hashCode()},
 * so this hashed by array IDENTITY - completely inconsistent with {@code equals()}, which
 * correctly compares checksum bytes via {@code Arrays.equals()}. Two independently-parsed copies
 * of the exact same file content (e.g. an unchanged file re-parsed on a later commit) were
 * {@code .equals()} but had different, non-reproducible hash codes - confirmed via temporary debug
 * instrumentation in {@code Trees.treeFusion()}. This broke {@code ChildIndex}'s hash-based
 * candidate lookup (used to decide whether an incoming child already has a match in mainTree): a
 * real match existed but the broken hash sent the lookup to the wrong bucket, so {@code
 * treeFusion()} concluded no candidate existed and tried to add a brand new child - which {@code
 * SerNode.addChild()}'s own (correct, equals()-based) duplicate check then rejected, since the
 * matching child was unordered and already truly present.
 * <p>
 * Fix: {@link FileArtifactData#hashCode()} now uses {@code Arrays.hashCode(this.checksum)},
 * matching the same fix already applied to {@code ImageArtifactData} elsewhere in the codebase
 * (which has an explicit comment warning against exactly this mistake).
 */
public class CImportGitTreeFusionReproTest {

	@Test
	public void fileArtifactData_equalChecksumBytes_haveEqualHashCode() throws IOException {
		// two SEPARATE byte[] instances with identical content, exactly like two independent
		// parses of the same unchanged file across two different commits
		Path file1 = Files.createTempFile("checksum-a", ".txt");
		Path file2 = Files.createTempFile("checksum-b", ".txt");
		Files.writeString(file1, "identical content", StandardOpenOption.TRUNCATE_EXISTING);
		Files.writeString(file2, "identical content", StandardOpenOption.TRUNCATE_EXISTING);

		FileArtifactData a = new FileArtifactData(file1.getParent(), file1.getFileName());
		FileArtifactData b = new FileArtifactData(file2.getParent(), file2.getFileName());

		assertEquals(a, b, "equal content should be equals()");
		assertEquals(a.hashCode(), b.hashCode(),
				"equals() objects must have equal hashCode() - this is what broke ChildIndex's hash-based lookup");
	}

	@Test
	public void commitSameUnchangedFile_acrossTwoCommits_doesNotThrow() throws IOException {
		Path v1 = Files.createTempDirectory("unchanged-file-repro-v1");
		Path v2 = Files.createTempDirectory("unchanged-file-repro-v2");
		String content = "MIT License\n\nCopyright (c) 2016 example\n";
		Files.writeString(v1.resolve("LICENSE"), content, StandardOpenOption.CREATE);
		Files.writeString(v2.resolve("LICENSE"), content, StandardOpenOption.CREATE);

		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory("unchanged-file-repro-repo").resolve(".ecco"));
		service.init();

		service.setBaseDir(v1);
		service.commit("commit 1");

		service.setBaseDir(v2);
		service.commit("commit 2");

		service.close();
	}
}