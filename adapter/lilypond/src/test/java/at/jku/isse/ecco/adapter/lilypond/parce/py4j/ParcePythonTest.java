package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static at.jku.isse.ecco.adapter.lilypond.parce.py4j.ParcePython.LYMODEL;
import static at.jku.isse.ecco.adapter.lilypond.parce.py4j.ParcePython.PARCE;
import static at.jku.isse.ecco.adapter.lilypond.parce.py4j.ParcePython.PY4J;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain {@code python} from the PATH was the only python tried: a commit from the GUI failed with
 * "No module named 'lymodel'" although lymodel was there for another python - and from the Finder,
 * whose PATH has no MacPorts or Homebrew, no python would have been found at all. ParcePython tries
 * each candidate until one imports what the repository needs. The probes here are stand-ins, so
 * the choice is tested whatever this machine has installed.
 */
public class ParcePythonTest {

	private static BiFunction<String, Path, ParcePython.Probe> installed(Map<String, Set<String>> modules, List<String> asked) {
		return (python, lymodelDir) -> {
			asked.add(python);
			return new ParcePython.Probe(python, modules.get(python));
		};
	}

	@Test
	public void musicalTokens_skipAPythonWithoutLymodel() {
		List<String> asked = new ArrayList<>();
		ParcePython.Choice choice = ParcePython.resolve(true, List.of("python", "python3", "/opt/local/bin/python3"), null,
				installed(Map.of(
						"python", Set.of(PARCE, PY4J),
						"python3", Set.of(),
						"/opt/local/bin/python3", Set.of(PARCE, PY4J, LYMODEL)), asked));
		assertEquals("/opt/local/bin/python3", choice.python());
		assertEquals(List.of("python", "python3", "/opt/local/bin/python3"), asked);
	}

	@Test
	public void plainTokens_takeTheFirstPythonWithParce() {
		List<String> asked = new ArrayList<>();
		ParcePython.Choice choice = ParcePython.resolve(false, List.of("python", "python3"), null,
				installed(Map.of("python", Set.of(PARCE, PY4J), "python3", Set.of(PARCE, PY4J, LYMODEL)), asked));
		assertEquals("python", choice.python());
		assertEquals(List.of("python"), asked, "stops at the first that will do");
	}

	@Test
	public void noneWillDo_reportsWhatEachPythonLacks() {
		EccoException e = assertThrows(EccoException.class, () -> ParcePython.resolve(true,
				List.of("python", "python3", "/opt/homebrew/bin/python3"), null,
				installed(Map.of("python", Set.of(PARCE, PY4J), "python3", Set.of()), new ArrayList<>())));
		String message = e.getMessage();
		assertTrue(message.contains("python: no lymodel"), message);
		assertTrue(message.contains("python3: no lymodel, no parce, no py4j"), message);
		assertTrue(message.contains("/opt/homebrew/bin/python3: not found"), message);
		assertTrue(message.contains("Preferences > LilyPond"), message);
	}

	@Test
	public void theLymodelDirectoryIsHandedToTheProbeAndTheChoice() throws Exception {
		Path plugin = Files.createTempDirectory("plugin-python");
		Files.createDirectory(plugin.resolve("lymodel"));
		List<Path> handed = new ArrayList<>();
		ParcePython.Choice choice = ParcePython.resolve(true, List.of("python"), plugin, (python, dir) -> {
			handed.add(dir);
			return new ParcePython.Probe(python, dir == null ? Set.of(PARCE, PY4J) : Set.of(PARCE, PY4J, LYMODEL));
		});
		assertEquals(List.of(plugin), handed);
		assertEquals(plugin, choice.lymodelDir());
	}

	@Test
	public void aConfiguredPythonIsTheOnlyCandidate() {
		assertEquals(List.of("/my/python"), ParcePython.candidates(" /my/python "));
		List<String> searched = ParcePython.candidates("");
		assertEquals(List.of("python", "python3"), searched.subList(0, 2));
		assertTrue(searched.contains("/opt/homebrew/bin/python3"), searched.toString());
		assertTrue(searched.contains("/opt/local/bin/python3"), searched.toString());
	}

	@Test
	public void lymodelDirectory_preferencesBeforeLypython_andOnlyOneHoldingLymodel() throws Exception {
		Path withLymodel = Files.createTempDirectory("with-lymodel");
		Files.createDirectory(withLymodel.resolve("lymodel"));
		Path other = Files.createTempDirectory("with-lymodel-too");
		Files.createDirectory(other.resolve("lymodel"));
		Path without = Files.createTempDirectory("without-lymodel");

		assertEquals(withLymodel, ParcePython.lymodelDir(withLymodel.toString(), other.toString()));
		assertEquals(other, ParcePython.lymodelDir("", other.toString()));
		assertEquals(other, ParcePython.lymodelDir(without.toString(), other.toString()), "a directory without lymodel is passed over");
		assertNull(ParcePython.lymodelDir(null, null));
	}

	@Test
	@Timeout(30)
	public void aPythonThatDoesNotExistIsNotFound() {
		assertNull(ParcePython.probe("/no/such/python", null).modules());
	}
}
