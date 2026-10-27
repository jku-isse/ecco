package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Histories of one text file, a.txt, for the sync tests: committed, forked, pulled and pushed, and
 * a repository's state as far as sync must keep it - its commits, its modules' counts and its
 * associations' conditions with the lines they hold.
 */
final class SyncHistory {

    /** a commit: its configuration and a.txt's lines, "|" between them */
    record Step(String configuration, String lines) {

        String text() {
            return this.lines.replace("|", "\n") + "\n";
        }
    }

    private static final String[] FEATURES = {"A", "B", "C", "D"};

    private SyncHistory() {
    }

    /**
     * Random steps for the search tests: on the features known so far (all of A-D when [own] is null,
     * which also makes them known) and [own], a feature of the side's own; a line changes now and then
     * without a new revision, as in real histories.
     */
    static List<Step> randomSteps(Random random, Map<String, Integer> version, Set<String> known, String own, int length) {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            List<String> pool = new ArrayList<>(own == null ? List.of(FEATURES) : known);
            if (own != null)
                pool.add(own);
            List<String> configuration = new ArrayList<>();
            for (String feature : pool)
                if (random.nextInt(2) == 0)
                    configuration.add(feature);
            if (configuration.isEmpty())
                configuration.add(pool.get(0));
            if (own == null)
                known.addAll(configuration);
            List<String> lines = new ArrayList<>();
            for (String feature : configuration) {
                if (random.nextInt(4) == 0)
                    version.merge(feature, 1, Integer::sum);
                lines.add(feature.toLowerCase() + version.getOrDefault(feature, 0));
            }
            steps.add(new Step(String.join(", ", configuration), String.join("|", lines)));
        }
        return steps;
    }

    static List<Step> steps(String... steps) {
        List<Step> result = new ArrayList<>();
        for (String step : steps) {
            String[] parts = step.split("=", 2);
            result.add(new Step(parts[0], parts[1]));
        }
        return result;
    }

    @SafeVarargs
    static List<Step> concat(List<Step>... parts) {
        List<Step> result = new ArrayList<>();
        for (List<Step> part : parts)
            result.addAll(part);
        return result;
    }

    /** a new repository in dir with the steps committed */
    static Path commit(Path dir, List<Step> steps) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.init();
        }
        commitInto(dir, steps);
        return dir;
    }

    static void commitInto(Path repository, List<Step> steps) throws Exception {
        Path work = Files.createDirectories(repository.resolveSibling(repository.getFileName() + "-work"));
        try (EccoService service = open(repository)) {
            service.setBaseDir(work);
            for (Step step : steps) {
                Files.writeString(work.resolve("a.txt"), step.text());
                service.commit(step.configuration(), step.configuration());
            }
        }
    }

    static Path fork(Path origin, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.fork(origin.resolve(".ecco"));
        }
        return dir;
    }

    /** a fork over a loopback socket, from a server serving origin */
    static Path forkOverTheNetwork(Path origin, Path dir) throws Exception {
        Files.createDirectories(dir);
        withServer(origin, port -> {
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(dir.resolve(".ecco"));
                service.fork("localhost", port, "");
            }
        });
        return dir;
    }

    interface WithPort {
        void run(int port) throws Exception;
    }

    /** serves the repository while running the body, then stops and closes it */
    static void withServer(Path repository, WithPort body) throws Exception {
        int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        EccoService server = open(repository);
        Thread thread = new Thread(() -> server.startServer(port), "test-ecco-server");
        thread.start();
        try {
            // serverRunning() is set before the port is bound: wait until it takes a connection
            long deadline = System.currentTimeMillis() + 10_000;
            while (true) {
                try (java.net.Socket probe = new java.net.Socket("localhost", port)) {
                    break;
                } catch (java.io.IOException e) {
                    if (System.currentTimeMillis() > deadline)
                        throw e;
                    Thread.sleep(20);
                }
            }
            body.run(port);
        } finally {
            if (server.serverRunning())
                server.stopServer();
            thread.join(10_000);
            server.close();
        }
    }

    static void pull(Path repository, Path from) {
        try (EccoService service = open(repository)) {
            if (service.getRemotes().stream().noneMatch(remote -> remote.getName().equals("from")))
                service.addRemote("from", from.toString(), Remote.Type.LOCAL);
            service.pull("from");
        }
    }

    static void push(Path repository, Path to) {
        try (EccoService service = open(repository)) {
            if (service.getRemotes().stream().noneMatch(remote -> remote.getName().equals("to")))
                service.addRemote("to", to.toString(), Remote.Type.LOCAL);
            service.push("to");
        }
    }

    /** a.txt as checked out for the configuration */
    static String checkout(Path repository, String configuration, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = open(repository)) {
            service.setBaseDir(dir);
            service.checkout(configuration);
        }
        Path file = dir.resolve("a.txt");
        return Files.exists(file) ? Files.readString(file) : "(no a.txt)";
    }

    /** each configuration committed -> a.txt as checked out for it */
    static Map<String, String> checkouts(Path repository, List<Step> steps, Path dir) throws Exception {
        Map<String, String> result = new TreeMap<>();
        int i = 0;
        for (Step step : steps)
            if (!result.containsKey(step.configuration()))
                result.put(step.configuration(), checkout(repository, step.configuration(), dir.resolve("" + i++)));
        return result;
    }

    /** the commits in order: id, configuration, message and the lines of their associations */
    static List<String> commits(Path repository) {
        List<String> result = new ArrayList<>();
        try (EccoService service = open(repository)) {
            List<Commit> commits = new ArrayList<>(service.getCommits());
            commits.sort(java.util.Comparator.comparing(Commit::getDate).thenComparing(Commit::getId));
            for (Commit commit : commits) {
                Set<String> lines = new TreeSet<>();
                for (Association association : commit.getAssociations())
                    lines.addAll(lines((Association.Op) association));
                result.add(commit.getId() + " [" + commit.getConfiguration() + "] " + commit.getCommitMessage() + " " + lines);
            }
        }
        return result;
    }

    /** each module revision -> its count */
    static Map<String, Integer> moduleCounts(Path repository) {
        Map<String, Integer> result = new TreeMap<>();
        try (EccoService service = open(repository)) {
            Repository.Op repo = (Repository.Op) service.getRepository();
            for (int order = 0; order <= repo.getMaxOrder(); order++)
                for (Module module : repo.getModules(order))
                    for (ModuleRevision revision : module.getRevisions())
                        result.put(revision.toString(), revision.getCount());
        }
        return result;
    }

    /** each association's condition with the lines it holds */
    static Set<String> conditions(Path repository) {
        Set<String> result = new TreeSet<>();
        try (EccoService service = open(repository)) {
            for (Association.Op association : ((Repository.Op) service.getRepository()).getAssociations())
                result.add(association.computeCondition() + " " + lines(association));
        }
        return result;
    }

    /** the leaves its unique nodes are - the lines, the final newline and the file's format */
    private static List<String> lines(Association.Op association) {
        List<String> lines = new ArrayList<>();
        association.getRootNode().traverse((Node.Op node) -> {
            if (node.isUnique() && node.getArtifact() != null && node.getChildren().isEmpty())
                lines.add(String.valueOf(node.getArtifact()));
        });
        Collections.sort(lines);
        return lines;
    }

    static EccoService open(Path repository) {
        EccoService service = new EccoService();
        service.setRepositoryDir(repository.resolve(".ecco"));
        service.open();
        return service;
    }
}
