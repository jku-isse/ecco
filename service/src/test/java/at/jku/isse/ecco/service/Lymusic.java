package at.jku.isse.ecco.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lymusic, from lymodel (the lilypond-idea-plugin's python/ package): whether two .ly files hold
 * the same music - notes, lyrics, articulations, slurs, dynamics, clefs/keys/meters - however each
 * is spelled. Taken from LYPYTHON when that names the plugin's python/ directory, else from an
 * installed lymodel ({@code pip install <plugin repo>/python}); tests that need it are skipped
 * without either.
 */
final class Lymusic {

	private static Boolean available;

	private Lymusic() {}

	static synchronized boolean available() {
		if (available == null) {
			try {
				Process process = python("-c", "import lymodel.compare.lymusic").redirectErrorStream(true).start();
				process.getInputStream().readAllBytes();
				available = process.waitFor() == 0;
			} catch (IOException | InterruptedException e) {
				available = false;
			}
		}
		return available;
	}

	static final String NEEDS = "needs lymodel: pip install lilypond-idea-plugin's python/, or LYPYTHON pointing at it";

	static void assertSameMusic(Path expected, Path actual, String aspects, String what)
			throws IOException, InterruptedException {
		Process process = python("-m", "lymodel.compare.lymusic", expected.toString(), actual.toString(), "--aspects", aspects)
				.redirectErrorStream(true)
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exit = process.waitFor();
		assertTrue(exit == 0 || exit == 1, () -> "lymusic failed for " + what + ":\n" + output);
		assertEquals(0, exit, () -> "music differs for " + what + " (" + aspects + "):\n" + output);
	}

	private static ProcessBuilder python(String... args) {
		String[] command = new String[args.length + 1];
		command[0] = "python3";
		System.arraycopy(args, 0, command, 1, args.length);
		ProcessBuilder builder = new ProcessBuilder(command);
		String root = System.getenv("LYPYTHON");
		if (root != null && !root.isBlank() && Files.isDirectory(Path.of(root, "lymodel"))) {
			builder.environment().put("PYTHONPATH", root);
		}
		return builder;
	}
}
