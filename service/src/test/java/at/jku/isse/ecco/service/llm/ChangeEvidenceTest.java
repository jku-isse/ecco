package at.jku.isse.ecco.service.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChangeEvidenceTest {

	@Test
	public void kindsOfFiles() {
		assertEquals("source", ChangeEvidence.kind("src/main/java/net/lingala/zip4j/ZipFile.java"));
		assertEquals("test", ChangeEvidence.kind("src/test/java/net/lingala/zip4j/ZipFileIT.java"));
		assertEquals("test", ChangeEvidence.kind("packages/vite/src/node/__tests__/build.spec.ts"));
		assertEquals("documentation", ChangeEvidence.kind("README.md"));
		assertEquals("documentation", ChangeEvidence.kind("docs/guide/api.md"));
		assertEquals("build", ChangeEvidence.kind("pom.xml"));
		assertEquals("build", ChangeEvidence.kind("LICENSE"));
		assertEquals("ci", ChangeEvidence.kind(".travis.yml"));
		assertEquals("ci", ChangeEvidence.kind(".github/workflows/ci.yml"));
		assertEquals("resource", ChangeEvidence.kind("src/main/resources/x.properties"));
		assertEquals("other", ChangeEvidence.kind("bin/run"));
	}

	@Test
	public void messageHints() {
		assertEquals(List.of("release or version"), ChangeEvidence.messageHints("Release 2.0.1"));
		assertEquals(List.of("merge", "issue reference"), ChangeEvidence.messageHints("Merge pull request #31 from x/patch-2"));
		assertEquals(List.of("revert"), ChangeEvidence.messageHints("Revert \"add AES\""));
		assertEquals(List.of(), ChangeEvidence.messageHints("Skip file attributes if nio is not supported"));
	}
}
