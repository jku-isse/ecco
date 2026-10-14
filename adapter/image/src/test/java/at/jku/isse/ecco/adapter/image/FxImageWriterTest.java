package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JavaFX side of the image adapter (FxImageWriter, ImageViewer), headless: built on the FX
 * thread without a window, never pressing the Save button (it opens a modal file chooser). Same
 * approach as the gui module's FxTestSupport.
 */
public class FxImageWriterTest {

    private final SerEntityFactory factory = new SerEntityFactory();

    @BeforeAll
    public static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
        // on macOS AWT must not be started by the test thread once JavaFX runs: start it on the FX
        // thread, as the GUI does, before any later test (ImageIO, Graphics2D) touches it
        onFx(() -> GraphicsEnvironment.getLocalGraphicsEnvironment());
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "the FX thread did not get to the action within 10 s");
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new Exception(failure.get());
        return result.get();
    }

    private Node.Op pluginWithTypeChildFirst(int width, int height) {
        Node.Op plugin = factory.createNode(new PluginArtifactData(ImagePlugin.class.getName(), Path.of("a.png")));
        plugin.addChild(factory.createNode(new ImageArtifactData(new int[]{5}, "TYPE")));
        Node.Op image = factory.createNode(new ImageArtifactData(new int[]{width, height}, ImageReader.TYPE_IMAGE));
        plugin.addChild(image);
        return plugin;
    }

    private Node.Op position(Node.Op plugin, int x, int y, int[]... colors) {
        Node.Op image = plugin.getChildren().stream()
                .filter(n -> ImageReader.TYPE_IMAGE.equals(((ImageArtifactData) n.getArtifact().getData()).getType()))
                .findFirst().orElseThrow();
        Node.Op pos = factory.createNode(new ImageArtifactData(new int[]{x, y}, ImageReader.TYPE_POS));
        for (int[] color : colors)
            pos.addChild(factory.createNode(new ImageArtifactData(color, ImageReader.TYPE_COLOR)));
        image.addChild(pos);
        return pos;
    }

    @Test
    public void fxWriterRendersThePixelsLikeTheAwtWriter() throws Exception {
        Node.Op plugin = pluginWithTypeChildFirst(3, 1);
        position(plugin, 0, 0, new int[]{255, 1, 2, 3});
        position(plugin, 1, 0, new int[]{255, 200, 0, 100}, new int[]{255, 0, 100, 51});
        position(plugin, 2, 0);
        Image image = onFx(() -> new FxImageWriter().write(Set.of(plugin))[0]);
        assertEquals(3, (int) image.getWidth());
        assertEquals(1, (int) image.getHeight());
        assertEquals(0xff010203, image.getPixelReader().getArgb(0, 0));
        assertEquals(0xff64324b, image.getPixelReader().getArgb(1, 0), "the average of two colors");
        // no color: the background, transparent white - a WritableImage stores premultiplied pixels,
        // so a fully transparent one reads back as 0
        assertEquals(0, image.getPixelReader().getArgb(2, 0) >>> 24, "no color: the transparent background");
    }

    @Test
    public void fxWriterLeavesAColorEqualToTheBackgroundOutOfTheAverage() throws Exception {
        Node.Op plugin = pluginWithTypeChildFirst(2, 1);
        position(plugin, 0, 0, new int[]{0, 255, 255, 255}, new int[]{255, 10, 20, 30});
        position(plugin, 1, 0, new int[]{0, 255, 255, 255});
        Image image = onFx(() -> new FxImageWriter().write(null, Set.of(plugin))[0]);
        assertEquals(0xff0a141e, image.getPixelReader().getArgb(0, 0));
        assertEquals(0, image.getPixelReader().getArgb(1, 0));
    }

    /**
     * FxImageWriter looks the image node up by its type; ImageUtil (file and AWT writers) takes the
     * plugin node's first child, so a tree whose first child is not the image node fails there.
     */
    @Test
    public void onlyTheFxWriterFindsAnImageNodeThatIsNotTheFirstChildCurrentBehaviour() throws Exception {
        Node.Op plugin = pluginWithTypeChildFirst(1, 1);
        position(plugin, 0, 0, new int[]{255, 1, 2, 3});
        assertEquals(0xff010203, onFx(() -> new FxImageWriter().write(Set.of(plugin))[0]).getPixelReader().getArgb(0, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> new AwtImageWriter().write(Set.of(plugin)));
    }

    @Test
    public void fxWriterRefusesAPluginNodeWithoutAnImageNode() {
        Node.Op plugin = factory.createNode(new PluginArtifactData(ImagePlugin.class.getName(), Path.of("a.png")));
        Exception e = assertThrows(Exception.class, () -> onFx(() -> new FxImageWriter().write(Set.of(plugin))));
        assertInstanceOf(EccoException.class, e.getCause());
    }

    @Test
    public void fxWriterRefusesANonPluginTopNode() {
        Node.Op notAPlugin = factory.createNode(new ImageArtifactData(new int[]{1, 1}, ImageReader.TYPE_IMAGE));
        assertThrows(EccoException.class, () -> new FxImageWriter().write(Set.of(notAPlugin)));
    }

    @Test
    public void fxWriterListenersAndPluginId() {
        FxImageWriter writer = new FxImageWriter();
        WriteListener listener = new WriteListener() {
        };
        writer.addListener(listener);
        writer.removeListener(listener);
        assertEquals(ImagePlugin.class.getName(), writer.getPluginId());
    }

    // --- ImageViewer ---

    @Test
    public void viewerShowsTheWholeImageForAPluginNode() throws Exception {
        Node.Op plugin = pluginWithTypeChildFirst(2, 2);
        position(plugin, 1, 1, new int[]{255, 9, 8, 7});
        ImageViewer viewer = onFx(() -> {
            ImageViewer v = new ImageViewer();
            v.showTree(plugin);
            return v;
        });
        ImageView view = assertInstanceOf(ImageView.class, viewer.getCenter());
        assertEquals(0xff090807, view.getImage().getPixelReader().getArgb(1, 1));
        assertEquals("Save", assertInstanceOf(Button.class, viewer.getTop()).getText());
        assertEquals(ImagePlugin.class.getName(), viewer.getPluginId());
    }

    @Test
    public void viewerShowsAColorNodeAsItsColor() throws Exception {
        Node.Op color = factory.createNode(new ImageArtifactData(new int[]{255, 255, 0, 51}, ImageReader.TYPE_COLOR));
        ImageViewer viewer = onFx(() -> {
            ImageViewer v = new ImageViewer();
            v.setAssociationInfos(null);
            v.showTree(color);
            return v;
        });
        assertNull(viewer.getCenter());
        BackgroundFill fill = viewer.getBackground().getFills().get(0);
        assertEquals(new Color(1.0, 0.0, 0.2, 1.0), fill.getFill());
    }

    @Test
    public void viewerIgnoresAPositionNode() throws Exception {
        Node.Op pos = factory.createNode(new ImageArtifactData(new int[]{0, 0}, ImageReader.TYPE_POS));
        ImageViewer viewer = onFx(() -> {
            ImageViewer v = new ImageViewer();
            v.showTree(pos);
            return v;
        });
        assertNull(viewer.getCenter());
        assertNull(viewer.getBackground());
    }

    @Test
    public void viewerIgnoresOtherArtifacts() throws Exception {
        Node.Op other = factory.createNode(new at.jku.isse.ecco.adapter.dispatch.FinalNewlineArtifactData());
        ImageViewer viewer = onFx(() -> {
            ImageViewer v = new ImageViewer();
            v.showTree(other);
            return v;
        });
        assertNull(viewer.getCenter());
    }
}
