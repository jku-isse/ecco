package at.jku.isse.ecco.adapter.python.test;

import at.jku.isse.ecco.adapter.python.PythonPlugin;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Variants of one Python module, generated: imports, assignments with a trailing comment, functions
 * and classes of a shared pool, each variant with each of them or not and, of a function it has, each
 * statement of its body or not. Committed under a feature each, every variant must check out with the
 * code committed (the adapter is not byte-exact: it normalizes whitespace-only lines, AD-6), and
 * byte for byte as from a repository holding it alone.
 */
public class PythonVariantsRoundTripTest {

	private static final String[] STATEMENTS = {"    a += 1", "    print(a)", "    if a > 0:\n        a -= 1", "    # a comment",
			"    t = a * 2", "    for i in range(3):\n        a += i"};

	@Test
	@Timeout(1800)
	public void variantsOfAModuleComeBackWithTheirCodeAndAsAlone(@TempDir Path tmp) throws Exception {
		assumeTrue(PythonAvailable.check(), PythonAvailable.NEEDS);
		Random random = new Random(19);
		List<String> wrong = new ArrayList<>();
		for (int round = 0; round < 40 && wrong.size() < 3; round++) {
			List<List<String>> pool = new ArrayList<>();
			for (int c = 0; c < 2 + random.nextInt(5); c++) {
				switch (random.nextInt(5)) {
					case 0 -> pool.add(List.of(new String[]{"import os", "import sys", "from math import sqrt"}[random.nextInt(3)]));
					case 1 -> pool.add(List.of("x" + c + " = " + random.nextInt(9) + "  # note " + c));
					case 2 -> pool.add(List.of("class K" + c + ":", "    def m(self):", "        return " + c));
					case 3 -> pool.add(List.of("# a module comment " + c));
					default -> {
						List<String> function = new ArrayList<>(List.of("def f" + c + "(a):"));
						for (int s = random.nextInt(4); s > 0; s--)
							function.add(STATEMENTS[random.nextInt(STATEMENTS.length)]);
						function.add("    return a");
						pool.add(function);
					}
				}
			}
			List<String> variants = new ArrayList<>();
			for (int v = 2 + random.nextInt(3); v > 0; v--) {
				StringBuilder text = new StringBuilder();
				for (List<String> chunk : pool) {
					if (random.nextInt(10) >= 7)
						continue;
					boolean function = chunk.get(0).startsWith("def ");
					for (String line : chunk)
						if (!function || !line.startsWith("    ") || line.equals("    return a") || random.nextInt(10) < 7)
							text.append(line).append("\n");
					if (chunk.size() > 1)
						text.append("\n");
				}
				if (text.isEmpty())
					text.append(String.join("\n", pool.get(0))).append("\n");
				variants.add(text.toString());
			}

			Path dir = Files.createDirectories(tmp.resolve("round" + round));
			List<String> together = checkouts(dir.resolve("together"), variants, round == 0);
			for (int v = 0; v < variants.size(); v++) {
				String alone = checkouts(dir.resolve("alone" + v), List.of(variants.get(v)), false).get(0);
				if (!code(variants.get(v)).equals(code(alone)))
					wrong.add("round " + round + ", variant " + v + " alone:\n--- committed ---\n" + variants.get(v) + "--- checked out ---\n" + alone);
				else if (!alone.equals(together.get(v)))
					wrong.add("round " + round + ", variant " + v + " of " + variants.size() + " together:\n--- committed ---\n" + variants.get(v)
							+ "--- alone ---\n" + alone + "--- together ---\n" + together.get(v));
			}
		}
		assertEquals(List.of(), wrong);
	}

	/** [source] without blank lines and trailing whitespace - its code and comments, as the adapter keeps them */
	private static String code(String source) {
		StringBuilder code = new StringBuilder();
		for (String line : source.split("\n"))
			if (!line.isBlank())
				code.append(line.stripTrailing()).append("\n");
		return code.toString();
	}

	/** [variants] of main.py committed in turn into one repository, each under a feature of its own, then each checked out */
	private static List<String> checkouts(Path dir, List<String> variants, boolean checkPlugin) throws Exception {
		Path work = Files.createDirectories(dir.resolve("work"));
		List<String> result = new ArrayList<>();
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(dir.resolve(".ecco"));
			service.init();
			service.setBaseDir(work);
			for (int v = 0; v < variants.size(); v++) {
				Files.writeString(work.resolve("main.py"), variants.get(v));
				service.commit("variant " + v, "V" + v);
			}
			// the Python adapter read the file - with the file adapter every variant came back trivially
			if (checkPlugin) {
				Set<String> plugins = new TreeSet<>();
				for (at.jku.isse.ecco.core.Association association : service.getRepository().getAssociations())
					((at.jku.isse.ecco.core.Association.Op) association).getRootNode().traverse((at.jku.isse.ecco.tree.Node.Op node) -> {
						if (node.getArtifact() != null && node.getArtifact().getData() instanceof at.jku.isse.ecco.adapter.dispatch.PluginArtifactData data)
							plugins.add(data.getPluginId());
					});
				assertEquals(Set.of(PythonPlugin.class.getName()), plugins);
			}
			for (int v = 0; v < variants.size(); v++) {
				Path out = Files.createDirectories(dir.resolve("out" + v));
				service.setBaseDir(out);
				service.checkout("V" + v);
				result.add(Files.exists(out.resolve("main.py")) ? Files.readString(out.resolve("main.py")) : "(no file)");
			}
		}
		return result;
	}
}
