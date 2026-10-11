package at.jku.isse.ecco.adapter.lilypond.parser.py4j;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What reading LilyPond asks of a python (see PythonFinderTest for how one is chosen): lymodel only
 * for musical tokens, and from the directory in Preferences before LYPYTHON.
 */
public class LilypondPythonTest {

	@Test
	public void readingNeedsTreeSitterPy4jAndLymodel_notParce() {
		assertFalse(LilypondPython.MODULES.contains("parce"), LilypondPython.MODULES.toString());
		assertTrue(LilypondPython.MODULES.containsAll(List.of("tree_sitter", "py4j", "lymodel.lybar.grammar")),
				LilypondPython.MODULES.toString());
		assertTrue(LilypondPython.advice(null).contains("tree-sitter"), LilypondPython.advice(null));
	}

	@Test
	public void lymodelDirectory_preferencesBeforeLypython_andOnlyOneHoldingLymodel() throws Exception {
		Path withLymodel = Files.createTempDirectory("with-lymodel");
		Files.createDirectory(withLymodel.resolve("lymodel"));
		Path other = Files.createTempDirectory("with-lymodel-too");
		Files.createDirectory(other.resolve("lymodel"));
		Path without = Files.createTempDirectory("without-lymodel");

		assertEquals(withLymodel, LilypondPython.lymodelDir(withLymodel.toString(), other.toString()));
		assertEquals(other, LilypondPython.lymodelDir("", other.toString()));
		assertEquals(other, LilypondPython.lymodelDir(without.toString(), other.toString()), "a directory without lymodel is passed over");
		assertNull(LilypondPython.lymodelDir(null, null));
	}

	@Test
	public void theAdviceSaysWhereLymodelWasLookedFor() throws Exception {
		Path plugin = Files.createTempDirectory("plugin-python");
		assertTrue(LilypondPython.advice(plugin).contains("looked for in " + plugin), LilypondPython.advice(plugin));
		assertTrue(LilypondPython.advice(null).contains("installed only"), LilypondPython.advice(null));
		assertTrue(LilypondPython.advice(null).contains("Preferences > Lilypond"), LilypondPython.advice(null));
	}
}
