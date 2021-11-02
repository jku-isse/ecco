package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilypondCompiler;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.LilypondStringWriter;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import java.util.HashSet;
import java.util.Set;

public class ImageViewer extends BorderPane implements ArtifactViewer {

	private final LilypondStringWriter textWriter = new LilypondStringWriter();

	@Override
	public void showTree(Node node) {
		Set<Node> nodes = new HashSet<>();
		nodes.add(node);

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
            setCursor(Cursor.WAIT);
            Thread th = new Thread(() -> {
                LilypondCompiler lilyC = new LilypondCompiler(this.textWriter.write(nodes)[0]);

                Image image = lilyC.compilePNG();

                if (null != image) {
                    ImageView imageView = new ImageView();
                    imageView.setPreserveRatio(true);
                    imageView.setImage(image);

                    ScrollPane sp = new ScrollPane();
                    sp.setContent(imageView);
                    Platform.runLater(() -> {
                        this.setCenter(sp);
                        this.setBackground(Background.EMPTY);
                    });

                } else if (null != lilyC.getLastError()) {
                    TextArea ta = new TextArea();
                    ta.setText(lilyC.getLastError());
                    Platform.runLater(() -> {
                        this.setCenter(ta);
                        this.setBackground(Background.EMPTY);
                    });
                }
                Platform.runLater(() -> setCursor(Cursor.DEFAULT));
            });
            th.start();
		}
	}

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}
}