package at.jku.isse.ecco.adapter.text;

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
 * Variants of one text file, generated: each line of a shared pool in it or not - lines repeat, as
 * "}" does in code, and include tabs, trailing spaces, blank and whitespace-only lines and non-ASCII
 * characters - each variant with its own line ends (LF, CRLF or CR), final newline or none, and
 * charset (UTF-8 or Latin-1, FR-K10a) - in a quarter of them line ends mixed, line by line (gap #53) -
 * committed under a feature each and then each checked out: every one must come back byte for byte
 * (AD-6). Its lines are merged with the other variants' in the repository, which TextRoundTripTest's
 * single files never exercise.
 */
public class TextVariantsRoundTripTest {

	private static final String[] LINES = {"int x = 1;", "}", "}", "", "", "   ", "\tindented", "trailing  ", "café", "größe", "€ 5",
			"return x;", "// comment", "{", "x"};

	@Test
	@Timeout(900)
	public void variantsOfAFileEachComeBackExactly(@TempDir Path tmp) throws Exception {
		Random random = new Random(5);
		List<String> wrong = new ArrayList<>();
		for (int round = 0; round < 600 && wrong.size() < 3; round++) {
			List<String> pool = new ArrayList<>();
			for (int i = 2 + random.nextInt(10); i > 0; i--)
				pool.add(LINES[random.nextInt(LINES.length)]);
			List<byte[]> variants = new ArrayList<>();
			for (int v = 2 + random.nextInt(3); v > 0; v--) {
				List<String> lines = new ArrayList<>();
				for (String line : pool)
					if (random.nextInt(10) < 7)
						lines.add(line);
				if (lines.isEmpty())
					lines.add(pool.get(0));
				variants.add(encode(lines, random));
			}
			Path dir = Files.createDirectories(tmp.resolve("round" + round));
			Path work = Files.createDirectories(dir.resolve("work"));
			try (EccoService service = new EccoService()) {
				service.setRepositoryDir(dir.resolve(".ecco"));
				service.init();
				service.setBaseDir(work);
				for (int v = 0; v < variants.size(); v++) {
					Files.write(work.resolve("a.txt"), variants.get(v));
					service.commit("variant " + v, "V" + v);
				}
				// the TextPlugin read the file - with the file adapter every variant came back trivially
				if (round == 0)
					assertEquals(java.util.Set.of(TextPlugin.class.getName()), pluginIds(service));
				for (int v = 0; v < variants.size(); v++) {
					Path out = Files.createDirectories(dir.resolve("out" + v));
					service.setBaseDir(out);
					service.checkout("V" + v);
					byte[] written = Files.exists(out.resolve("a.txt")) ? Files.readAllBytes(out.resolve("a.txt")) : new byte[0];
					if (!Arrays.equals(variants.get(v), written))
						wrong.add("round " + round + ", variant " + v + " of " + variants.size() + ":\n--- committed ---\n" + show(variants.get(v))
								+ "\n--- checked out ---\n" + show(written));
				}
			}
		}
		assertEquals(List.of(), wrong);
	}

	/** [lines] with LF, CRLF or CR - or each line its own - with or without a final newline, in UTF-8 or Latin-1 */
	private static byte[] encode(List<String> lines, Random random) {
		String[] separators = {"\n", "\n", "\r\n", "\r"};
		String separator = separators[random.nextInt(4)];
		boolean mixed = random.nextInt(4) == 0;
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			text.append(lines.get(i));
			if (i < lines.size() - 1 || random.nextInt(4) != 0)
				text.append(mixed ? separators[random.nextInt(4)] : separator);
		}
		return random.nextInt(4) == 0
				? text.toString().replace("€", "EUR").getBytes(StandardCharsets.ISO_8859_1)
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
