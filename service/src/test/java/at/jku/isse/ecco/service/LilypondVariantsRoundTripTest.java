package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
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
 * Variants of one LilyPond file, generated: a header, comments and variables of \\relative music of a
 * shared pool, each variant with each of them or not and, of a variable it has, each bar or not.
 * Committed under a feature each, every variant must check out as committed, byte for byte, when it is
 * alone in its repository; together with the others, with the same music (lymusic) - a token shared
 * with another variant is written with the whitespace before it and the spelling it had there, e.g.
 * c4 for c (gap #101). LilypondVariantsCommitCheckoutTest checks the corpus's variants.
 */
public class LilypondVariantsRoundTripTest {

	private static final String[] BARS = {"c4 d e f |", "g2 a |", "b4 c d e |", "f1 |", "r4 g a b |", "c8 d e f g4 g |"};

	@Test
	@Timeout(1800)
	public void variantsOfAFileComeBackAsCommittedAloneAndWithTheirMusicTogether(@TempDir Path tmp) throws Exception {
		assumeTrue(LilypondTraceMeasure.readable() && Lymusic.available(), LilypondTraceMeasure.UNREADABLE + "; " + Lymusic.NEEDS);
		roundTrips(tmp, false);
	}

	@Test
	@org.junit.jupiter.api.Disabled("gap #101: a token shared by variants is written with the whitespace before it and the spelling it had in one of them")
	@Timeout(1800)
	public void variantsOfAFileComeBackExactlyTogether(@TempDir Path tmp) throws Exception {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		roundTrips(tmp, true);
	}

	private void roundTrips(Path tmp, boolean exactTogether) throws Exception {
		Random random = new Random(29);
		List<String> wrong = new ArrayList<>();
		for (int round = 0; round < 25 && wrong.size() < 3; round++) {
			List<List<String>> pool = new ArrayList<>();
			if (random.nextBoolean())
				pool.add(List.of("\\header {", "  title = \"Piece " + round + "\"", "}"));
			for (int c = 0; c < 2 + random.nextInt(4); c++) {
				if (random.nextInt(4) == 0) {
					pool.add(List.of("% a comment " + c));
				} else {
					List<String> variable = new ArrayList<>(List.of("melody" + (char) ('A' + c) + " = \\relative c' {"));
					for (int b = 1 + random.nextInt(4); b > 0; b--)
						variable.add("  " + BARS[random.nextInt(BARS.length)]);
					variable.add("}");
					pool.add(variable);
				}
			}
			List<String> variants = new ArrayList<>();
			for (int v = 2 + random.nextInt(3); v > 0; v--) {
				StringBuilder text = new StringBuilder("\\version \"2.24.0\"\n\n");
				for (List<String> chunk : pool) {
					if (random.nextInt(10) >= 7)
						continue;
					boolean variable = chunk.get(0).startsWith("melody");
					for (String line : chunk)
						if (!variable || !line.startsWith("  ") || random.nextInt(10) < 7)
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
				else if (!alone.equals(together.get(v)) && (exactTogether || !sameMusic(dir, v, alone, together.get(v))))
					wrong.add("round " + round + ", variant " + v + " of " + variants.size() + " together:\n--- committed ---\n" + variants.get(v)
							+ "--- checked out ---\n" + together.get(v));
			}
		}
		assertEquals(List.of(), wrong);
	}

	/** whether [alone] and [together] hold the same music, by lymusic */
	private static boolean sameMusic(Path dir, int v, String alone, String together) throws Exception {
		Path a = Files.writeString(dir.resolve("alone-" + v + ".ly"), alone);
		Path t = Files.writeString(dir.resolve("together-" + v + ".ly"), together);
		try {
			Lymusic.assertSameMusic(a, t, "notes,lyrics,articulations,slurs,dynamics,attributes", "variant " + v);
			return true;
		} catch (AssertionError e) {
			return false;
		}
	}

	/** [variants] of piece.ly committed in turn into one repository, each under a feature of its own, then each checked out */
	private static List<String> checkouts(Path dir, List<String> variants, boolean checkPlugin) throws Exception {
		Path work = Files.createDirectories(dir.resolve("work"));
		List<String> result = new ArrayList<>();
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(dir.resolve(".ecco"));
			service.init();
			service.setBaseDir(work);
			for (int v = 0; v < variants.size(); v++) {
				Files.writeString(work.resolve("piece.ly"), variants.get(v));
				service.commit("variant " + v, "V" + v);
			}
			// the LilyPond adapter read the file - with the file adapter every variant came back trivially
			if (checkPlugin) {
				Set<String> plugins = new TreeSet<>();
				for (at.jku.isse.ecco.core.Association association : service.getRepository().getAssociations())
					((at.jku.isse.ecco.core.Association.Op) association).getRootNode().traverse((at.jku.isse.ecco.tree.Node.Op node) -> {
						if (node.getArtifact() != null && node.getArtifact().getData() instanceof at.jku.isse.ecco.adapter.dispatch.PluginArtifactData data)
							plugins.add(data.getPluginId());
					});
				assertEquals(Set.of(LilypondPlugin.class.getName()), plugins);
			}
			for (int v = 0; v < variants.size(); v++) {
				Path out = Files.createDirectories(dir.resolve("out" + v));
				service.setBaseDir(out);
				service.checkout("V" + v);
				result.add(Files.exists(out.resolve("piece.ly")) ? Files.readString(out.resolve("piece.ly")) : "(no file)");
			}
		}
		return result;
	}
}
