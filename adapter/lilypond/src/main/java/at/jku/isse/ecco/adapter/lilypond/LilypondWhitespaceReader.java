package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class LilypondWhitespaceReader implements ArtifactReader<Path, Set<Node>> {

	private final EntityFactory entityFactory;

	@Inject
	public LilypondWhitespaceReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}

	private static final Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(1, new String[]{"**.ly", "**.ily"});
	}

	@Override
	public Map<Integer, String[]> getPrioritizedPatterns() {
		return Collections.unmodifiableMap(prioritizedPatterns);
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {

        Set<Node> nodes = new HashSet<>();
        for (Path path : input) {
            Path resolvedPath = base.resolve(path);
            Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
            Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
            nodes.add(pluginNode);

            Scanner sc;
            int i = 0;
            try {
                sc = new Scanner(resolvedPath.toFile());
                while (sc.hasNext()) {
                    i++;
                    String tk = sc.next();
                    DefaultTokenArtifactData dtad = new DefaultTokenArtifactData(new ParceToken(i, tk, "unknown"));
                    Artifact.Op<DefaultTokenArtifactData> lpaf = this.entityFactory.createArtifact(dtad);
                    Node.Op lpNode = this.entityFactory.createNode(lpaf);
                    lpNode.putProperty("TOKEN_START", i);
                    lpNode.putProperty("TOKEN_END", i + tk.length());

                    pluginNode.addChild(lpNode);
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            //listeners.stream().forEach(l -> l.fileReadEvent(file, this));
        }

        return nodes;
	}

	private Collection<ReadListener> listeners = new ArrayList<>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
