package at.jku.isse.ecco.plugin.artifact.runtime;

import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RuntimeWriter implements ArtifactWriter<Set<Node>, Path> {

	public RuntimeWriter() {

	}

	@Override
	public String getPluginId() {
		return RuntimePlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> input) {
		return this.write(Paths.get("."), input);
	}

	@Override
	public Path[] write(Path base, Set<Node> input) {
		List<Path> output = new ArrayList<Path>();
		try {

			Iterator i = input.iterator();
			while (i.hasNext()) {
				Node tempNode = (Node) i.next();

				if (tempNode.getArtifact().getData() instanceof PluginArtifactData) {
					PluginArtifactData pad = (PluginArtifactData) tempNode.getArtifact().getData();
					List<Node> children = tempNode.getChildren();
					try {
						boolean fileCreated = false;
						PrintWriter writer = null;
						for (Node tempChildNode : children) {
							if (tempChildNode.getArtifact().getData() instanceof ConfigArtifactData) {
								if (!fileCreated) {
									Path outputPath = base.resolve("/config/" + pad.getFileName());
									output.add(outputPath);
									new File(base.toString() + "/config").mkdirs();
									writer = new PrintWriter(base.toString() + "/config/" + pad.getFileName(), "UTF-8");
									fileCreated = true;
								}
								ConfigArtifactData tempCad = (ConfigArtifactData) tempChildNode.getArtifact().getData();
								try {
									if (tempCad.getType().equals("conn")) {
										writer.println("conn=" + tempCad.getValue());
									} else if (tempCad.getType().equals("source")) {
										writer.println("source=" + tempCad.getValue());
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							} else {
								break;
							}
						}
						if (fileCreated) {
							i.remove();
							writer.close();
							break;
						}
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			}

			return output.toArray(new Path[output.size()]);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
