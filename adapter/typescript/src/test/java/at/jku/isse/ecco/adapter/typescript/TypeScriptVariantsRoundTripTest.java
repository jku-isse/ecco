package at.jku.isse.ecco.adapter.typescript;

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

/**
 * Variants of one TypeScript file, generated: constants, typed variables with a trailing comment,
 * functions with JSDoc, interfaces, enums, classes and comments of a shared pool, each variant with
 * each of them or not and, of a function it has, each statement of its body or not. Committed under a
 * feature each, every variant must check out as committed - the adapter promises the exact text (AD-6)
 * - when it is alone in its repository; together with the others, its code - not the line breaks and
 * comments between statements, which a statement keeps from one variant as its leading text (gap #101).
 * TypeScriptRoundTripTest reads and writes single files.
 */
public class TypeScriptVariantsRoundTripTest {

	private static final String[] STATEMENTS = {"    a++;", "    console.log(a);", "    if (a > 0) { a--; }", "    // a comment",
			"    const t = a * 2;", "    for (let i = 0; i < 3; i++) { a += i; }"};

	@Test
	@Timeout(900)
	public void variantsOfAFileComeBackAsCommittedAloneAndTheirCodeTogether(@TempDir Path tmp) throws Exception {
		roundTrips(tmp, false);
	}

	@Test
	@org.junit.jupiter.api.Disabled("gap #101: a statement shared by variants keeps one variant's leading text - the line break and comments before it")
	@Timeout(900)
	public void variantsOfAFileComeBackExactlyTogether(@TempDir Path tmp) throws Exception {
		roundTrips(tmp, true);
	}

	private void roundTrips(Path tmp, boolean exactTogether) throws Exception {
		Random random = new Random(17);
		List<String> wrong = new ArrayList<>();
		for (int round = 0; round < 60 && wrong.size() < 3; round++) {
			List<List<String>> pool = new ArrayList<>();
			for (int c = 0; c < 2 + random.nextInt(5); c++) {
				switch (random.nextInt(7)) {
					case 0 -> pool.add(List.of("const c" + c + " = " + random.nextInt(9) + ";"));
					case 1 -> pool.add(List.of("let v" + c + ": number = " + random.nextInt(9) + "; // note " + c));
					case 2 -> pool.add(List.of("interface I" + c + " {", "    a: number;", "    b?: string;", "}"));
					case 3 -> pool.add(List.of("enum E" + c + " { A, B, C }"));
					case 4 -> pool.add(List.of("class K" + c + " {", "    m() {", "        return " + c + ";", "    }", "}"));
					case 5 -> pool.add(List.of("// a top-level comment " + c));
					default -> {
						List<String> function = new ArrayList<>();
						if (random.nextBoolean())
							function.add("/** Function " + c + ". */");
						function.add("function f" + c + "(a: number): number {");
						for (int s = random.nextInt(4); s > 0; s--)
							function.add(STATEMENTS[random.nextInt(STATEMENTS.length)]);
						function.add("    return a;");
						function.add("}");
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
					boolean function = chunk.stream().anyMatch(line -> line.startsWith("function "));
					for (String line : chunk)
						if (!function || !line.startsWith("    ") || line.equals("    return a;") || random.nextInt(10) < 7)
							text.append(line).append("\n");
				}
				if (text.isEmpty())
					text.append(String.join("\n", pool.get(0))).append("\n");
				variants.add(text.toString());
			}

			Path dir = Files.createDirectories(tmp.resolve("round" + round));
			List<String> together = checkouts(dir.resolve("together"), variants, round == 0);
			for (int v = 0; v < variants.size(); v++) {
				String alone = checkouts(dir.resolve("alone" + v), List.of(variants.get(v)), false).get(0);
				if (!variants.get(v).equals(alone))
					wrong.add("round " + round + ", variant " + v + " alone:\n--- committed ---\n" + variants.get(v) + "--- checked out ---\n" + alone);
				else if (exactTogether ? !alone.equals(together.get(v)) : !code(alone).equals(code(together.get(v))))
					wrong.add("round " + round + ", variant " + v + " of " + variants.size() + " together:\n--- committed ---\n" + variants.get(v)
							+ "--- checked out ---\n" + together.get(v));
			}
		}
		assertEquals(List.of(), wrong);
	}

	/** [source] without its comments and whitespace - its code (the generated files have no "//" in a string) */
	private static String code(String source) {
		return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "").replaceAll("\\s+", "");
	}

	/** [variants] of main.ts committed in turn into one repository, each under a feature of its own, then each checked out */
	private static List<String> checkouts(Path dir, List<String> variants, boolean checkPlugin) throws Exception {
		Path work = Files.createDirectories(dir.resolve("work"));
		List<String> result = new ArrayList<>();
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(dir.resolve(".ecco"));
			service.init();
			service.setBaseDir(work);
			for (int v = 0; v < variants.size(); v++) {
				Files.writeString(work.resolve("main.ts"), variants.get(v));
				service.commit("variant " + v, "V" + v);
			}
			// the TypeScript adapter read the file - with the file adapter every variant came back trivially
			if (checkPlugin) {
				Set<String> plugins = new TreeSet<>();
				for (at.jku.isse.ecco.core.Association association : service.getRepository().getAssociations())
					((at.jku.isse.ecco.core.Association.Op) association).getRootNode().traverse((at.jku.isse.ecco.tree.Node.Op node) -> {
						if (node.getArtifact() != null && node.getArtifact().getData() instanceof at.jku.isse.ecco.adapter.dispatch.PluginArtifactData data)
							plugins.add(data.getPluginId());
					});
				assertEquals(Set.of(TypeScriptPlugin.class.getName()), plugins);
			}
			for (int v = 0; v < variants.size(); v++) {
				Path out = Files.createDirectories(dir.resolve("out" + v));
				service.setBaseDir(out);
				service.checkout("V" + v);
				result.add(Files.exists(out.resolve("main.ts")) ? Files.readString(out.resolve("main.ts")) : "(no file)");
			}
		}
		return result;
	}
}
