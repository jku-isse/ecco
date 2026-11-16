package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.AdapterPreferences;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Variants of one Go file, generated: imports, constants, variables, types and functions of a shared
 * pool, each variant with each of them or not and, of a function it has, each statement of its body or
 * not. Committed under a feature each, every variant must check out as committed - the adapter promises
 * exact reconstruction (AD-6) - and as from a repository holding it alone. The Go adapter is off by
 * default; the test turns it on for itself.
 */
public class GoVariantsRoundTripTest {

	private static final String[] STATEMENTS = {"\ta++", "\tfmt.Println(a)", "\tif a > 0 {\n\t\ta--\n\t}", "\t// a comment",
			"\tt := a * 2\n\t_ = t", "\tfor i := 0; i < 3; i++ {\n\t\ta += i\n\t}"};

	@Test
	@Timeout(1800)
	public void variantsOfAFileComeBackExactly(@TempDir Path tmp) throws Exception {
		Set<String> disabled = AdapterPreferences.getDisabledPluginIds();
		Set<String> withGo = new HashSet<>(disabled);
		withGo.remove(GoPlugin.class.getName());
		AdapterPreferences.setDisabledPluginIds(withGo);
		try {
			roundTrips(tmp);
		} finally {
			AdapterPreferences.setDisabledPluginIds(disabled);
		}
	}

	private void roundTrips(Path tmp) throws Exception {
		Random random = new Random(23);
		List<String> wrong = new ArrayList<>();
		for (int round = 0; round < 80 && wrong.size() < 3; round++) {
			List<List<String>> pool = new ArrayList<>();
			for (int c = 0; c < 2 + random.nextInt(5); c++) {
				switch (random.nextInt(6)) {
					case 0 -> pool.add(List.of("import \"" + new String[]{"fmt", "os", "strings"}[random.nextInt(3)] + "\""));
					case 1 -> pool.add(List.of("const c" + c + " = " + random.nextInt(9)));
					case 2 -> pool.add(List.of("var v" + c + " = " + random.nextInt(9) + " // note " + c));
					case 3 -> pool.add(List.of("type T" + c + " struct {", "\tA int", "\tB string", "}"));
					case 4 -> pool.add(List.of("// a comment " + c));
					default -> {
						List<String> function = new ArrayList<>(List.of("func f" + c + "(a int) int {"));
						for (int s = random.nextInt(4); s > 0; s--)
							function.add(STATEMENTS[random.nextInt(STATEMENTS.length)]);
						function.add("\treturn a");
						function.add("}");
						pool.add(function);
					}
				}
			}
			List<String> variants = new ArrayList<>();
			for (int v = 2 + random.nextInt(3); v > 0; v--) {
				StringBuilder text = new StringBuilder("package main\n\n");
				for (List<String> chunk : pool) {
					if (random.nextInt(10) >= 7)
						continue;
					boolean function = chunk.get(0).startsWith("func ");
					for (String line : chunk)
						if (!function || !line.startsWith("\t") || line.equals("\treturn a") || random.nextInt(10) < 7)
							text.append(line).append("\n");
					text.append("\n");
				}
				variants.add(text.toString());
			}

			Path dir = Files.createDirectories(tmp.resolve("round" + round));
			List<String> together = checkouts(dir.resolve("together"), variants, round == 0);
			for (int v = 0; v < variants.size(); v++) {
				String alone = checkouts(dir.resolve("alone" + v), List.of(variants.get(v)), false).get(0);
				if (!variants.get(v).equals(alone))
					wrong.add("round " + round + ", variant " + v + " alone:\n--- committed ---\n" + variants.get(v) + "--- checked out ---\n" + alone);
				else if (!alone.equals(together.get(v)))
					wrong.add("round " + round + ", variant " + v + " of " + variants.size() + " together:\n--- committed ---\n" + variants.get(v)
							+ "--- checked out ---\n" + together.get(v));
			}
		}
		assertEquals(List.of(), wrong);
	}

	/** [variants] of main.go committed in turn into one repository, each under a feature of its own, then each checked out */
	private static List<String> checkouts(Path dir, List<String> variants, boolean checkPlugin) throws Exception {
		Path work = Files.createDirectories(dir.resolve("work"));
		List<String> result = new ArrayList<>();
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(dir.resolve(".ecco"));
			service.init();
			service.setBaseDir(work);
			for (int v = 0; v < variants.size(); v++) {
				Files.writeString(work.resolve("main.go"), variants.get(v));
				service.commit("variant " + v, "V" + v);
			}
			// the Go adapter read the file - with the file adapter every variant came back trivially
			if (checkPlugin) {
				Set<String> plugins = new TreeSet<>();
				for (at.jku.isse.ecco.core.Association association : service.getRepository().getAssociations())
					((at.jku.isse.ecco.core.Association.Op) association).getRootNode().traverse((at.jku.isse.ecco.tree.Node.Op node) -> {
						if (node.getArtifact() != null && node.getArtifact().getData() instanceof at.jku.isse.ecco.adapter.dispatch.PluginArtifactData data)
							plugins.add(data.getPluginId());
					});
				assertEquals(Set.of(GoPlugin.class.getName()), plugins);
			}
			for (int v = 0; v < variants.size(); v++) {
				Path out = Files.createDirectories(dir.resolve("out" + v));
				service.setBaseDir(out);
				service.checkout("V" + v);
				result.add(Files.exists(out.resolve("main.go")) ? Files.readString(out.resolve("main.go")) : "(no file)");
			}
		}
		return result;
	}
}
