package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.parser.LilypondToken;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Several LilyPond files are shown a tab each; only the file shown is built into lines (in the
 * background), the others when their tab is first selected - every file of the tree was, each in a
 * thread of its own, on every click.
 */
public class LilypondCodeViewerTabsTest {

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
		if (failure.get() != null)
			throw new Exception(failure.get());
		return result.get();
	}

	private static void waitUntil(String what, Callable<Boolean> condition) throws Exception {
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			if (onFx(condition))
				return;
			Thread.sleep(20);
		}
		fail("timed out waiting for " + what);
	}

	/** A LilyPond file node with one token, as the reader builds it - without the parser. */
	private Node.Op file(Node.Op root, String name, String token) {
		Node.Op file = factory.createOrderedNode(factory.createArtifact(new PluginArtifactData(LilypondPlugin.class.getName(), Path.of(name))));
		LilypondNode<LilypondToken> context = new LilypondNode<>("ctx", null);
		context.setLevel(0);
		context.append("tok", new LilypondToken(0, token, "Token"), 1);
		new LilypondReader(factory).generateEccoTree(context, file);
		root.addChild(file);
		return file;
	}

	private static int lines(Tab tab) {
		return ((ListView<?>) tab.getContent()).getItems().size();
	}

	@Test
	public void onlyTheFileShownIsBuiltTheOthersWhenSelected() throws Exception {
		Node.Op root = factory.createRootNode();
		file(root, "a.ly", "a'4");
		Node.Op b = file(root, "b.ly", "b'4");
		file(root, "c.ly", "c'4");

		LilypondCodeViewer viewer = onFx(() -> {
			LilypondCodeViewer v = new LilypondCodeViewer();
			v.showTree(b.getChildren().get(0));
			return v;
		});
		TabPane tabs = onFx(() -> (TabPane) ((SplitPane) viewer.getCenter()).getItems().get(0));
		Tab tabA = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("a.ly")).findFirst().orElseThrow());
		Tab tabB = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("b.ly")).findFirst().orElseThrow());
		Tab tabC = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("c.ly")).findFirst().orElseThrow());
		assertSame(tabB, onFx(() -> tabs.getSelectionModel().getSelectedItem()));
		waitUntil("b.ly built", () -> lines(tabB) > 0);
		Thread.sleep(300); // the others would have been built in the background meanwhile
		assertEquals(0, onFx(() -> lines(tabA)), "a.ly not built");
		assertEquals(0, onFx(() -> lines(tabC)), "c.ly not built");

		onFx(() -> {
			tabs.getSelectionModel().select(tabC);
			return null;
		});
		waitUntil("c.ly built when selected", () -> lines(tabC) > 0);
		assertEquals(0, onFx(() -> lines(tabA)), "a.ly still not");
	}
}
