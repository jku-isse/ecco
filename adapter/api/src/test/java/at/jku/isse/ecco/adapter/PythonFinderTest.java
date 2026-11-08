package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.EccoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain {@code python} from the PATH was the only python the LilyPond and Python adapters tried: a
 * LilyPond commit from the GUI failed with "No module named 'lymodel'" although lymodel was there
 * for another python - and from the Finder, whose PATH has no MacPorts or Homebrew, no python would
 * have been found at all. PythonFinder tries each candidate until one imports what is needed. The
 * probes here are stand-ins, so the choice is tested whatever this machine has installed.
 */
public class PythonFinderTest {

	private static final String PURPOSE = "does the job", ADVICE = "Install it.";

	private static PythonFinder.Prober installed(Map<String, Set<String>> modules, List<String> asked) {
		return (python, wanted, extraPath) -> {
			asked.add(python);
			return new PythonFinder.Probe(python, modules.get(python));
		};
	}

	@Test
	public void aPythonWithoutEveryModuleIsPassedOver() {
		List<String> asked = new ArrayList<>();
		String python = PythonFinder.find(List.of("python", "python3", "/opt/local/bin/python3"),
				List.of("parce", "py4j", "lymodel.lybar.normalize"), null, PURPOSE, ADVICE,
				installed(Map.of(
						"python", Set.of("parce", "py4j"),
						"python3", Set.of(),
						"/opt/local/bin/python3", Set.of("parce", "py4j", "lymodel.lybar.normalize")), asked));
		assertEquals("/opt/local/bin/python3", python);
		assertEquals(List.of("python", "python3", "/opt/local/bin/python3"), asked);
	}

	@Test
	public void theFirstThatWillDoIsTaken() {
		List<String> asked = new ArrayList<>();
		String python = PythonFinder.find(List.of("python", "python3"), List.of("libcst", "py4j"), null, PURPOSE, ADVICE,
				installed(Map.of("python", Set.of("libcst", "py4j"), "python3", Set.of("libcst", "py4j")), asked));
		assertEquals("python", python);
		assertEquals(List.of("python"), asked);
	}

	@Test
	public void noneWillDo_reportsWhatEachPythonLacks() {
		EccoException e = assertThrows(EccoException.class, () -> PythonFinder.find(
				List.of("python", "python3", "/opt/homebrew/bin/python3"),
				List.of("parce", "py4j", "lymodel.lybar.normalize"), null, PURPOSE, ADVICE,
				installed(Map.of("python", Set.of("parce", "py4j"), "python3", Set.of()), new ArrayList<>())));
		String message = e.getMessage();
		assertTrue(message.startsWith("No python found that does the job: it needs parce, py4j, lymodel."), message);
		assertTrue(message.contains("python: no lymodel\n"), message);
		assertTrue(message.contains("python3: no parce, no py4j, no lymodel"), message);
		assertTrue(message.contains("/opt/homebrew/bin/python3: not found"), message);
		assertTrue(message.endsWith(ADVICE), message);
	}

	@Test
	public void theExtraPathIsHandedToTheProbe() throws Exception {
		Path extra = Files.createTempDirectory("python-path");
		List<Path> handed = new ArrayList<>();
		PythonFinder.find(List.of("python"), List.of("lymodel"), extra, PURPOSE, ADVICE, (python, modules, path) -> {
			handed.add(path);
			return new PythonFinder.Probe(python, Set.of("lymodel"));
		});
		assertEquals(List.of(extra), handed);
	}

	@Test
	public void aConfiguredPythonIsTheOnlyCandidate() {
		assertEquals(List.of("/my/python"), PythonFinder.candidates(" /my/python "));
		List<String> searched = PythonFinder.candidates("");
		assertEquals(List.of("python", "python3"), searched.subList(0, 2));
		assertTrue(searched.contains("/opt/homebrew/bin/python3"), searched.toString());
		assertTrue(searched.contains("/opt/local/bin/python3"), searched.toString());
	}

	@Test
	@Timeout(30)
	public void aPythonThatDoesNotExistIsNotFound() {
		assertNull(PythonFinder.probe("/no/such/python", List.of("os"), null).modules());
	}

	@Test
	@Timeout(30)
	public void aRealPythonReportsWhatItImports() {
		String python = PythonFinder.candidates("").stream()
				.filter(p -> PythonFinder.probe(p, List.of("os"), null).modules() != null)
				.findFirst().orElse(null);
		if (python == null) return; // no python on this machine at all
		assertEquals(Set.of("os", "json"), PythonFinder.probe(python, List.of("os", "json", "no_such_module_here"), null).modules());
	}
}
