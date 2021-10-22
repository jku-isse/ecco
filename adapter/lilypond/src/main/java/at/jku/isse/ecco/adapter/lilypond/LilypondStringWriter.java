package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.context.BaseContextArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.Collection;
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
			for (Node n : fileNode.getChildren()) {
			    writeNodeRec(n, sb);
			}
			output.add(sb.toString());
		}

		return output.toArray(new String[0]);
	}

	private void writeNodeRec(Node n, StringBuilder sb) {
        ArtifactData d = n.getArtifact().getData();
        if (d instanceof BaseContextArtifactData) {
            for (Node cn : n.getChildren()) {
                writeNodeRec(cn, sb);
            }

        } else if (d instanceof DefaultTokenArtifactData) {
            DefaultTokenArtifactData dad = (DefaultTokenArtifactData)d;
            sb.append(dad.getText())
                    .append(dad.getPostWhitespace());
        }
    }

	private Collection<WriteListener> listeners = new ArrayList<>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
