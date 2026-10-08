package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What reading LilyPond asks of a python (see PythonFinderTest for how one is chosen): lymodel only
 * for musical tokens, and from the directory in Preferences before LYPYTHON.
 */
public class ParcePythonTest {

	@Test
	public void lymodelIsNeededForMusicalTokensOnly() {
		assertEquals(List.of("parce", "py4j", "lymodel.lybar.normalize"), ParcePython.modules(true));
		assertEquals(List.of("parce", "py4j"), ParcePython.modules(false));
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
	public void theAdviceSaysWhereLymodelWasLookedFor() throws Exception {
		Path plugin = Files.createTempDirectory("plugin-python");
		assertTrue(ParcePython.advice(plugin).contains("looked for in " + plugin), ParcePython.advice(plugin));
		assertTrue(ParcePython.advice(null).contains("installed only"), ParcePython.advice(null));
		assertTrue(ParcePython.advice(null).contains("Preferences > Lilypond"), ParcePython.advice(null));
	}
}
