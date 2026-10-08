package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The tokens each trace of a LilyPond repository holds - see LilypondRespelledVariantsTest. */
final class LilypondTraceMeasure {

	private LilypondTraceMeasure() {}

	/** Whether a repository created by these tests reads with musical tokens - the default, unless -Decco.lilypond.musicalTokens=false. */
	static boolean musicalTokens() {
		return Boolean.parseBoolean(System.getProperty("ecco.lilypond.musicalTokens", "true"));
	}

	/** Whether this machine can read such a repository: a musical one fails without lymodel, by design. */
	static boolean readable() {
		return !musicalTokens() || Lymusic.available();
	}

	static final String UNREADABLE = "a repository with musical tokens (the default) " + Lymusic.NEEDS
			+ " - or run with -Decco.lilypond.musicalTokens=false";

	/** Tokens in traces whose condition mentions [feature], as "condition: token" lines. */
	static List<String> tokensTracedTo(EccoService service, String feature) {
		List<String> out = new ArrayList<>();
		for (Association association : service.getRepository().getAssociations()) {
			String condition = association.computeCondition().toLogicString();
			if (condition.contains(feature)) {
				collect(association.getRootNode(), condition, out);
			}
		}
		return out;
	}

	private static void collect(Node node, String condition, List<String> out) {
		if (node.isUnique() && node.getArtifact() != null && node.getChildren().isEmpty()) {
			out.add(condition + ": " + node.getArtifact().getData());
		}
		for (Node child : node.getChildren()) {
			collect(child, condition, out);
		}
	}

	/** The first [history] variants of examples/lilypond_variants, then [last] as [configuration]. */
	static EccoService commitAfter(Path variants, int history, Path last, String configuration) throws java.io.IOException {
		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory("lilypond-respelled-repo").resolve(".ecco"));
		service.init();
		String[][] commits = {{"v1_setup", "setup.1"}, {"v2_setup_notes", "setup.1, notes.1"},
				{"v3_setup_notes_articulation", "setup.1, notes.1, articulation.1"},
				{"v4_setup_notes_articulation_lyrics", "setup.1, notes.1, articulation.1, lyrics.1"}};
		for (int i = 0; i < history; i++) {
			service.setBaseDir(variants.resolve(commits[i][0]));
			service.commit(commits[i][0], commits[i][1]);
		}
		service.setBaseDir(last);
		service.commit(last.getFileName().toString(), configuration);
		return service;
	}
}
