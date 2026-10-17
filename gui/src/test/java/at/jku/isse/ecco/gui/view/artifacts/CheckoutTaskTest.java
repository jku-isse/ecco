package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import javafx.concurrent.Worker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.artifacts.ArtifactTreeTableViewTest.association;
import static at.jku.isse.ecco.gui.view.artifacts.ArtifactTreeTableViewTest.twoFeatures;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Artifacts tab's "Checkout Selected" task writes: the composition of exactly the selected
 * associations, into the service's base directory. (Its failure path shows a modal alert and is not
 * driven here.)
 */
public class CheckoutTaskTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** Both associations composed: both files, with their committed content. */
    @Test
    @Timeout(60)
    public void writesTheComposedAssociationsIntoTheBaseDirectory(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            LazyCompositionRootNode root = new LazyCompositionRootNode();
            for (Association a : service.getRepository().getAssociations())
                root.addOrigNode(a.getRootNode());
            Path out = Files.createDirectories(tmp.resolve("out"));
            Path work = Files.createDirectories(tmp.resolve("work"));
            service.setBaseDir(work);

            CheckoutTask task = new CheckoutTask(service, root, out);
            task.run();
            waitUntil("the task succeeded", () -> task.getState() == Worker.State.SUCCEEDED);

            assertEquals("a1\na2\n", Files.readString(out.resolve("a.txt")));
            assertEquals("b1\n", Files.readString(out.resolve("b.txt")));
            // into the folder chosen: the working directory stays (it was set to the folder chosen)
            assertEquals(work, service.getBaseDir());
            assertEquals(Set.of(), files(work));
        }
    }

    /** Only a.txt's association composed: b.txt is not written. */
    @Test
    @Timeout(60)
    public void writesOnlyWhatTheSelectedAssociationsHold(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            LazyCompositionRootNode root = new LazyCompositionRootNode();
            root.addOrigNode(association(service, "a.txt").getRootNode());
            Path out = Files.createDirectories(tmp.resolve("out"));
            Path work = Files.createDirectories(tmp.resolve("work"));
            service.setBaseDir(work);

            CheckoutTask task = new CheckoutTask(service, root, out);
            task.run();
            waitUntil("the task succeeded", () -> task.getState() == Worker.State.SUCCEEDED);

            assertEquals(Set.of("a.txt"), files(out));
            assertEquals("a1\na2\n", Files.readString(out.resolve("a.txt")));
        }
    }

    /** A task cancelled before it runs writes nothing. */
    @Test
    @Timeout(60)
    public void aCancelledTaskWritesNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            LazyCompositionRootNode root = new LazyCompositionRootNode();
            root.addOrigNode(association(service, "a.txt").getRootNode());
            Path out = Files.createDirectories(tmp.resolve("out"));
            Path work = Files.createDirectories(tmp.resolve("work"));
            service.setBaseDir(work);

            CheckoutTask task = new CheckoutTask(service, root, out);
            assertTrue(onFx(() -> task.cancel()));
            task.run();
            waitUntil("the task is cancelled", () -> task.getState() == Worker.State.CANCELLED);

            assertEquals(Set.of(), files(out));
        }
    }

    private static Set<String> files(Path folder) throws java.io.IOException {
        try (Stream<Path> list = Files.list(folder)) {
            // the checkout's own .config/.warnings files aside
            return list.map(p -> p.getFileName().toString()).filter(n -> !n.startsWith(".")).collect(Collectors.toSet());
        }
    }
}
