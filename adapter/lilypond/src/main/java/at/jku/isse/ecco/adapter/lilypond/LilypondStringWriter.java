package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class LilypondStringWriter implements ArtifactWriter<Set<Node>, String> {

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}

	@Override
	public String[] write(String base, Set<Node> input) {
		return this.write(input);
	}

	@Override
	public String[] write(Set<Node> input) {
		List<String> output = new ArrayList<>();

		// Every node in the input is a text file. The children of files are contexts or tokens. The children of contexts and tokens characters.
		for (Node fileNode : input) {
            Artifact<?> fileArtifact = fileNode.getArtifact();
            ArtifactData artifactData = fileArtifact.getData();
            if (!(artifactData instanceof PluginArtifactData))
                throw new EccoException("Expected plugin artifact data.");

			StringBuilder sb = new StringBuilder();
			List<Node> tokenNodes = new ArrayList<>();
			LilypondWriter.collectTokenNodes(fileNode, tokenNodes);

			if (!tokenNodes.isEmpty()) {
				List<DefaultTokenArtifactData> tokens = new ArrayList<>(tokenNodes.size());
				for (Node node : tokenNodes) tokens.add((DefaultTokenArtifactData) node.getArtifact().getData());
				List<String> texts = LilypondRelativizer.texts(tokens);
				for (int i = 0; i < tokens.size(); i++) {
					sb.append(texts.get(i));
					if (i + 1 < tokens.size() && LilypondFormatter.appendSpace(tokens.get(i), tokens.get(i + 1))) {
						sb.append(" ");
					}
				}
			}

			output.add(sb.toString());
		}

		return output.toArray(new String[0]);
	}

	private final Collection<WriteListener> listeners = new ArrayList<>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
