package at.jku.isse.ecco.service;

import at.jku.isse.ecco.service.listener.EccoListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Listeners hear of a commit after its transaction is stored. A listener that throws then must not
 * roll the commit back (FR-M10c) - the events are fired from inside the commit's try block, whose
 * catch would otherwise undo it - nor keep the other listeners from hearing of it (NFR-C2).
 */
public class ListenerFailureKeepsCommitTest {

    @Test
    @Timeout(60)
    public void aThrowingListenerNeitherUndoesTheCommitNorSilencesTheOthers(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        AtomicInteger heard = new AtomicInteger();

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(work);
            service.addListener(new EccoListener() {
                @Override
                public void statusChangedEvent(EccoService s) {
                    throw new IllegalStateException("listener failure");
                }
            });
            service.addListener(new EccoListener() {
                @Override
                public void statusChangedEvent(EccoService s) {
                    heard.incrementAndGet();
                }
            });

            Files.writeString(work.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            assertTrue(heard.get() > 0, "the other listener heard of the commit");
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.open();
            assertEquals(1, service.getCommits().size());
            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            assertEquals("a\n", Files.readString(out.resolve("a.txt")));
        }
    }
}
