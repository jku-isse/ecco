package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link SurplusLatticeAbsorber} through the real {@link EccoService} commit/checkout
 * pipeline -- the shape of bug this fixes only exists in real diff-produced conditions (see
 * {@code CONSTRAINT_MINING_DESIGN.md}'s "Surplus-module suppression" section), not hand-built
 * fixtures, so a real pipeline is the faithful way to test it. This exact repo shape
 * (Common/Old/New/Extra) reliably produced 6 real surplus entries for a novel "Common, Old, Extra"
 * checkout during the investigation -- all 6 pure lattice-bloat noise, all eliminated by absorption.
 */
public class SurplusLatticeAbsorberTest {

    private record Counts(int surplus, int missing) {
    }

    private Counts buildToyRepoAndCheckout(boolean absorptionEnabled) throws IOException {
        Path workDir = Files.createTempDirectory("surplus-lattice-absorber-test-" + absorptionEnabled);
        Path repoDir = workDir.resolve(".ecco");

        Path commonDir = workDir.resolve("common");
        Files.createDirectories(commonDir);
        Files.writeString(commonDir.resolve("common.txt"), "base\n");

        Path oldDir = workDir.resolve("old");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("common.txt"), "old-variant\n");
        Files.writeString(oldDir.resolve("old.txt"), "old only\n");

        Path newDir = workDir.resolve("new");
        Files.createDirectories(newDir);
        Files.writeString(newDir.resolve("common.txt"), "new-variant\n");
        Files.writeString(newDir.resolve("new.txt"), "new only\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            service.setBaseDir(commonDir);
            service.commit("base", "Common");

            service.setBaseDir(oldDir);
            service.commit("old", "Common, Old");
            for (int i = 1; i <= 3; i++) {
                Path p = workDir.resolve("old-pad-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("common.txt"), "old-variant\n");
                Files.writeString(p.resolve("old.txt"), "old only\n");
                Files.writeString(p.resolve("old-extra-" + i + ".txt"), "old pad " + i + "\n");
                service.setBaseDir(p);
                service.commit("old pad " + i, "Common, Old");
            }

            service.setBaseDir(newDir);
            service.commit("new", "Common, New");
            for (int i = 1; i <= 3; i++) {
                Path p = workDir.resolve("new-pad-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("common.txt"), "new-variant\n");
                Files.writeString(p.resolve("new.txt"), "new only\n");
                Files.writeString(p.resolve("new-extra-" + i + ".txt"), "new pad " + i + "\n");
                service.setBaseDir(p);
                service.commit("new pad " + i, "Common, New");
            }

            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("extra-plain-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("common.txt"), "base\n");
                Files.writeString(p.resolve("extra.txt"), "extra plain\n");
                Files.writeString(p.resolve("extra-pad-" + i + ".txt"), "extra plain pad " + i + "\n");
                service.setBaseDir(p);
                service.commit("extra plain " + i, "Common, Extra");
            }
            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("extra-with-new-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("common.txt"), "new-variant\n");
                Files.writeString(p.resolve("new.txt"), "new only\n");
                Files.writeString(p.resolve("extra.txt"), "extra with new\n");
                Files.writeString(p.resolve("extra-new-pad-" + i + ".txt"), "extra new pad " + i + "\n");
                service.setBaseDir(p);
                service.commit("extra with new " + i, "Common, New, Extra");
            }

            service.setSurplusAbsorptionEnabled(absorptionEnabled);
            service.setSurplusSuppressionEnabled(false); // isolate absorption from the separate suppressor
            Path checkoutDir = workDir.resolve("checkout");
            Files.createDirectory(checkoutDir);
            service.setBaseDir(checkoutDir);
            Checkout checkout = service.checkout("Common, Old, Extra");

            return new Counts(checkout.getSurplusModules().size(), checkout.getMissing().size());
        }
    }

    @Test
    @Timeout(60)
    public void absorptionEnabled_eliminatesAllLatticeBloatSurplus() throws IOException {
        Counts counts = buildToyRepoAndCheckout(true);
        assertEquals(0, counts.surplus(),
                "every real surplus entry in this scenario is pure lattice-bloat noise and must be eliminated");
    }

    @Test
    @Timeout(60)
    public void absorptionDisabled_leavesTheKnownBaselineSurplusUntouched() throws IOException {
        Counts counts = buildToyRepoAndCheckout(false);
        assertEquals(6, counts.surplus(),
                "with absorption off, the known baseline surplus count for this scenario must be unchanged "
                        + "-- confirms the flag actually gates behavior, not that absorption silently always ran");
    }

    @Test
    @Timeout(60)
    public void missingIsIdenticalRegardlessOfAbsorption() throws IOException {
        Counts countsOn = buildToyRepoAndCheckout(true);
        Counts countsOff = buildToyRepoAndCheckout(false);
        assertEquals(countsOff.missing(), countsOn.missing(), "absorption must never touch getMissing()");
    }
}
