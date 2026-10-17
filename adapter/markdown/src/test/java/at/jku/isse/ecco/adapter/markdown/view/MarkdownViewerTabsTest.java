package at.jku.isse.ecco.adapter.markdown.view;

import at.jku.isse.ecco.adapter.markdown.MarkdownReader;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Several Markdown files are shown a tab each; only the file shown is built into rows, the others
 * when their tab is first selected - every file of the tree was, on every click.
 */
public class MarkdownViewerTabsTest {

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

	/** [tab]'s rows: none, or no content at all, until it is first shown. */
	private static int rows(Tab tab) {
		return tab.getContent() instanceof ListView<?> list ? list.getItems().size() : 0;
	}

	@Test
	public void onlyTheFileShownIsBuiltTheOthersWhenSelected(@TempDir Path dir) throws Exception {
		for (String name : List.of("a", "b", "c"))
			Files.writeString(dir.resolve(name + ".md"), "# " + name + "\n\ntext of " + name + "\n");
		SerEntityFactory factory = new SerEntityFactory();
		Node.Op root = factory.createRootNode();
		for (Node.Op file : new MarkdownReader(factory).read(dir, new Path[]{Path.of("a.md"), Path.of("b.md"), Path.of("c.md")}))
			root.addChild(file);
		Node inB = root.getChildren().stream().filter(f -> f.getArtifact().getData().toString().contains("b.md")).findFirst().orElseThrow().getChildren().get(0);

		MarkdownViewer viewer = onFx(() -> {
			MarkdownViewer v = new MarkdownViewer();
			v.showTree(inB);
			return v;
		});
		TabPane tabs = (TabPane) viewer.getCenter();
		Tab a = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("a.md")).findFirst().orElseThrow());
		Tab b = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("b.md")).findFirst().orElseThrow());
		Tab c = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("c.md")).findFirst().orElseThrow());
		assertSame(b, onFx(() -> tabs.getSelectionModel().getSelectedItem()));
		assertTrue(onFx(() -> rows(b)) > 0);
		assertEquals(0, onFx(() -> rows(a)), "a.md not built yet");
		assertEquals(0, onFx(() -> rows(c)), "c.md not built yet");

		onFx(() -> {
			tabs.getSelectionModel().select(c);
			return null;
		});
		assertTrue(onFx(() -> rows(c)) > 0, "built when selected");
		assertEquals(0, onFx(() -> rows(a)), "a.md still not");
	}
}
