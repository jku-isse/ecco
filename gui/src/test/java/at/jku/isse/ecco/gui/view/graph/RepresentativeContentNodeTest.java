package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The knowledge graph's association preview opens at the association's first content node - not at
 * whitespace (a LilyPond line break), which would scroll it to blank lines above the content.
 */
public class RepresentativeContentNodeTest {

	private record Token(String text) implements ArtifactData {
		@Override
		public boolean isWhitespace() {
			return this.text.isBlank();
		}
	}

	private static SerNode node(ArtifactData data, Node.Op... children) {
		SerNode node = new SerNode(new SerArtifact<>(data));
		for (Node.Op child : children)
			node.addChild(child);
		return node;
	}

	@Test
	public void skipsLeadingWhitespace() {
		SerNode content = node(new Token("tenorOneVoice"));
		SerNode file = node(new PluginArtifactData("lilypond", Path.of("a.ly")), node(new Token("\n\n")), content);
		assertSame(content, KnowledgeGraphView.findRepresentativeContentNode(file));
	}

	@Test
	public void takesWhitespaceWhenThereIsNothingElse() {
		SerNode lineBreak = node(new Token("\n"));
		SerNode file = node(new PluginArtifactData("lilypond", Path.of("a.ly")), lineBreak);
		assertSame(lineBreak, KnowledgeGraphView.findRepresentativeContentNode(file));
	}
}
