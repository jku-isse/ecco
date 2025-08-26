package at.jku.isse.ecco.adapter.ds;

import at.jku.isse.designspace.variant.dto.*;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.ds.util.DsDtoFactory;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DSFileWriter implements ArtifactWriter<Set<Node>, Path> {

	@Override
	public String getPluginId() {
		return DSPlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<>();

		for (Node pluginNode : input) {
			if (pluginNode.getArtifact().getData() instanceof PluginArtifactData pluginArtifactData) {
				Path outputPath = base.resolve(pluginArtifactData.getPath());
				output.add(outputPath);
				this.writeFolderDto(outputPath, pluginNode.getChildren());
			} else {
				throw new RuntimeException("Top nodes are not plugin nodes.");
			}
		}

		return output.toArray(new Path[0]);
	}

	private void writeFolderDto(Path outputPath, List<? extends Node> instanceTypeNodes) {
		Set<InstanceTypeDto> instanceTypeDtos = new HashSet<>();
		for (Node instanceTypeNode : instanceTypeNodes){
			instanceTypeDtos.add(DsDtoFactory.createIntanceTypeDto(instanceTypeNode));
		}
		FolderDto folderDto = new FolderDto("root", instanceTypeDtos);

		Gson gson = new GsonBuilder()
				.registerTypeAdapter(PropertyDto.class, new PropertyDtoDeserializer())
				.registerTypeAdapter(ValueDto.class, new ValueDtoDeserializer())
				.create();

		String json = gson.toJson(folderDto);

		try (FileWriter fileWriter = new FileWriter(outputPath.toFile())) {
			fileWriter.write(json);
		} catch (IOException e) {
			throw new RuntimeException(e);
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
