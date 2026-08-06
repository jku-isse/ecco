package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.JavaEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.StpEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacade;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.GrammarInferenceFacadeImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.main.GrammarInferencerMain;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.parserGenerator.AntlrParserWrapperServiceImpl;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.tokenization.AmbiguousTokenDefinitionsException;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.GenericAdapterPlugin;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import com.google.inject.name.Named;

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
	private final AntlrParserWrapperServiceImpl antlrParserService;
	private final String grammarDataBaseDir;

	@Inject
	public GenericAdapterReader(EntityFactory entityFactory, @Named("repositoryDir") String repositoryDir) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;

		this.antlrParserService = new AntlrParserWrapperServiceImpl();
		this.eccoModelBuilder = new EccoModelBuilderImpl(entityFactory, antlrParserService);
		this.grammarInferenceFacade = new GrammarInferenceFacadeImpl();
//		this.grammarDataBaseDir = = GrammarInferencerMain.DEFAULT_GRAMMAR_PATH_ROOT;
		this.grammarDataBaseDir = repositoryDir;
	}


	@Override
	public String getPluginId() {
		return GenericAdapterPlugin.class.getName();
	}

	@Override
	public String[] getTypeHierarchy() {
		//return strategies.stream().map(EccoModelBuilderStrategy::getStrategyName).collect(Collectors.toList()).toArray(new String[10]);
		return new String[]{"text"};
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
		String grammarFilePath = locateFile(strategy, AntlrParserWrapperServiceImpl.GRAMMAR_FILE_EXTIONS);

		// if no grammar file was found, infer grammar here
		NonTerminal rootSymbol = null;
		if (grammarFilePath == null) {
			if (ParameterSettings.INFO_OUTPUT)
				System.err.println("ATTENTION! Inferring grammar only on set of input files is risky!");
			try {
				rootSymbol = grammarInferenceFacade.inferGrammar(resolvedPaths, strategy, getGrammarDataOutputPath(strategy));
				String outputFilePath = antlrParserService.writeAntlrGrammarToFile(new File(grammarDataBaseDir).toPath(), strategy.getStrategyName(), rootSymbol, true);

				if (outputFilePath != null) {
					System.out.println("----------------------------\nInferred Grammar g4 file successfully written to: " + outputFilePath);
				} else {
					System.err.println("An error occured while trying to write to the output path!");
				}
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

			Node node = null;
			try {
				if (grammarFilePath != null) {
					node = eccoModelBuilder.buildEccoModel(strategy, grammarFilePath, resolvedPaths.get(i), false);
				} else {
					if (rootSymbol != null) {
						node = eccoModelBuilder.buildEccoModel(strategy, rootSymbol, resolvedPaths.get(i), false);
					}
				}

			} catch (IOException | AmbiguousTokenDefinitionsException e) {
				e.printStackTrace();
				return null;
			} catch (ParserErrorException e) {
				// file could not be parsed by current grammar -> search for grammar data file and try to update grammar
				System.err.println("Parser error found in file " + resolvedPaths.get(i) + ": " + e.getParseErrorDescription());
				System.err.println("Trying to update grammar and reparse the file;");

				String grammarDatFile = locateFile(strategy, GrammarInferencerMain.GRAMMAR_DATA_FILE_EXTIONS);
				if (grammarDatFile != null) {
					try {
						rootSymbol = grammarInferenceFacade.updateGrammar(Arrays.asList(resolvedPaths.get(i)), strategy, grammarDatFile);

						if (rootSymbol != null) {
							try {
								node = eccoModelBuilder.buildEccoModel(strategy, rootSymbol, resolvedPaths.get(i), false);
								if (grammarFilePath != null && !grammarFilePath.isEmpty()) {
									antlrParserService.writeAntlrGrammarToFile(new File(grammarDataBaseDir).toPath(), strategy.getStrategyName(), rootSymbol, true);
								}
							} catch (ParserErrorException e1) {
								System.err.println("Parse error found after trying to update grammar: " + e1.getParseErrorDescription());
								e1.printStackTrace();
							}
						}
					} catch (IOException | AmbiguousTokenDefinitionsException e1) {
						e1.printStackTrace();
						return null;
					}
				}
			}
			if (node != null) {
				fileNode.addChild(node);
			}
		}

		return nodes;
	}

	private String getGrammarDataOutputPath(EccoModelBuilderStrategy strategy) {
		return grammarDataBaseDir + File.separator + strategy.getStrategyName() + "." + GrammarInferencerMain.GRAMMAR_DATA_FILE_EXTIONS;
	}

	private String locateFile(EccoModelBuilderStrategy strategy, String fileExtension) {
		String grammarFilePath = null;
		try {
			List<Path> grammarFiles = Files.list(new File(grammarDataBaseDir).toPath())
					.filter(it -> it.toString().endsWith(fileExtension)).collect(Collectors.toList());
			Optional<Path> grammarFileOpt = grammarFiles.stream().filter(it -> it.toString().endsWith(strategy.getStrategyName() + "." + fileExtension)).findFirst();
			if (!grammarFileOpt.isPresent()) {
				System.err.println("Could not find grammar (data) file for type: " + strategy.getStrategyName() + fileExtension + " in path: " + grammarDataBaseDir);
				System.err.println("Found grammar files: ");
				grammarFiles.stream().forEach(System.err::println);
			} else {
				grammarFilePath = grammarFileOpt.get().toString();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return grammarFilePath;
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
