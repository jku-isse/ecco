package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Apply Fix on a MISSING warning checks the configuration against the accepted constraints before it
 * commits - in the background, holding the service's lock.
 */
public class CheckoutDetailViewApplyFixTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /**
     * While a commit holds the service's lock, the check does not freeze the window: it waits in the
     * background, and asks about the violation once the lock is free - declined, nothing is committed.
     * It parsed and checked the configuration on the FX thread, which waited for the lock.
     */
    @Test
    @Timeout(60)
    public void theConstraintsAreCheckedOffTheFxThread(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            for (int i = 0; i < 4; i++)
                commit(service, tmp.resolve("seed" + i), "BASE, A", Map.of("seed.txt", "seed " + i + "\n"));
            service.acceptConstraint(Constraint.Kind.MANDATORY, "A", null);
            Path fix = Files.createDirectories(tmp.resolve("fix"));
            Files.writeString(fix.resolve("fix.txt"), "fix\n");
            List<List<String>> askedAbout = new CopyOnWriteArrayList<>();
            CheckoutDetailView view = onFx(() -> new CheckoutDetailView(service) {
                @Override
                boolean confirmDespiteViolations(List<String> violations) {
                    askedAbout.add(violations);
                    return false;
                }
            });

            synchronized (service) { // as a commit running
                // onFx fails after 10 s if the FX thread waits for the lock
                onFx(() -> view.commitFix(fix, "BASE", "the fix"));
                assertTrue(askedAbout.isEmpty(), "not checked while the lock is held");
            }
            waitUntil("the violation asked about", () -> !askedAbout.isEmpty());
            assertFalse(askedAbout.get(0).isEmpty());
            Thread.sleep(300);
            assertEquals(4, service.getCommits().size(), "declined: nothing committed");
        }
    }
}
