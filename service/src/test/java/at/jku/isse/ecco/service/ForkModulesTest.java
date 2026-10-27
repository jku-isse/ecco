package at.jku.isse.ecco.service;

import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fork has its origin's modules and module revisions - counted once, as subset() counts them - and a
 * checkout of it warns as one of its origin does. subset() took only the modules of the associations'
 * conditions, and a checkout of the fork warned "MISSING: documentation (without model)" for a
 * combination committed in the origin.
 */
public class ForkModulesTest {

    private record Step(String configuration, String text) {
    }

    private static final List<Step> HISTORY = List.of(
            new Step("documentation", "doc\n"),
            new Step("documentation, model", "doc\nmodel\n"),
            new Step("documentation, model, search", "doc2\nmodel\nsearch\n"));

    @Test
    @Timeout(120)
    public void aForkHasTheModulesOfItsOriginAndWarnsAsItDoes(@TempDir Path tmp) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(origin.resolve(".ecco"));
            service.init();
            service.setBaseDir(origin);
            for (Step step : HISTORY) {
                Files.writeString(origin.resolve("a.txt"), step.text());
                service.commit("", step.configuration());
            }
        }
        Path fork = Files.createDirectories(tmp.resolve("fork"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.fork(origin.resolve(".ecco"), "");
        }

        assertEquals(modules(origin).keySet(), modules(fork).keySet());
        int i = 0;
        for (Step step : HISTORY) {
            assertEquals(warnings(origin, step.configuration(), tmp.resolve("co-origin-" + i)), warnings(fork, step.configuration(), tmp.resolve("co-fork-" + i)), step.configuration());
            i++;
        }
    }

    @Test
    @Timeout(120)
    public void aCommitAfterForkingGivesTheConditionsOfTheSameCommitInTheOrigin(@TempDir Path tmp) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(origin.resolve(".ecco"));
            service.init();
            service.setBaseDir(origin);
            for (Step step : HISTORY) {
                Files.writeString(origin.resolve("a.txt"), step.text());
                service.commit("", step.configuration());
            }
        }
        Path fork = Files.createDirectories(tmp.resolve("fork"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.fork(origin.resolve(".ecco"), "");
        }

        // the same commit in both: documentation alone, with a line of its own
        for (Path repository : List.of(origin, fork)) {
            Path dir = Files.createDirectories(tmp.resolve("next-" + repository.getFileName()));
            Files.writeString(dir.resolve("a.txt"), "doc\nmore doc\n");
            try (EccoService service = open(repository)) {
                service.setBaseDir(dir);
                service.commit("", "documentation");
            }
        }

        assertEquals(conditions(origin), conditions(fork));
        int i = 0;
        for (Step step : HISTORY) {
            assertEquals(checkout(origin, step.configuration(), tmp.resolve("co-origin-" + i)), checkout(fork, step.configuration(), tmp.resolve("co-fork-" + i)), step.configuration());
            i++;
        }
    }

    /** the associations' conditions, each with the lines its association holds */
    private static java.util.Set<String> conditions(Path repository) {
        java.util.Set<String> result = new java.util.TreeSet<>();
        try (EccoService service = open(repository)) {
            for (var association : ((Repository.Op) service.getRepository()).getAssociations()) {
                java.util.List<String> lines = new java.util.ArrayList<>();
                association.getRootNode().traverse((at.jku.isse.ecco.tree.Node.Op node) -> {
                    if (node.isUnique() && node.getArtifact() != null && node.getChildren().isEmpty())
                        lines.add(String.valueOf(node.getArtifact()));
                });
                java.util.Collections.sort(lines);
                result.add(association.computeCondition().toString() + " " + lines);
            }
        }
        return result;
    }

    private static String checkout(Path repository, String configuration, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = open(repository)) {
            service.setBaseDir(dir);
            service.checkout(configuration);
        }
        return Files.readString(dir.resolve("a.txt"));
    }

    /** each module and module revision -> its count */
    private static Map<String, Integer> modules(Path repository) {
        Map<String, Integer> result = new TreeMap<>();
        try (EccoService service = open(repository)) {
            Repository.Op repo = (Repository.Op) service.getRepository();
            for (int order = 0; order <= repo.getMaxOrder(); order++)
                for (Module module : repo.getModules(order)) {
                    result.put("module " + module, module.getCount());
                    for (ModuleRevision revision : module.getRevisions())
                        result.put("revision " + revision, revision.getCount());
                }
        }
        return result;
    }

    private static String warnings(Path repository, String configuration, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = open(repository)) {
            service.setBaseDir(dir);
            service.checkout(configuration);
        }
        Path warnings = dir.resolve(EccoService.WARNINGS_FILE_NAME);
        return Files.exists(warnings) ? Files.readString(warnings).replaceAll("association [0-9a-f-]+", "association ...") : "";
    }

    private static EccoService open(Path repository) {
        EccoService service = new EccoService();
        service.setRepositoryDir(repository.resolve(".ecco"));
        service.open();
        return service;
    }
}
