package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.lilypond.LilypondCompiler;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.ParserFactory;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.service.listener.ReadListener;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

public class PerformanceAndCorrectnessTest {

	private static final Path DATA_DIR;
	private static final Path BASE_DIR;
    private static final Path LILYPOND_PATH;
    private static final Path[] lilypond_search_paths;

	static {
		Path dataPath = null;
		try {
			dataPath = Paths.get(Objects.requireNonNull(PerformanceAndCorrectnessTest.class.getClassLoader().getResource("data")).toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		DATA_DIR = dataPath;

        Path startupPath = Path.of(System.getProperty("user.dir"));
        BASE_DIR = startupPath.getParent().getParent();

        LILYPOND_PATH = LilypondCompiler.LilypondPath();
        lilypond_search_paths = LilypondCompiler.LilypondSearchPaths();
    }

    @Test(groups = {"performance"})
    @BeforeClass(alwaysRun = true)
    private void setUp() {
        Logger log = LilypondReader.getLogger();
        log.setLevel(Level.ALL);
        try {
            FileHandler handler = new FileHandler("test.log");
            handler.setFormatter(new SimpleFormatter());
            log.addHandler(handler);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @DataProvider(name = "featurePaths")
    public static Object[][] featurePaths() {
        // input path relative to DATA_DIR, repository name and create metrics flag
        return new Object[][] {
                {BASE_DIR.getParent().resolve("lytests/debussy").toString(), ".eccoDebussy", true},
                {BASE_DIR.getParent().resolve("lytests/sulzer").toString(), ".eccoSulzer", true}
        };
    }

    @DataProvider(name = "serializedNodes")
    public static Object[][] serializedNodesPaths() {
        // input path relative to DATA_DIR, repository name and create metrics flag
        return new Object[][] {
                {DATA_DIR.resolve("input/dieu_nodes").toString(), ".eccoDebussy", true},
                {DATA_DIR.resolve("input/sulzer_nodes").toString(), ".eccoSulzer", true}
        };
    }

    @DataProvider(name = "checkoutConfigurations")
    public static Object[][] checkoutConfigurations() {
        String repo = ".eccoSulzer";

        return new Object[][] {
                // name, filename, repository, config, Lily_searchPaths[]
                {"AllFeatures",
                        "factusestrepente.ly",
                        repo,
                        """
partoneSopOneNSlurs.1, partoneTenOneLyrics.1, parttwoSopTwoNotes.1, scorePartOne.1, parttwoSopTwoBeams.1,
partoneTenOneNotes.1, partoneBasTwoLyrics.1, partoneSopOneLyrics.1, partoneTenTwoSlurs.1, partoneBasOneNotes.1,
partoneSopTwoArticulations.1, partoneTenTwoDynamics.1, partoneBasOneArticulations.1, parttwoSopOneLyrics.1,
partoneBasTwoDynamics.1, parttwoSopTwoDynamics.1, partoneBasOneSlurs.1, partoneSopOneNBeams.1,
parttwoSopTwoLyrics.1, partoneSopOneNArticulations.1, partoneBasTwoNotes.1, partoneTenOneBeams.1,
partoneTenTwoLyrics.1, partoneBasOneLyrics.1, partoneTenTwoBeams.1, parttwoSopOneSlurs.1, partoneSopTwoNotes.1,
partoneSopTwoLyrics.1, partoneSopTwoDynamics.1, partoneBasTwoArticulations.1, partoneBasTwoBeams.1,
partoneTenTwoArticulations.1, partoneSopTwoSlurs.1, partoneTenOneArticulations.1, parttwoSopOneNotes.1,
partoneSopTwoBeams.1, parttwoSopTwoSlurs.1, partoneBasTwoSlurs.1, parttwoSopOneBeams.1,
header.1, partoneSopOneNotes.1, partoneTenOneDynamics.1, partoneBasOneBeams.1, partoneSopOneNDynamics.1,
parttwoSopOneDynamics.1, partoneTenTwoNotes.1, scorePartTwo.1, partoneTenOneSlurs.1,
parttwoSopTwoArticulations.1, parttwoSopOneArticulations.1, partoneBasOneDynamics.1, text.1""",
                        null}/*,

                {"PartTwo",
                        "factusestrepente.ly",
                        repo,
                        """
header.1, scorePartTwo.1,
parttwoSopOneNotes.1, parttwoSopOneLyrics.1,
parttwoSopOneArticulations.1, parttwoSopOneDynamics.1,
parttwoSopOneSlurs.1, parttwoSopOneBeams.1, parttwoSopTwoNotes.1,
parttwoSopTwoLyrics.1, parttwoSopTwoArticulations.1,
parttwoSopTwoDynamics.1, parttwoSopTwoSlurs.1, parttwoSopTwoBeams.1""",
                        null},
                {"Test","factusestrepente.ly",repo,
                        "scorePartTwo.1, header.1, parttwoSopOneNotes.1, partoneTenOneDynamics.1, scorePartOne.1, partoneSopTwoNotes.1, partoneSopTwoDynamics.1, partoneTenOneNotes.1, parttwoSopOneDynamics.1",
                        null
                }
                {"SopranOne",
                        "factusestrepente.ly",
                        repo,
                        """
header.1, scorePartOne.1, scorePartTwo.1,
partoneSopOneNotes.1, partoneSopOneLyrics.1, partoneSopOneNArticulations.1,
partoneSopOneNDynamics.1, partoneSopOneNSlurs.1, partoneSopOneNBeams.1,
parttwoSopOneNotes.1, parttwoSopOneLyrics.1, parttwoSopOneNArticulations.1,
parttwoSopOneNDynamics.1, parttwoSopOneNSlurs.1, parttwoSopOneNBeams.1""",
                        null},

                {"NotesOnly",
                        "factusestrepente.ly",
                        repo,
                        """
partoneSopOneNSlurs.1, parttwoSopTwoNotes.1, scorePartOne.1, parttwoSopTwoBeams.1,
partoneTenOneNotes.1, partoneTenTwoSlurs.1, partoneBasOneNotes.1,
partoneSopTwoArticulations.1, partoneTenTwoDynamics.1, partoneBasOneArticulations.1,
partoneBasTwoDynamics.1, parttwoSopTwoDynamics.1, partoneBasOneSlurs.1, partoneSopOneNBeams.1,
partoneSopOneNArticulations.1, partoneBasTwoNotes.1, partoneTenOneBeams.1,
partoneTenTwoBeams.1, parttwoSopOneSlurs.1, partoneSopTwoNotes.1,
partoneSopTwoDynamics.1, partoneBasTwoArticulations.1, partoneBasTwoBeams.1,
partoneTenTwoArticulations.1, partoneSopTwoSlurs.1, partoneTenOneArticulations.1, parttwoSopOneNotes.1,
partoneSopTwoBeams.1, parttwoSopTwoSlurs.1, partoneBasTwoSlurs.1, parttwoSopOneBeams.1,
header.1, partoneSopOneNotes.1, partoneTenOneDynamics.1, partoneBasOneBeams.1, partoneSopOneNDynamics.1,
parttwoSopOneDynamics.1, partoneTenTwoNotes.1, scorePartTwo.1, partoneTenOneSlurs.1,
parttwoSopTwoArticulations.1, parttwoSopOneArticulations.1, partoneBasOneDynamics.1""",
                        null}*/
        };
    }

    @DataProvider(name = "sulzerFeatureDependencies")
    public static Object[][] sulzerFeatureDependencies() {
        return new Object[][] {
            // feature, featureDependency
                {
                        Map.ofEntries(
                            new AbstractMap.SimpleEntry<>("scorePartOne.1", "header.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneNotes.1", "scorePartOne.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoNotes.1", "scorePartOne.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneNotes.1", "scorePartOne.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoNotes.1", "scorePartOne.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneNotes.1", "scorePartOne.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoNotes.1", "scorePartOne.1"),

                            new AbstractMap.SimpleEntry<>("partoneSopOneNSlurs.1", "partoneSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneLyrics.1", "partoneSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneNBeams.1", "partoneSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneNArticulations.1", "partoneSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneNDynamics.1", "partoneSopOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneSopTwoSlurs.1", "partoneSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoLyrics.1", "partoneSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoBeams.1", "partoneSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoArticulations.1", "partoneSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoDynamics.1", "partoneSopTwoNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneTenOneSlurs.1", "partoneTenOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneLyrics.1", "partoneTenOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneBeams.1", "partoneTenOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneArticulations.1", "partoneTenOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneDynamics.1", "partoneTenOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneTenTwoSlurs.1", "partoneTenTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoLyrics.1", "partoneTenTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoBeams.1", "partoneTenTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoArticulations.1", "partoneTenTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoDynamics.1", "partoneTenTwoNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneBasOneSlurs.1", "partoneBasOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneLyrics.1", "partoneBasOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneBeams.1", "partoneBasOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneArticulations.1", "partoneBasOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneDynamics.1", "partoneBasOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneBasTwoSlurs.1", "partoneBasTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoLyrics.1", "partoneBasTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoBeams.1", "partoneBasTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoArticulations.1", "partoneBasTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoDynamics.1", "partoneBasTwoNotes.1"),

                            new AbstractMap.SimpleEntry<>("scorePartTwo.1", "header.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneNotes.1", "scorePartTwo.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoNotes.1", "scorePartTwo.1"),

                            new AbstractMap.SimpleEntry<>("parttwoSopOneSlurs.1", "parttwoSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneLyrics.1", "parttwoSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneBeams.1", "parttwoSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneArticulations.1", "parttwoSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneDynamics.1", "parttwoSopOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("parttwoSopTwoSlurs.1", "parttwoSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoLyrics.1", "parttwoSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoBeams.1", "parttwoSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoArticulations.1", "parttwoSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoDynamics.1", "parttwoSopTwoNotes.1")
                    )
                }
        };
    }

    /**
     * This method creates and populates a repository from previously parsed and serialized files from resources folder.
     */
    @Test(groups = {"performance"}, dataProvider = "serializedNodes")
    public void populateRepositoryFromNodes(String inPath, String repoName, boolean createMetrics) {
        ParserFactory.setParseFiles(false); // will load serialized nodes

        try {
            populateRepository(inPath, repoName, createMetrics);

        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test(groups = {"performance"}, dataProvider = "featurePaths")
    public void populateRepositoryFromFiles(String inPath, String repoName, boolean createMetrics) {
        try {
            populateRepository(inPath, repoName, createMetrics);

        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    private void populateRepository(String inPath, String repoName, boolean createMetrics) throws IOException {
        // open repository
        EccoService service = new EccoService();

        Path log = BASE_DIR.resolve("measure.csv");
        BufferedWriter wr = Files.newBufferedWriter(log, StandardCharsets.UTF_8);

        //create Repo
        Path repo = BASE_DIR.resolve(repoName);
        AdapterTest.deleteDirectoryContents(repo);
        Assert.assertFalse(Files.exists(repo));

        service.setRepositoryDir(repo);
        service.init();

        // features for commits are taken from config files
        long start = System.nanoTime();
        final int[] cntCommits = {0};

        EccoListener el = createMetrics ? getFileReadListener() : null;

        Path root = Path.of(inPath);
        List<Path> paths = Files.walk(root)
                .filter(d -> !d.equals(root) && Files.isDirectory(d))
                .sorted()
                .collect(Collectors.toList());
        for (Path p : paths) {
            long tm = System.nanoTime();
            if (createMetrics && p == paths.get(paths.size()-1)) {
                service.addListener(el);
            }
            service.setBaseDir(p);
            service.commit();
            cntCommits[0]++;

            long nanos = System.nanoTime() - tm;
            wr.write(LocalTime.ofNanoOfDay(nanos) + ";" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000000 + "\n");
            System.out.printf("*** committed in %s, using %dMB of memory ***\n",
                    LocalTime.ofNanoOfDay(nanos),
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000000);
        }

        if (createMetrics) {
            service.removeListener(el);
        }
        //System.out.printf("%d commits in %s\n", cntCommits[0], LocalTime.ofNanoOfDay(System.nanoTime() - start));
        wr.write(cntCommits[0] + ";" + LocalTime.ofNanoOfDay(System.nanoTime() - start) + "\n");
        wr.close();

        // close repository
        service.close();
        System.out.println("Repository closed.");
    }

    @Test(groups = {"correctness"}, dataProvider = "checkoutConfigurations")
    public void testCheckoutIsCompilable(String name, String filename, String repository, String config, String[] lilypondSearchPaths) {
        EccoService service = new EccoService();
        Path checkout = BASE_DIR.getParent().resolve("checkout");
        try {
            prepareCheckout(service, repository, checkout);
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail();
        }

        Checkout chk = service.checkout(config);
        System.out.println("selected associations:");
        for (Association a : chk.getSelectedAssociations()) {
            System.out.println(a.computeCondition().getSimpleModuleConditionString());
        }
        System.out.println("-------------------\n");

        if (isCompilable(checkout.resolve(filename), lilypondSearchPaths, false)) {
            System.out.println(name + " is COMPILABLE");
        } else {
            Assert.fail("configuration " + name + " is not compilable");
        }
    }

    private void prepareCheckout(EccoService service, String repositoryName, Path checkout) throws IOException {
        Path repo = BASE_DIR.resolve(repositoryName);
        if (!Files.exists(repo)) {
            throw new IOException("repository " + repo + " does not exist");
        }

        AdapterTest.deleteDirectoryContents(checkout);

        service.setRepositoryDir(repo);
        service.open();

        Files.createDirectory(checkout);
        service.setBaseDir(checkout);
    }

    @Ignore
    @Test(groups = {"correctness"}, dataProvider = "sulzerFeatureDependencies")
    public void generate100RandomSulzerConfigurations(Map<String, String> featureDependencies) {
        // open repository
        EccoService service = new EccoService();

        //open Repo
        Path repo = BASE_DIR.resolve(".eccoSulzer");
        if (!Files.exists(repo)) {
            Assert.fail("repository " + repo + " does not exist");
        }

        service.setRepositoryDir(repo);
        service.open();
        System.out.println("opened repository " + repo);

        Repository r = service.getRepository();
        List<FeatureRevision> features = r.getFeatures().stream().map(Feature::getLatestRevision).collect(Collectors.toList());
        System.out.println(features.size() + " features in repository");

        JDKRandomGenerator rnd = new JDKRandomGenerator(47277);
        Path result = BASE_DIR.resolve("01_configurations.txt");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(result, StandardOpenOption.CREATE))) {
            ArrayList<Set<String>> configs = new ArrayList<>();
            while (configs.size() < 100) {
                final int rndSize = 1 + rnd.nextInt(features.size()); // pick number of features
                TreeSet<String> config = new TreeSet<>();
                while (config.size() < rndSize) {
                    final int idx = rnd.nextInt(features.size()); // pick feature
                    String f = features.get(idx).getFeature().getName()
                            .concat(".")
                            .concat(features.get(idx).getId());
                    config.add(f);
                    // fix dependencies
                    while (featureDependencies.containsKey(f) && !config.contains(featureDependencies.get(f))) {
                        f = featureDependencies.get(f);
                        config.add(f);
                    }
                }

                if (!configs.contains(config)) {
                    configs.add(config);
                    out.write(String.join(",", config).concat("\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            Assert.fail();
        }
    }

    @Ignore
    @Test(groups = {"correctness"})
    public void storeGeneratedSulzerConfigurationsAsVariants() {
        // open repository
        EccoService service = new EccoService();

        //open Repo
        Path repo = BASE_DIR.resolve(".eccoSulzer_01");
        if (!Files.exists(repo)) {
            Assert.fail("repository " + repo + " does not exist");
        }

        service.setRepositoryDir(repo);
        service.open();
        System.out.println("opened repository " + repo);

        Path pConfigs = BASE_DIR.resolve("01_configurations.txt");
        List<String> configs;
        try {
            configs = Files.readAllLines(pConfigs);
        } catch (IOException ex) {
            Assert.fail(ex.getLocalizedMessage());
            return;
        }
        // TODO
    }

    @Test(dataProvider = "sulzerFeatureDependencies")
    public void testDependencyValidation(Map<String, String> featureDependencies) {
        ArrayList<String> conf = new ArrayList<>();
        conf.add("header.1");
        conf.add("scorePartOne.1");
        conf.add("partoneSopOneNotes.1");
        Assert.assertTrue(isDependencyValidConfiguration(conf, featureDependencies));

        conf.add("parttwoSopOneNotes.1");
        Assert.assertFalse(isDependencyValidConfiguration(conf, featureDependencies));
    }

    private boolean isDependencyValidConfiguration(ArrayList<String> config, Map<String, String> dependencies) {
        for (String c : config) {
            if (dependencies.containsKey(c) && !config.contains(dependencies.get(c))) {
                return false;
            }
        }
        return true;
    }

    @Test(groups = {"correctness"})
    public void findValidDebussyCompositions() {
        // open repository
        EccoService service = new EccoService();
        String filename = "dieu.ly";

        //open Repo
        Path repo = BASE_DIR.resolve(".eccoDebussy");
        if (!Files.exists(repo)) {
            System.out.println("repository " + repo + " does not exist");
            return;
        }

        service.setRepositoryDir(repo);
        service.open();

        Collection<Commit> commits = service.getCommits();
        commits.forEach(c -> System.out.println("commit " + c.getId() + ": " + c.getConfiguration()));

        Repository r = service.getRepository();
        System.out.println("features with latest revision:");
        List<FeatureRevision> features = r.getFeatures().stream().map(Feature::getLatestRevision).collect(Collectors.toList());
        features.forEach(fr -> System.out.println(fr.getFeature().getId() + ": " + fr.getFeature().getName() + "." + fr.getId()));

        List<String> compilableConfigs = new ArrayList<>();
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(15);

        for (int i=1; i<= features.size(); i++) {
            final long cnt = CombinatoricsUtils.binomialCoefficient(features.size(), i);
            System.out.println("\n--- " + cnt + " combinations with k=" + i + " for " + features.size() + " features ---");
            Iterator<int[]> itCombos = CombinatoricsUtils.combinationsIterator(features.size(), i);
            long l = 0;
            while (itCombos.hasNext()) {
                final int[] combination = itCombos.next();

                StringBuilder sb = new StringBuilder();
                for (int j=0; j<combination.length; j++) {
                    sb.append(features.get(combination[j]).getFeature().getName())
                            .append(".")
                            .append(features.get(combination[j]).getId());
                    if (j+1 < combination.length) sb.append(",");
                }
                final String config = sb.toString();
                //System.out.println("(" + ++l + "/" + cnt + ") " + config + ": ");

                try {
                    Path checkout = Files.createTempDirectory("chk_");
                    service.setBaseDir(checkout);
                    service.checkout(config);

                    executor.execute(new IsCompilableTask(checkout, filename, config, compilableConfigs));

                    while (executor.getActiveCount() > executor.getCorePoolSize() - 2) {
                        Thread.sleep(200);
                    }

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n" + compilableConfigs.size() + " compilable configs found:");
        try {
            Path result = BASE_DIR.resolve("combinations.txt");
            try (OutputStream os = Files.newOutputStream(result)) {
                for (String s : compilableConfigs) {
                    os.write(s.getBytes());
                    os.write("\n".getBytes());
                }
            }
            System.out.println("wrote combinations to " + result);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @DataProvider(name = "sulzerConfigurationsFiles")
    public static Object[][] sulzerConfigurationsFiles() {
        // run, repository, configurationsFile
        return new Object[][] {
                {1, ".eccoSulzer", "configurations.txt"}
                //{2, ".eccoSulzer", "configurations.txt"}
                //{3, ".eccoSulzer", "configurations.txt"}
                //{4, ".eccoSulzer", "configurations.txt"}
        };
    }

    @Test(groups = {"correctness"})
    public void testCommitSulzerConfig() {
        EccoService service = new EccoService();
        service.setRepositoryDir(BASE_DIR.resolve(".eccoSulzer_02"));
        service.open();

        Path inDir = BASE_DIR.getParent().resolve("checkout");
        service.setBaseDir(inDir);
        service.commit();

        System.out.println("committed to " + service.getRepositoryDir());
    }

    @Test(groups = {"correctness"}, dataProvider = "sulzerConfigurationsFiles")
    public void testSulzerConfigsAreCompilable(int run, String repoName, String configsFile) {
        // open repository
        EccoService service = new EccoService();
        String filename = "factusestrepente.ly";

        //open Repo
        Path repo = BASE_DIR.resolve(String.format("%s_%02d", repoName, run));
        if (!Files.exists(repo)) {
            Assert.fail("repository " + repo + " does not exist");
            return;
        }

        service.setRepositoryDir(repo);
        service.open();
        System.out.println("opened repository " + repo);

        Path pConfigs = BASE_DIR.resolve("01_".concat(configsFile)); // always check all 100 configs (fixes possibly break others such that they are no longer compilable)
        List<String> configs;
        try {
             configs = Files.readAllLines(pConfigs);
        } catch (IOException ex) {
            Assert.fail(ex.getLocalizedMessage());
            return;
        }

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
        ArrayList<String> compilableConfigs = new ArrayList<>();
        try {
            for (String c : configs) {
                Path checkout = Files.createTempDirectory("chk_");
                service.setBaseDir(checkout);
                service.checkout(c);

                executor.execute(new IsCompilableTask(checkout, filename, c, compilableConfigs));
            }

        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail();
        }

        executor.shutdown();
        try {
            Assert.assertTrue(executor.awaitTermination(15, TimeUnit.MINUTES));
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail();
        }

        Path invalidConfigs = BASE_DIR.resolve(String.format("%02d_%s", run + 1, configsFile));
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(invalidConfigs,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
            boolean checkedOutFirst = false;
            for (int i = 0; i < configs.size(); i++) {
                if (!compilableConfigs.contains(configs.get(i))) {
                    if (!checkedOutFirst) {
                        // checkout first uncompilable config to fix
                        checkedOutFirst = true;
                        Path checkout = BASE_DIR.getParent().resolve("checkout");
                        try {
                            AdapterTest.deleteDirectoryContents(checkout);
                            Files.createDirectory(checkout);
                            service.setBaseDir(checkout);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Assert.fail();
                        }

                        service.checkout(configs.get(i));
                        System.out.println("checked out one uncompilable config to " + checkout);
                    }
                    out.write(configs.get(i).concat("\n").getBytes(StandardCharsets.UTF_8));
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            Assert.fail();
        }

        System.out.println("run " + run + ": " + compilableConfigs.size() + " compilable configs, " +
                (configs.size() - compilableConfigs.size()) + " uncompilable");
    }

    private class IsCompilableTask implements Runnable {

        private Path checkout;
        private String filename;
        private String config;
        private List<String> configs;

        public IsCompilableTask(Path directory, String filename, String config, List<String> compilableConfigs) {
            checkout = directory;
            this.filename = filename;
            this.config = config;
            configs = compilableConfigs;
        }

        @Override
        public void run() {
            if (isCompilable(checkout.resolve(filename), null, true)) {
                configs.add(config);
            }

            // cleanup
            try {
                AdapterTest.deleteDirectoryContents(checkout);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isCompilable(Path lilyFile, String[] lilypond_searchPaths, Boolean silent) {
        if (null == lilyFile) return false;

        ProcessBuilder lilycmd=new ProcessBuilder(LILYPOND_PATH.toString());
        if (null != lilypond_searchPaths) {
            for (String p : lilypond_searchPaths) {
                lilycmd.command().add("-I");
                lilycmd.command().add(p);
            }
        }
        lilycmd.command().add(lilyFile.toString());

        Process process = null;
        try {
            process = lilycmd.start();
            StringJoiner sjErr;
            final BufferedReader errRd = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            sjErr = new StringJoiner(System.getProperty("line.separator"));
            errRd.lines().iterator().forEachRemaining(sjErr::add);
            errRd.close();

            int exitCode = -1;
            final int AWAIT_SECONDS = 30;
            if (process.waitFor(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                exitCode = process.exitValue();
            }

            if (exitCode < 0) {
                System.out.println("Lilypond could not finish in less than " + AWAIT_SECONDS + " seconds");
            }
            if (!silent || exitCode < -1) {
                System.out.println(sjErr);
            }
            return exitCode == 0;

        } catch (IOException | InterruptedException e) {
            return false;

        } finally {
            if (null != process) process.destroy();
        }
    }

    private EccoListener getFileReadListener() {
        return new EccoListener() {
            @Override
            public void fileReadEvent(Path file, ArtifactReader reader) {
                if (LilypondReader.class != reader.getClass()) return;

                final ReadListener rl = new ReadListener() {
                    @Override
                    public void fileReadEvent(Path file, ArtifactReader reader) {
                        Map<String, Integer> m = ((LilypondReader) reader).getTokenMetric();
                        SortedMap<Integer, String> sm = new TreeMap<>(Collections.reverseOrder());
                        m.forEach((k, v) -> sm.put(v, k));
                        sm.forEach((k, v) -> System.out.println(v + ": " + k.toString()));
                    }
                };

                ((LilypondReader) reader).setGenerateTokenMetric(true);
                reader.addListener(rl);
            }
        };
    }

}
