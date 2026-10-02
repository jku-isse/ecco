package at.jku.isse.ecco.adapter.lilypond;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The bundled lilypond-config.properties held one developer's absolute paths, so on every other
 * machine the score viewer pointed at a LilyPond that did not exist. The bundled defaults are now
 * empty, and an unset executable is looked up on the PATH.
 */
public class LilypondConfigTest {

	@Test
	public void bundledConfigHasNoMachineSpecificPaths() throws IOException {
		Properties properties = new Properties();
		try (InputStream in = LilypondCompiler.class.getClassLoader().getResourceAsStream("lilypond-config.properties")) {
			assertNotNull(in);
			properties.load(in);
		}
		assertEquals("", properties.getProperty("lilypond_executable"));
		assertEquals("", properties.getProperty("lilypond_search_paths"));
	}

	@Test
	public void findsTheExecutableInTheFirstPathDirectoryHoldingIt(@TempDir Path tmp) throws IOException {
		Path empty = Files.createDirectory(tmp.resolve("empty"));
		Path first = Files.createDirectory(tmp.resolve("first"));
		Path second = Files.createDirectory(tmp.resolve("second"));
		Path inFirst = executable(first.resolve("lilypond"));
		executable(second.resolve("lilypond"));

		String path = String.join(File.pathSeparator, empty.toString(), "", first.toString(), second.toString());
		assertEquals(inFirst, LilypondCompiler.findOnPath(path, "lilypond"));
	}

	@Test
	public void skipsNonExecutableFilesAndMissingDirectories(@TempDir Path tmp) throws IOException {
		Path dir = Files.createDirectory(tmp.resolve("bin"));
		Path plain = Files.writeString(dir.resolve("lilypond"), "");
		assumeTrue(plain.toFile().setExecutable(false) && !Files.isExecutable(plain));

		String path = String.join(File.pathSeparator, tmp.resolve("missing").toString(), dir.toString());
		assertNull(LilypondCompiler.findOnPath(path, "lilypond"));
	}

	@Test
	public void noPathMeansNoExecutable() {
		assertNull(LilypondCompiler.findOnPath(null, "lilypond"));
		assertNull(LilypondCompiler.findOnPath("", "lilypond"));
	}

	private static Path executable(Path file) throws IOException {
		Files.writeString(file, "#!/bin/sh\n");
		assumeTrue(file.toFile().setExecutable(true));
		return file;
	}
}
