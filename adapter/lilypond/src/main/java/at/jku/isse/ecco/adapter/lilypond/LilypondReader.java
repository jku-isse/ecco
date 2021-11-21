package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.ContextArtifactDataFactory;
import at.jku.isse.ecco.adapter.lilypond.data.TokenArtifactDataFactory;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class LilypondReader implements ArtifactReader<Path, Set<Node.Op>> {
    private static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());
    protected final EntityFactory entityFactory;
    private HashMap<String, Integer> tokenMetric;

    public static Logger getLogger() {
        return LOGGER;
    }

    @Inject
    public LilypondReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);

        //LOGGER.setLevel(Level.FINEST);                // change level for logger (e.g. DEBUG)
        //LOGGER.info("LilypondReader logging level: " + LOGGER.getLevel());
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

    public void setGenerateTokenMetric(boolean generate) {
        if (generate) {
            tokenMetric = new HashMap<>();
        } else {
            tokenMetric = null;
        }
    }

    public Map<String, Integer> getTokenMetric() {
        return null == tokenMetric ? null : Collections.unmodifiableMap(tokenMetric);
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return this.read(Paths.get("."), input);
    }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();

        LilypondParser<ParceToken> parser = ParserFactory.getParser();
        try {
            parser.init();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "could not initialize parser", e);
            return nodes;
        }

        for (Path path : input) {
            Path resolvedPath = base.resolve(path);
            Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
            Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
            nodes.add(pluginNode);

            LilypondNode<ParceToken> head = parser.parse(resolvedPath, tokenMetric);
            if (head == null) {
                LOGGER.log(Level.SEVERE, "parser returned no node, file {0}", resolvedPath);
            } else {
                LOGGER.setLevel(Level.FINEST);                // change level for logger (e.g. DEBUG)
                head = LilyEccoTransformer.transform(head);
                generateEccoTree(head, pluginNode);
            }

            listeners.forEach(l -> l.fileReadEvent(resolvedPath, this));
        }

        parser.shutdown();

        return nodes;
    }

    private void generateEccoTree(LilypondNode<ParceToken> head, Node.Op node) {
        Artifact.Op<ArtifactData> a;
        Node.Op nop;

        LilypondNode<ParceToken> n = head;
        int cntNodes = 0;
        while (n != null) {
            a = n.getData() == null ?
                this.entityFactory.createArtifact(ContextArtifactDataFactory.getContextArtifactData(n.getName())) :
                this.entityFactory.createArtifact(TokenArtifactDataFactory.getTokenArtifactData(n.getData()));

            assert node != null;
            if (n.getNext() != null && n.getNext().getLevel() > n.getLevel()) {
                nop = this.entityFactory.createOrderedNode(a);
                node.addChild(nop);
                node = nop;

            } else {
                nop = this.entityFactory.createNode(a);
                node.addChild(nop);
            }
            cntNodes++;

            int prevLevel = n.getLevel();
            n = n.getNext();
            while (n != null && n.getLevel() < prevLevel) {
                prevLevel--;
                //LOG.trace("({}) ecco-node level ({}) == node level ({})", cntNodes, node.computeDepth(), n.getLevel());
                node = node.getParent();
            }
            if (node == null && n != null) {
                LOGGER.log(Level.SEVERE, "EccoNode is null after node {0}", cntNodes);
            }
        }
    }

	private final Collection<ReadListener> listeners = new ArrayList<>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
