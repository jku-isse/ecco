package at.jku.isse.ecco.adapter.python.repositoryTest;

import at.jku.isse.ecco.adapter.python.PythonPlugin;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static at.jku.isse.ecco.adapter.python.repositoryTest.PythonAdapterRepositoryTestUtil.*;
import static at.jku.isse.ecco.adapter.python.test.PythonAdapterTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PythonAdapterRepositoryTest {

    private static Path repoPath;
    private static EccoService service;
    private static Logger logger;
    private PythonAdapterRepositoryTestLogger log;

    private static void warmupJava() {
        service = new EccoService();
        checkPathInitService(PythonAdapterRepositoryTestUtil.PATH_POMMERMAN);
        String commit = PythonAdapterRepositoryTestUtil.getCommits(repoPath)[0]; // make 1st commit of Pommerman (26 files)
        service.setBaseDir(repoPath.resolve(commit));
        service.commit(commit); // not printed in console
        service.close();
    }

    @BeforeAll
    public static void setUpEccoService() {
        warmupJava();
        service = new EccoService();
        logger = Logger.getLogger(PythonPlugin.class.getName());
    }

    @BeforeEach
    public void initMeasures() {
        log = new PythonAdapterRepositoryTestLogger();
    }

    @AfterEach
    public void closeEccoService() {
        service.close();
        PythonAdapterRepositoryTestUtil.finishLogging(repoPath, log);
    }

    @Test
    @Order(1)
    public void pythonTests() {

        preparePathAndEnableLogging(PythonAdapterRepositoryTestUtil.PATH_PYTHON);

        // make commits
        String[] commits = PythonAdapterRepositoryTestUtil.getCommits(repoPath);
        makeCommits(commits);

        // extensional correctness - reproduce commits
        checkExtensionalCorrectness(commits, PythonAdapterRepositoryTestUtil.EXT_PYTHON);

        // checkout valid variants
        String[] invalidCheckouts = new String[]{
                "person.1",
                "purpleshirt.1",
                "glasses.1",
                "hat.1",
                "stripedshirt.1",
                "jacket.1"
        };

        checkoutInvalidVariants(invalidCheckouts);

        // checkout valid variants
        String[] validCheckouts = new String[]{
                "person.1, purpleshirt.1, glasses.1, hat.1",
                "person.1, purpleshirt.1, glasses.1"
        };

        checkoutValidVariants(validCheckouts);

        PythonAdapterRepositoryTestUtil.logPythonDetails(repoPath, log);
    }

    @Test
    @Order(2)
    public void jupyterTests() {

        preparePathAndEnableLogging(PythonAdapterRepositoryTestUtil.PATH_JUPYTER);

        // make commits
        String[] commits = PythonAdapterRepositoryTestUtil.getCommits(repoPath);
        makeCommits(commits);

        // extensional correctness - reproduce commits
        checkExtensionalCorrectness(commits, PythonAdapterRepositoryTestUtil.EXT_JUPYTER);

        // checkout valid variants
        String[] invalidCheckouts = new String[]{
                "train.1, resolution.1",
                "dataset.2, export.1, log.1"
        };

        checkoutInvalidVariants(invalidCheckouts);

        // checkout valid variants
        String[] validCheckouts = new String[]{
                "train.1, resolution.1, parameters.1, weights.1, dataset.1, export.1, notify.1, log.1",
                "train.1, resolution.2, parameters.2, weights.2, dataset.2, export.1",
                "train.1, resolution.2, parameters.2, weights.2, dataset.1, export.1, log.1, notify.1",
        };

        checkoutValidVariants(validCheckouts);
        PythonAdapterRepositoryTestUtil.logJuypterDetails(repoPath, log);

        checkoutAllVariants(readConfigsFromFile(repoPath.resolve("intensional_correctness_all.txt")));
    }

    @Test
    @Order(3)
    public void pommermanSlowTests() {

        preparePathAndEnableLogging(PythonAdapterRepositoryTestUtil.PATH_POMMERMAN);

        // make commits
        String[] commits = PythonAdapterRepositoryTestUtil.getCommits(repoPath);
        makeCommits(commits);

        // extensional correctness - reproduce commits
        checkExtensionalCorrectness(commits, PythonAdapterRepositoryTestUtil.EXT_PYTHON);

        // checkout valid variants
        String[] invalidCheckouts = new String[]{
                "framework.2, learning.4, dqnmodel.1, simplevslearning.1", // invalid algo-model combination
                "framework.1, heuristic.8, ppomodel.2, simplevsheuristic.1", // invalid algo-model combination
        };

        checkoutInvalidVariants(invalidCheckouts);

        // checkout valid variants
        String[] validCheckouts = new String[]{
                "framework.2, learning.3, dqnmodel.2, simplevslearning.1",
                "framework.2, learning.4, ppomodel.2, simplevslearning.1",
                "framework.2, heuristic.10, simplevsheuristic.1",
        };

        checkoutValidVariants(validCheckouts);
        PythonAdapterRepositoryTestUtil.logPythonDetails(repoPath, log);

        checkoutAllVariants(readConfigsFromFile(repoPath.resolve("intensional_correctness_all.txt")));
    }

    @Test
    @Order(4)
    public void pommermanFastTests() {

        preparePathAndEnableLogging(PythonAdapterRepositoryTestUtil.PATH_POMMERMAN_FAST);

        // make commits
        String[] commits = PythonAdapterRepositoryTestUtil.getCommits(repoPath);
        makeCommits(commits);

        // extensional correctness - reproduce commits
        checkExtensionalCorrectness(commits, PythonAdapterRepositoryTestUtil.EXT_PYTHON);
        recreateCommitsWithRedundancies(repoPath); // to check for equality with original

        // checkout valid variants
        String[] invalidCheckouts = new String[]{
                "framework.2, redundant.1, learning.4, dqnmodel.1, simplevsheuristic.1", // invalid algo-model combination
                "framework.1, redundant.1, heuristic.8, ppomodel.2, simplevslearning.1", // invalid algo-model combination
        };

        checkoutInvalidVariants(invalidCheckouts);

        // checkout valid variants
        String[] validCheckouts = new String[]{
                "framework.2, redundant.1, learning.3, dqnmodel.2, simplevslearning.1",
                "framework.2, redundant.1, learning.4, ppomodel.2, simplevslearning.1",
                "framework.2, redundant.1, heuristic.10, simplevsheuristic.1",
        };

        checkoutValidVariants(validCheckouts);
        PythonAdapterRepositoryTestUtil.logPythonDetails(repoPath, log);
    }

    /**
     * compare PATH_POMMERMAN and PATH_POMMERMAN_FAST repository
     * make sure both extensional checkouts (incl. redundant.1 for PATH_POMMERMAN_FAST) are equal
     * run only after PythonAdapterRepositoryTest has finished successfully
     */
    @AfterAll
    //@Order(99)
    public static void comparePommermanCheckoutsTest() {

        Path p1 = PATH_REPOSITORIES_ROOT.resolve(PythonAdapterRepositoryTestUtil.PATH_POMMERMAN).resolve(PythonAdapterRepositoryTestUtil.PATH_EXTENSIONAL);
        Path p2 = PATH_REPOSITORIES_ROOT.resolve(PythonAdapterRepositoryTestUtil.PATH_POMMERMAN_FAST).resolve("extensional_correctness_check_red");

        compareRepositories(p1, p2);
    }

    // Helper Methods ----------------------------------------------------------------------------------------
    private static boolean pythonPluginIsLoaded() {
        return service.getArtifactPlugins().stream().anyMatch(pl -> pl.getName().equals("PythonArtifactPlugin"));
    }

    private static void checkPathInitService(String repository) {
        repoPath = PATH_REPOSITORIES_ROOT.resolve(repository);
        Path p = repoPath.resolve(".ecco");
        deleteDir(p);
        assertFalse(Files.exists(p));
        service.setRepositoryDir(p);
        service.init();

        assertTrue(pythonPluginIsLoaded(), "Python Plugin not loaded ... skipping tests...");
    }

    private void preparePathAndEnableLogging(String repository) {
        checkPathInitService(repository);
        PythonAdapterRepositoryTestUtil.enableLoggingToFile(repoPath);
    }

    private void makeCommits(String[] commits) {
        long accumulatedTime = 0;
        log.add(0, "Config", "commitECCOTime", "accumulatedCommitECCOTime");
        for (int i = 0; i < commits.length; i++) {
            Path commitPath = repoPath.resolve(commits[i]);
            service.setBaseDir(commitPath);
            String configString = service.getConfigStringFromFile(commitPath);

            String finalCommit = commits[i];
            long timeElapsed = measureTime(() -> service.commit(finalCommit));

            accumulatedTime += timeElapsed / 1000000;
            log.add(i + 1,
                    configString,
                    String.valueOf(((float) (timeElapsed / 1000000)) / 1000f),
                    String.valueOf((float) accumulatedTime / 1000f));

            logger.info("Commit " + (i + 1) + " successful");
        }
    }

    private void checkoutValidVariants(String[] validVariants) {
        checkoutVariants(repoPath.resolve(PythonAdapterRepositoryTestUtil.PATH_INTENSIONAL_VALID), validVariants, "VV");
    }

    private void checkoutInvalidVariants(String[] invalidVariants) {
        checkoutVariants(repoPath.resolve(PythonAdapterRepositoryTestUtil.PATH_INTENSIONAL_INVALID), invalidVariants, "IV");
    }

    private void checkoutAllVariants(String[] allVariants) {
        checkoutVariants(repoPath.resolve(PythonAdapterRepositoryTestUtil.PATH_INTENSIONAL_ALL), allVariants, "ALL");
    }


    private void checkoutVariants(Path repoPath, String[] variants, String shortcut) {
        PythonAdapterRepositoryTestLogger missingSurplusLogger = new PythonAdapterRepositoryTestLogger();
        missingSurplusLogger.add("config", "missing", "surplus");
        for (int i = 1; i <= variants.length; i++) {
            String name = shortcut + i + "_" + variants[i - 1].replaceAll("[.][0-9]+", "").replaceAll(", ", "_");
            Path compositionPath = repoPath.resolve(name);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            service.checkout(variants[i - 1]);

            int[] missingSurplus = countMissingSurplus(compositionPath);
            missingSurplusLogger.add(i, variants[i - 1], String.valueOf(missingSurplus[0]), String.valueOf(missingSurplus[1]));
            logger.info("Checkout " + shortcut + i + " successful");
        }
        missingSurplusLogger.createCSV(repoPath, "missingSurplus");
    }

    private void checkExtensionalCorrectness(String[] commits, String ending) {
        log.add(0, "CheckoutECCOTime", "accumulatedCheckoutECCOTime", "missing", "surplus");
        long accumulatedTime = 0;
        int k = 1;
        for (Commit c : service.getCommits()) {
            System.out.println(c.getConfiguration().toString());

            Path compositionPath = repoPath.resolve(PythonAdapterRepositoryTestUtil.PATH_EXTENSIONAL + "/Commit" + k);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            String config = c.getConfiguration().toString();

            long timeElapsed = measureTime(() -> service.checkout(config));
            accumulatedTime += timeElapsed / 1000000;

            int[] missingSurplus = countMissingSurplus(compositionPath);

            log.add(k,
                    String.valueOf(((float) (timeElapsed / 1000000)) / 1000f),
                    String.valueOf((float) accumulatedTime / 1000f),
                    String.valueOf(missingSurplus[0]),
                    String.valueOf(missingSurplus[1])
            );

            logger.info("Checkout of Commit  " + k + " successful");

            // check all files of certain type
            List<Path> relPaths = Objects.requireNonNull(PythonAdapterRepositoryTestUtil.getRelativeFilePaths(compositionPath, ending));
            for (Path relPath : relPaths) {
                assertTrue(
                        compareFiles(compositionPath.resolve(relPath),
                                repoPath.resolve(commits[k - 1]).resolve(relPath)),
                        "Commit " + k + " extensional correctness test failed for " + relPath);
            }
            k++;
        }
    }

    private long measureTime(Runnable runnable) {
        long startCheckout = System.nanoTime();
        runnable.run();
        long finishCheckout = System.nanoTime();
        return finishCheckout - startCheckout;
    }

    private void recreateCommitsWithRedundancies(Path repoPath) {
        log.add(0, "redCheckoutECCOTime", "accumulatedRedCheckoutECCOTime", "redConfig");
        int k = 1;
        long accumulatedTime = 0;
        for (Commit c : service.getCommits()) {
            Path compositionPath = repoPath.resolve(PythonAdapterRepositoryTestUtil.PATH_EXTENSIONAL + "_red/Commit" + k);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            String config = c.getConfiguration().toString();
            if (!config.contains("redundant.1"))
                config += ", redundant.1";

            System.out.println(config);

            String finalConfig = config;
            long timeElapsed = measureTime(() -> service.checkout(finalConfig));

            accumulatedTime += timeElapsed / 1000000;
            log.add(k,
                    String.valueOf(((float) (timeElapsed / 1000000)) / 1000f),
                    String.valueOf((float) accumulatedTime / 1000f),
                    config);

            System.out.printf("Checkout of Commit %d (with redundancy) successful\n", k);
            k++;
        }
    }
}
