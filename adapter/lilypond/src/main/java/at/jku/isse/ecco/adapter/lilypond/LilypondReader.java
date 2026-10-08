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
import com.google.inject.name.Named;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
    public final static String PARSER_ACTION_LINEBREAK = "__LineBreak";

    private final Path repositoryDir;

    /** Without a repository (tests): musical tokens if -Decco.lilypond.musicalTokens=true. */
    public LilypondReader(EntityFactory entityFactory) {
        this(entityFactory, null);
    }

    @Inject
    public LilypondReader(EntityFactory entityFactory, @Named("repositoryDir") Path repositoryDir) {
        checkNotNull(entityFactory);

        this.entityFactory = entityFactory;
        this.repositoryDir = repositoryDir;
    }

    /**
     * Whether this repository reads LilyPond with musical tokens: what it recorded when it was created
     * ({@code .ecco/.settings}, see LilypondPlugin#newRepositorySettings). A repository from before
     * has no such setting and keeps plain tokens - its artifacts are plain, and musical ones would
     * never match them. Read on every read, not once: a new repository's readers are built before its
     * settings are written.
     */
    boolean musicalTokens() {
        if (this.repositoryDir == null) {
            return Boolean.getBoolean("ecco.lilypond.musicalTokens");
        }
        Path settings = this.repositoryDir.resolve(".settings");
        if (!Files.exists(settings)) {
            return false;
        }
        Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(settings)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + settings, e);
        }
        return Boolean.parseBoolean(properties.getProperty(LilypondPlugin.MUSICAL_TOKENS_SETTING, "false"));
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
        boolean musical = this.musicalTokens();
        try {
            if (parser == null) {
                throw new IOException("no parser found");
            }
            parser.setMusicalTokens(musical);
            parser.init();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "could not initialize parser", e);
            throw new RuntimeException("could not initialize parser", e);
        }

        try {
            for (Path path : input) {
                Path resolvedPath = base.resolve(path);
                Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
                Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
                nodes.add(pluginNode);

                LilypondNode<ParceToken> head = parser.parse(resolvedPath, tokenMetric);
                if (head == null) {
                    LOGGER.log(Level.SEVERE, "parser returned no node, file {0}", resolvedPath);
                } else {
                    head = LilyEccoTransformer.transform(head, musical);
                    generateEccoTree(head, pluginNode);
                }

                listeners.forEach(l -> l.fileReadEvent(resolvedPath, this));
            }
        } finally {
            // also when a parse fails, so the parser's gateway doesn't leak
            parser.shutdown();
        }

        return nodes;
    }

    public void generateEccoTree(LilypondNode<ParceToken> head, Node.Op node) {
        Artifact.Op<ArtifactData> a;
        Node.Op nop;

        LilypondNode<ParceToken> n = head;
        int cntNodes = 0;
        while (n != null) {
            a = n.getData() == null ?
                this.entityFactory.createArtifact(ContextArtifactDataFactory.getContextArtifactData(n.getName())) :
                this.entityFactory.createArtifact(TokenArtifactDataFactory.getTokenArtifactData(n.getData()));

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
            while (n != null && node != null && n.getLevel() < prevLevel) {
                prevLevel--;
                //LOG.trace("({}) ecco-node level ({}) == node level ({})", cntNodes, node.computeDepth(), n.getLevel());
                node = node.getParent();
            }
            if (node == null && n != null) {
                // a level drop bigger than the ecco-tree's own remaining depth - can happen after
                // a LilyEccoTransformer splice sets a synthetic node's level from an arbitrary
                // earlier node, so adjacent nodes aren't guaranteed to differ by only one level.
                // Stop here instead of continuing: the next iteration would otherwise NPE trying
                // to add a child to a null node.
                LOGGER.log(Level.SEVERE, "EccoNode is null after {0} nodes - stopping tree construction early", cntNodes);
                break;
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
