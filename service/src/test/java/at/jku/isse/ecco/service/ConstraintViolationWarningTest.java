package at.jku.isse.ecco.service;

import at.jku.isse.ecco.mining.ConstraintMiner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: {@link EccoService#checkConstraintViolations} warns (without blocking anything --
 * see {@code at.jku.isse.ecco.core.Constraint}'s "purely advisory" contract) when a configuration
 * violates an accepted, currently-still-hard constraint, and stays quiet otherwise.
 */
public class ConstraintViolationWarningTest {

    @Test
    @Timeout(30)
    public void configurationViolatingAcceptedExcludes_isReported() throws IOException {
        Path workDir = Files.createTempDirectory("constraint-violation-warning");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            // Core and Extra never co-occur (4 witnesses each) -- EXCLUDES(Core,Extra) mines hard.
            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("core-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("core.txt"), "core " + i + "\n");
                service.setBaseDir(p);
                service.commit("core " + i, "Core");
            }
            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("extra-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("extra.txt"), "extra " + i + "\n");
                service.setBaseDir(p);
                service.commit("extra " + i, "Extra");
            }

            List<ConstraintMiner.Suggestion> mined = service.acceptedSuggestions(service.getRepository());
            // nothing accepted yet -- mined-but-unaccepted suggestions must not be trusted.
            assertTrue(mined.isEmpty(), "sanity check: nothing accepted yet");

            List<ConstraintMiner.Suggestion> hypotheses =
                    new ConstraintMiner(4, 0.9, null).mine(
                            at.jku.isse.ecco.mining.ConfigurationBridge.readConfigurations(service));
            ConstraintMiner.Suggestion excludesCoreExtra = hypotheses.stream()
                    .filter(s -> s.kind == ConstraintMiner.Kind.EXCLUDES
                            && ((s.a.equals("Core") && "Extra".equals(s.b)) || (s.a.equals("Extra") && "Core".equals(s.b))))
                    .findFirst().orElseThrow();
            service.acceptConstraint(excludesCoreExtra);

            Path bothDir = workDir.resolve("both");
            Files.createDirectories(bothDir);
            Files.writeString(bothDir.resolve("core.txt"), "core both\n");
            Files.writeString(bothDir.resolve("extra.txt"), "extra both\n");
            service.setBaseDir(bothDir);

            List<String> violations = service.checkConstraintViolations(service.parseConfigurationString("Core, Extra"));
            assertTrue(violations.stream().anyMatch(v -> v.contains("EXCLUDES")),
                    "committing Core and Extra together should violate the accepted EXCLUDES(Core,Extra) constraint");

            List<String> noViolations = service.checkConstraintViolations(service.parseConfigurationString("Core"));
            assertTrue(noViolations.isEmpty(), "a configuration respecting the accepted constraint should report nothing");
        }
    }

    @Test
    @Timeout(30)
    public void disablingFlag_suppressesWarnings() throws IOException {
        Path workDir = Files.createTempDirectory("constraint-violation-warning-disabled");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("core-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("core.txt"), "core " + i + "\n");
                service.setBaseDir(p);
                service.commit("core " + i, "Core");
            }
            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("extra-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("extra.txt"), "extra " + i + "\n");
                service.setBaseDir(p);
                service.commit("extra " + i, "Extra");
            }

            List<ConstraintMiner.Suggestion> hypotheses =
                    new ConstraintMiner(4, 0.9, null).mine(
                            at.jku.isse.ecco.mining.ConfigurationBridge.readConfigurations(service));
            ConstraintMiner.Suggestion excludesCoreExtra = hypotheses.stream()
                    .filter(s -> s.kind == ConstraintMiner.Kind.EXCLUDES
                            && ((s.a.equals("Core") && "Extra".equals(s.b)) || (s.a.equals("Extra") && "Core".equals(s.b))))
                    .findFirst().orElseThrow();
            service.acceptConstraint(excludesCoreExtra);

            service.setConstraintViolationWarningsEnabled(false);
            List<String> violations = service.checkConstraintViolations(service.parseConfigurationString("Core, Extra"));
            assertTrue(violations.isEmpty(), "disabling the flag must suppress constraint-violation warnings");
        }
    }
}
