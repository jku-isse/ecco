package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.JavaEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.StpEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacade;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacadeImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.main.GrammarInferencerMain;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.parserGenerator.AntlrParserWrapperServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.GenericAdapterPlugin;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.sun.org.apache.xerces.internal.xni.grammars.Grammar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
        return strategies.stream().map(EccoModelBuilderStrategy::getStrategyName).collect(Collectors.toList()).toArray(new String[10]);
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

		// try to find grammar file
		String grammarFilePath = null;
		try {
			List<Path> grammarFiles = Files.list(new File(GrammarInferencerMain.DEFAULT_GRAMMAR_PATH_ROOT).toPath())
					.filter(it -> it.toString().endsWith(AntlrParserWrapperServiceImpl.GRAMMAR_FILE_EXTIONS)).collect(Collectors.toList());
			Optional<Path> grammarFileOpt = grammarFiles.stream().filter(it -> it.toString().endsWith(strategy.getStrategyName() + "." + AntlrParserWrapperServiceImpl.GRAMMAR_FILE_EXTIONS)).findFirst();
			if(!grammarFileOpt.isPresent()) {
				System.err.println("Could not find grammar file for type: " + strategy.getStrategyName() + " in path: " + GrammarInferencerMain.DEFAULT_GRAMMAR_PATH_ROOT);
				System.err.println("Found grammar files: ");
				grammarFiles.stream().forEach(System.err::println);
			}  else {
				grammarFilePath = grammarFileOpt.get().toString();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// if no grammar file was found, infer grammar here
		NonTerminal rootSymbol = null;
		if(grammarFilePath == null) {
			System.err.println("ATTENTION! Inferring grammar only on set of input files is risky!");
			try {
				rootSymbol = grammarInferenceFacade.inferGrammar(resolvedPaths, strategy);
			} catch (IOException | AmbiguousTokenDefinitionsException e) {
				e.printStackTrace();
				return null;
			}
		}


		// build ecco nodes
		Set<Node> nodes = new HashSet<>();
		for (int i = 0; i < resolvedPaths.size(); i++) {
			Artifact<PluginArtifactData> fileArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), input[i]));
			Node fileNode = this.entityFactory.createOrderedNode(fileArtifact);
			nodes.add(fileNode);

			try {
				Node node = null;
				if (grammarFilePath != null) {
					node = eccoModelBuilder.buildEccoModel(strategy, grammarFilePath, resolvedPaths.get(i), false);
				} else {
					if(rootSymbol != null) {
						node = eccoModelBuilder.buildEccoModel(strategy, rootSymbol, resolvedPaths.get(i), false);
					}
				}
				if (node != null) {
					fileNode.addChild(node);
				}
			} catch (IOException | AmbiguousTokenDefinitionsException e) {
				e.printStackTrace();
				return null;
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
