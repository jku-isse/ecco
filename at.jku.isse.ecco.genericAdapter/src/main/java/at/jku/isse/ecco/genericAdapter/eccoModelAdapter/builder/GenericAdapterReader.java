package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.JavaEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.StpEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacade;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacadeImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.parserGenerator.AntlrParserWrapperServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.GenericAdapterPlugin;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Michael Jahn
 */
public class GenericAdapterReader implements ArtifactReader<Path, Set<Node>> {

	// TODO scan strategies and return strategy ids
	private static final List<EccoModelBuilderStrategy> strategies = Arrays.asList(new JavaEccoModelBuilderStrategy(),
			new StpEccoModelBuilderStrategy());

	private Collection<ReadListener> listeners = new ArrayList<>();

	private final EntityFactory entityFactory;
	private final EccoModelBuilder eccoModelBuilder;
	private final GrammarInferenceFacade grammarInferenceFacade;

	@Inject
	public GenericAdapterReader(EntityFactory entityFactory, @Named("repositoryDir") String repositoryDir) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;

		this.eccoModelBuilder = new EccoModelBuilderImpl(entityFactory, new AntlrParserWrapperServiceImpl());
		this.grammarInferenceFacade = new GrammarInferenceFacadeImpl();
	}


	@Override
	public String getPluginId() {
		return GenericAdapterPlugin.class.getName();
	}

	@Override
	public String[] getTypeHierarchy() {
		return new String[]{"java", "stp"};
//        return (String[]) strategies.stream().map(EccoModelBuilderStrategy::getStrategyName).collect(toList()).toArray();
	}

	@Override
	public boolean canRead(Path path) {
		if (!Files.isDirectory(path) && Files.isRegularFile(path)) {
			String filePath = path.getFileName().toString().toLowerCase();
			return strategies.stream().anyMatch(strategy -> filePath.endsWith(strategy.getFileExtension()));
		} else {
			return false;
		}
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {

		if (input.length <= 0) {
			return new HashSet<>();
		}
		Optional<EccoModelBuilderStrategy> optStrategy = strategies.stream().filter(st -> input[0].toString().endsWith(st.getFileExtension())).findFirst();
		EccoModelBuilderStrategy strategy;
		if (!optStrategy.isPresent()) {
			return null;
		} else {
			strategy = optStrategy.get();
		}

		List<String> resolvedPaths = new ArrayList<>();
		for (Path inputPath : input) {
			resolvedPaths.add(base.resolve(inputPath).toString());
		}

		// infer grammar
		NonTerminal rootSymbol = null;
		try {
			rootSymbol = grammarInferenceFacade.inferGrammar(resolvedPaths, strategy);
		} catch (IOException | AmbiguousTokenDefinitionsException e) {
			e.printStackTrace();
		}

		if (rootSymbol == null) {
			return null;
		}


		// build ecco nodes
		Set<Node> nodes = new HashSet<>();
		for (int i = 0; i < resolvedPaths.size(); i++) {
			Artifact<PluginArtifactData> fileArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), input[i]));
			Node fileNode = this.entityFactory.createOrderedNode(fileArtifact);
			nodes.add(fileNode);

			try {
				Node node = eccoModelBuilder.buildEccoModel(strategy, rootSymbol, resolvedPaths.get(i), false);
				if (node != null) {
					fileNode.addChild(node);
				}
			} catch (IOException | AmbiguousTokenDefinitionsException e) {
				e.printStackTrace();
			}
		}

		return nodes;
	}


	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}
}
