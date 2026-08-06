package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.layout.BorderPane;

public class JavaViewer extends BorderPane implements ArtifactViewer {

    @Override
    public String getPluginId() {
        return JavaPlugin.class.getName();
    }

    @Override
    public void showTree(Node node) {

    }

}
