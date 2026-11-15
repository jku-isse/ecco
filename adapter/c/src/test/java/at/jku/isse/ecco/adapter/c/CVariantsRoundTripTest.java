package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Variants of one C file, generated: a pool of includes, defines, #if blocks, comments, declarations,
 * blank lines and functions - sometimes one starting on the line another ends on (gap #60) - each
 * variant with each of them or not and, of a function it has, each line of its body or not; LF or
 * CRLF, a final newline or none, UTF-8 or Latin-1. Each is committed under a feature and then checked
 * out: every one must come back byte for byte (the C adapter's fidelity, AD-6). The variants' functions
 * and lines are merged in the repository, which CRepositoryRoundTripTest's hand-written pairs cover
 * case by case.
 */
public class CVariantsRoundTripTest {

	private static final String[] STATEMENTS = {"int x = 1;", "x++;", "return x;", "if (x > 0) { x--; }", "printf(\"%d\\n\", x);",
			"// a comment", "", "\t\ty = x * 2;", "  trailing();  ", "s = \"größe\";"};

	@Test
	@Timeout(900)
	public void variantsOfAFileEachComeBackExactly(@TempDir Path tmp) throws Exception {
		Random random = new Random(3);
		List<String> wrong = new ArrayList<>();
		for (int round = 0; round < 400 && wrong.size() < 3; round++) {
			List<List<String>> pool = pool(random);
			List<byte[]> variants = new ArrayList<>();
			for (int v = 2 + random.nextInt(3); v > 0; v--) {
				List<String> lines = new ArrayList<>();
				for (List<String> chunk : pool) {
					if (random.nextInt(10) >= 7)
						continue;
					// a function (or two, the second starting where the first ends): the lines opening and
					// closing them, and each line of their bodies or not
					boolean function = chunk.get(0).endsWith("{");
					for (String line : chunk)
						if (!function || line.endsWith("{") || line.startsWith("}") || random.nextInt(10) < 7)
							lines.add(line);
				}
				if (lines.isEmpty())
					lines.addAll(pool.get(0));
				variants.add(encode(lines, random));
			}
			Path dir = Files.createDirectories(tmp.resolve("round" + round));
			Path work = Files.createDirectories(dir.resolve("work"));
			try (EccoService service = new EccoService()) {
				service.setRepositoryDir(dir.resolve(".ecco"));
				service.init();
				service.setBaseDir(work);
				for (int v = 0; v < variants.size(); v++) {
					Files.write(work.resolve("main.c"), variants.get(v));
					service.commit("variant " + v, "V" + v);
				}
				// the CPlugin read the file - with the file adapter every variant came back trivially
				if (round == 0)
					assertEquals(java.util.Set.of(CPlugin.class.getName()), pluginIds(service));
				for (int v = 0; v < variants.size(); v++) {
					Path out = Files.createDirectories(dir.resolve("out" + v));
					service.setBaseDir(out);
					service.checkout("V" + v);
					byte[] written = Files.exists(out.resolve("main.c")) ? Files.readAllBytes(out.resolve("main.c")) : new byte[0];
					if (!Arrays.equals(variants.get(v), written))
						wrong.add("round " + round + ", variant " + v + " of " + variants.size() + ":\n--- committed ---\n" + show(variants.get(v))
								+ "\n--- checked out ---\n" + show(written));
				}
			}
		}
		assertEquals(List.of(), wrong);
	}

	/** 2-7 top-level chunks of C, each a list of lines */
	private static List<List<String>> pool(Random random) {
		List<List<String>> pool = new ArrayList<>();
		int functions = 0;
		for (int i = 2 + random.nextInt(6); i > 0; i--) {
			switch (random.nextInt(7)) {
				case 0 -> pool.add(List.of("#include <stdio.h>"));
				case 1 -> pool.add(List.of("#define LIMIT " + random.nextInt(9)));
				case 2 -> pool.add(List.of("#if defined(FEATURE)", "int feature = 1;", "#endif"));
				case 3 -> pool.add(List.of(random.nextBoolean() ? "/* a block comment */" : "// a line comment", ""));
				case 4 -> pool.add(List.of("static int counter" + random.nextInt(3) + " = 0;"));
				default -> {
					String head = "int f" + functions++ + "(int x) {";
					// sometimes a function starts on the line the one before ends on (gap #60): one
					// chunk, which a variant has or not as a whole
					List<String> last = pool.isEmpty() ? null : pool.get(pool.size() - 1);
					List<String> function;
					if (last != null && last.get(0).endsWith("{") && random.nextInt(3) == 0) {
						function = new ArrayList<>(last.subList(0, last.size() - 1));
						function.add("} " + head);
						pool.remove(pool.size() - 1);
					} else {
						function = new ArrayList<>(List.of(head));
					}
					for (int s = 1 + random.nextInt(5); s > 0; s--)
						function.add("    " + STATEMENTS[random.nextInt(STATEMENTS.length)]);
					function.add("}");
					pool.add(function);
				}
			}
		}
		return pool;
	}

	/** [lines] with LF or CRLF, with or without a final newline, in UTF-8 or Latin-1 */
	private static byte[] encode(List<String> lines, Random random) {
		String separator = random.nextBoolean() ? "\n" : "\r\n";
		StringBuilder text = new StringBuilder(String.join(separator, lines));
		if (random.nextInt(4) != 0)
			text.append(separator);
		return random.nextInt(4) == 0
				? text.toString().getBytes(StandardCharsets.ISO_8859_1)
				: text.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String show(byte[] bytes) {
		return new String(bytes, StandardCharsets.ISO_8859_1).replace("\r", "\\r").replace("\t", "\\t");
	}

	/** the plugins whose artifacts the repository holds */
	static java.util.Set<String> pluginIds(EccoService service) {
		java.util.Set<String> ids = new java.util.TreeSet<>();
		for (at.jku.isse.ecco.core.Association association : service.getRepository().getAssociations())
			((at.jku.isse.ecco.core.Association.Op) association).getRootNode().traverse((at.jku.isse.ecco.tree.Node.Op node) -> {
				if (node.getArtifact() != null && node.getArtifact().getData() instanceof at.jku.isse.ecco.adapter.dispatch.PluginArtifactData data)
					ids.add(data.getPluginId());
			});
		return ids;
	}
}
