package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.printer;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.JavaEccoModelBuilderStrategy;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.StpEccoModelBuilderStrategy;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.plugin.artifact.GenericAdapterPlugin;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Michael Jahn
 */
public class GenericAdapterWriter implements ArtifactWriter<Set<Node>, Path> {


	private Collection<WriteListener> listeners = new ArrayList<>();
	private EccoModelPrinter eccoModelPrinter = new EccoModelPrinterImpl();

	// TODO scan strategies and return strategy ids
	private static final List<EccoModelBuilderStrategy> strategies = Arrays.asList(new JavaEccoModelBuilderStrategy(),
			new StpEccoModelBuilderStrategy());


	@Override
	public String getPluginId() {
		return GenericAdapterPlugin.class.getName();
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {

		System.out.println("BASE: " + base);

		List<Path> output = new ArrayList<Path>();

		for (Node fileNode : input) {
			Artifact<PluginArtifactData> fileArtifact = (Artifact<PluginArtifactData>) fileNode.getArtifact();
			Path outputPath = base.resolve(fileArtifact.getData().getPath());
			output.add(outputPath);

			// search for appropiate strategy
			Optional<EccoModelBuilderStrategy> optStrategy = strategies.stream().filter(st -> outputPath.getFileName().toString().endsWith(st.getFileExtension())).findFirst();
			EccoModelBuilderStrategy strategy;
			if (!optStrategy.isPresent()) {
				return null;
			} else {
				strategy = optStrategy.get();
			}

			try (BufferedWriter bw = Files.newBufferedWriter(outputPath)) {
				String model = eccoModelPrinter.printModelToString(fileNode.getChildren().get(0), strategy);
				bw.write(model);
				bw.newLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return output.toArray(new Path[output.size()]);
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}


	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}
}
