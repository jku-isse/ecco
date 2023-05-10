package at.jku.isse.ecco.adapter.lilypond.test.thesis;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.lilypond.LilypondCompiler;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.LilypondWriter;
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
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PerformanceAndCorrectnessTest {

	private static final Path DATA_DIR;
	private static final Path BASE_DIR;
    private static final Path LILYPOND_PATH;

	static {
		Path dataPath = null;
		try {
			dataPath = Paths.get(Objects.requireNonNull(PerformanceAndCorrectnessTest.class.getClassLoader().getResource("data")).toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		DATA_DIR = dataPath;

        Path startupPath = Path.of(System.getProperty("user.dir"));
        //BASE_DIR = startupPath.getParent().getParent();
        BASE_DIR = Path.of("G:/LilyECCO/");

        LILYPOND_PATH = LilypondCompiler.LilypondPath();
    }

    //@BeforeClass(alwaysRun = true)
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

    //@BeforeClass()
    private void initUI() {
        try {
            JavaFxLauncher.initialize();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //@AfterClass()
    private void teardown() {
        Platform.runLater(() -> JavaFxLauncher.stage.close());
    }

    @DataProvider(name = "featurePaths")
    public static Object[][] featurePaths() {
        // input path, output path, repository name, maxOrder and create metrics flag
        return new Object[][] {
                //{BASE_DIR.getParent().resolve("lytests/debussy").toString(), ".eccoDebussy", true},
                //{"F:/Uni/Lilypond_ECCO/lytests/sulzer", ".eccoSulzer", true}
/*
                {BASE_DIR.resolve("debussy_stepwise").toString(),
                        BASE_DIR.resolve("DebussyStep_maxOrder3").toString(), ".ecco", 3, false},
                {BASE_DIR.resolve("debussy_partwise").toString(),
                        BASE_DIR.resolve("DebussyPart_maxOrder3").toString(), ".ecco", 3, false},

                {BASE_DIR.resolve("debussy_stepwise").toString(),
                        BASE_DIR.resolve("DebussyStep_maxOrder4").toString(), ".ecco", 4, false},
                {BASE_DIR.resolve("debussy_partwise").toString(),
                        BASE_DIR.resolve("DebussyPart_maxOrder4").toString(), ".ecco", 4, false},

                {BASE_DIR.resolve("debussy_stepwise").toString(),
                        BASE_DIR.resolve("DebussyStep_maxOrder2").toString(), ".ecco", 2, false},
                {BASE_DIR.resolve("debussy_partwise").toString(),
                        BASE_DIR.resolve("DebussyPart_maxOrder2").toString(), ".ecco", 2, false},
*/
                {BASE_DIR.resolve("debussy_stepwise").toString(),
                        BASE_DIR.resolve("DebussyStepAdaptive_descendingMaxOrder2").toString(), ".ecco", 2, false},
//                {BASE_DIR.resolve("debussy_partwise").toString(),
//                        BASE_DIR.resolve("DebussyPartAdaptive_sortedMaxOrder2").toString(), ".ecco", 2, false}

        };
    }

    @DataProvider(name = "serializedNodes")
    public static Object[][] serializedNodesPaths() {
        // input path relative to DATA_DIR, repository name and create metrics flag
        return new Object[][] {
                {DATA_DIR.resolve("input/dieu_nodes").toString(), ".eccoDebussy", 2, true}
                //,{BASE_DIR.resolve("input/sulzer_nodes").toString(), ".eccoSulzer", 2, true}
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
partoneSopOneSlurs.1, partoneTenOneLyrics.1, parttwoSopTwoNotes.1, scorePartOne.1, parttwoSopTwoBeams.1,
partoneTenOneNotes.1, partoneBasTwoLyrics.1, partoneSopOneLyrics.1, partoneTenTwoSlurs.1, partoneBasOneNotes.1,
partoneSopTwoArticulations.1, partoneTenTwoDynamics.1, partoneBasOneArticulations.1, parttwoSopOneLyrics.1,
partoneBasTwoDynamics.1, parttwoSopTwoDynamics.1, partoneBasOneSlurs.1, partoneSopOneBeams.1,
parttwoSopTwoLyrics.1, partoneSopOneArticulations.1, partoneBasTwoNotes.1, partoneTenOneBeams.1,
partoneTenTwoLyrics.1, partoneBasOneLyrics.1, partoneTenTwoBeams.1, parttwoSopOneSlurs.1, partoneSopTwoNotes.1,
partoneSopTwoLyrics.1, partoneSopTwoDynamics.1, partoneBasTwoArticulations.1, partoneBasTwoBeams.1,
partoneTenTwoArticulations.1, partoneSopTwoSlurs.1, partoneTenOneArticulations.1, parttwoSopOneNotes.1,
partoneSopTwoBeams.1, parttwoSopTwoSlurs.1, partoneBasTwoSlurs.1, parttwoSopOneBeams.1,
header.1, partoneSopOneNotes.1, partoneTenOneDynamics.1, partoneBasOneBeams.1, partoneSopOneDynamics.1,
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
partoneSopOneNotes.1, partoneSopOneLyrics.1, partoneSopOneArticulations.1,
partoneSopOneDynamics.1, partoneSopOneSlurs.1, partoneSopOneBeams.1,
parttwoSopOneNotes.1, parttwoSopOneLyrics.1, parttwoSopOneNArticulations.1,
parttwoSopOneNDynamics.1, parttwoSopOneNSlurs.1, parttwoSopOneNBeams.1""",
                        null},

                {"NotesOnly",
                        "factusestrepente.ly",
                        repo,
                        """
partoneSopOneSlurs.1, parttwoSopTwoNotes.1, scorePartOne.1, parttwoSopTwoBeams.1,
partoneTenOneNotes.1, partoneTenTwoSlurs.1, partoneBasOneNotes.1,
partoneSopTwoArticulations.1, partoneTenTwoDynamics.1, partoneBasOneArticulations.1,
partoneBasTwoDynamics.1, parttwoSopTwoDynamics.1, partoneBasOneSlurs.1, partoneSopOneBeams.1,
partoneSopOneArticulations.1, partoneBasTwoNotes.1, partoneTenOneBeams.1,
partoneTenTwoBeams.1, parttwoSopOneSlurs.1, partoneSopTwoNotes.1,
partoneSopTwoDynamics.1, partoneBasTwoArticulations.1, partoneBasTwoBeams.1,
partoneTenTwoArticulations.1, partoneSopTwoSlurs.1, partoneTenOneArticulations.1, parttwoSopOneNotes.1,
partoneSopTwoBeams.1, parttwoSopTwoSlurs.1, partoneBasTwoSlurs.1, parttwoSopOneBeams.1,
header.1, partoneSopOneNotes.1, partoneTenOneDynamics.1, partoneBasOneBeams.1, partoneSopOneDynamics.1,
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

                            new AbstractMap.SimpleEntry<>("partoneSopOneSlurs.1", "partoneSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneLyrics.1", "partoneSopOneSlurs.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneBeams.1", "partoneSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneArticulations.1", "partoneSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopOneDynamics.1", "partoneSopOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneSopTwoSlurs.1", "partoneSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoLyrics.1", "partoneSopTwoSlurs.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoBeams.1", "partoneSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoArticulations.1", "partoneSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneSopTwoDynamics.1", "partoneSopTwoNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneTenOneSlurs.1", "partoneTenOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneLyrics.1", "partoneTenOneSlurs.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneBeams.1", "partoneTenOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneArticulations.1", "partoneTenOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenOneDynamics.1", "partoneTenOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneTenTwoSlurs.1", "partoneTenTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoLyrics.1", "partoneTenTwoSlurs.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoBeams.1", "partoneTenTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoArticulations.1", "partoneTenTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneTenTwoDynamics.1", "partoneTenTwoNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneBasOneSlurs.1", "partoneBasOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneLyrics.1", "partoneBasOneSlurs.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneBeams.1", "partoneBasOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneArticulations.1", "partoneBasOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasOneDynamics.1", "partoneBasOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("partoneBasTwoSlurs.1", "partoneBasTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoLyrics.1", "partoneBasTwoSlurs.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoBeams.1", "partoneBasTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoArticulations.1", "partoneBasTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("partoneBasTwoDynamics.1", "partoneBasTwoNotes.1"),

                            new AbstractMap.SimpleEntry<>("scorePartTwo.1", "header.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneNotes.1", "scorePartTwo.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoNotes.1", "scorePartTwo.1"),

                            new AbstractMap.SimpleEntry<>("parttwoSopOneSlurs.1", "parttwoSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneLyrics.1", "parttwoSopOneSlurs.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneBeams.1", "parttwoSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneArticulations.1", "parttwoSopOneNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopOneDynamics.1", "parttwoSopOneNotes.1"),

                            new AbstractMap.SimpleEntry<>("parttwoSopTwoSlurs.1", "parttwoSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoLyrics.1", "parttwoSopTwoSlurs.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoBeams.1", "parttwoSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoArticulations.1", "parttwoSopTwoNotes.1"),
                            new AbstractMap.SimpleEntry<>("parttwoSopTwoDynamics.1", "parttwoSopTwoNotes.1")
                    )
                }
        };
    }

    @DataProvider(name = "debussyFeatureDependencies")
    public static Object[][] debussyFeatureDependencies() {
        return new Object[][] {
                // feature, featureDependency
                {
                        Map.ofEntries(
                                new AbstractMap.SimpleEntry<>("sopNotes.1", "header.1"),
                                new AbstractMap.SimpleEntry<>("altNotes.1", "header.1"),
                                new AbstractMap.SimpleEntry<>("tenNotes.1", "header.1"),
                                new AbstractMap.SimpleEntry<>("basNotes.1", "header.1"),
                                
                                new AbstractMap.SimpleEntry<>("sopArticulation.1", "sopNotes.1"),
                                new AbstractMap.SimpleEntry<>("sopDynamics.1", "sopNotes.1"),
                                new AbstractMap.SimpleEntry<>("sopSlurs.1", "sopNotes.1"),
                                new AbstractMap.SimpleEntry<>("sopLyrics.1", "sopSlurs.1"),

                                new AbstractMap.SimpleEntry<>("altArticulation.1", "altNotes.1"),
                                new AbstractMap.SimpleEntry<>("altDynamics.1", "altNotes.1"),
                                new AbstractMap.SimpleEntry<>("altBeams.1", "altNotes.1"),
                                new AbstractMap.SimpleEntry<>("altSlurs.1", "altNotes.1"),
                                new AbstractMap.SimpleEntry<>("altLyrics.1", "altSlurs.1,altBeams.1"),

                                new AbstractMap.SimpleEntry<>("tenArticulation.1", "tenNotes.1"),
                                new AbstractMap.SimpleEntry<>("tenDynamics.1", "tenNotes.1"),
                                new AbstractMap.SimpleEntry<>("tenBeams.1", "tenNotes.1"),
                                new AbstractMap.SimpleEntry<>("tenSlurs.1", "tenNotes.1"),
                                new AbstractMap.SimpleEntry<>("tenLyrics.1", "tenSlurs.1,tenBeams.1"),

                                new AbstractMap.SimpleEntry<>("basArticulation.1", "basNotes.1"),
                                new AbstractMap.SimpleEntry<>("basDynamics.1", "basNotes.1"),
                                new AbstractMap.SimpleEntry<>("basBeams.1", "basNotes.1"),
                                new AbstractMap.SimpleEntry<>("basSlurs.1", "basNotes.1"),
                                new AbstractMap.SimpleEntry<>("basLyrics.1", "basSlurs.1,basBeams.1")
                        )
                }
        };
    }
    /**
     * This method creates and populates a repository from previously parsed and serialized files from resources folder.
     */
    @Ignore
    @Test(groups = {"performance"}, dataProvider = "serializedNodes")
    public void populateRepositoryFromNodes(String inPath, String outPath, String repoName, int maxOrder, boolean createMetrics) {
        ParserFactory.setParseFiles(false); // will load serialized nodes

        try {
            populateRepository(inPath, outPath, repoName, maxOrder, createMetrics);

        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test(groups = {"performance"}, dataProvider = "featurePaths")
    public void populateRepositoryFromFiles(String inPath, String outPath, String repoName, int maxOrder, boolean createMetrics) {
        try {
            populateRepository(inPath, outPath, repoName, maxOrder, createMetrics);

        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    private static int nextRun = 1;
    private static boolean adaptive = true;
    @Test(groups = {"correctness"}, dataProvider = "featurePaths")
    public void runDebussyConfigurations(String inPath, String outPath, String repoName, int maxOrder, boolean createMetrics) throws IOException {
        Path dest = Path.of(outPath);
        Path pConfigs = BASE_DIR.resolve("debussyConfigurations.txt");
        List<String> configs = null;
        try {
            configs = Files.readAllLines(pConfigs);
        } catch (IOException ex) {
            Assert.fail(ex.getLocalizedMessage());
        }
        configs.sort(Comparator.comparingInt(String::length));
        Collections.reverse(configs);

        Path results = dest.resolve(String.format("results%d.csv", maxOrder));
        // populate first repo
        if (nextRun == 1) {
            ThesisAdapterTest.deleteDirectoryContents(dest);
            Files.createDirectories(dest.resolve("fix"));

            populateRepositoryFromFiles(inPath, outPath, repoName + "_01", maxOrder, createMetrics);

            try (BufferedWriter wr = Files.newBufferedWriter(results, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                for (int i = 1; i <= configs.size(); i++) {
                    for (int j = 0; j < 4; j++) {
                        wr.write(";" + i);
                    }
                }
                wr.write("\n");
            }
        }

        int run = nextRun;
        ArrayList<Integer> fixed = new ArrayList<>();
        CompilationRunInfo cri = null;
        do {
            if (run > configs.size() + 1) {
                Assert.fail("could not solve " + configs.size() + " configurations");
            }
            try {
                Path repo = dest.resolve(String.format("%s_%02d", repoName, run));
                cri = runCompilationCheck(run, configs, repo, maxOrder, "dieu.ly", results);
                if (adaptive) {
                    // try to check uncompilable configs which are fixed with higher order
                    int i = 0;
                    while (i < cri.uncompilable.size()) {
                        if (fixed.contains(cri.uncompilable.get(i))) {
                            // open repository
                            EccoService service = new EccoService();
                            service.setRepositoryDir(repo);
                            service.open();
                            ((Repository.Op)service.getRepository()).setMaxOrder(maxOrder + 1);
                            System.out.println("raised max order to " + (maxOrder + 1) + " for config " + cri.uncompilable.get(i));

                            IsCompilableCheckoutTask t = new IsCompilableCheckoutTask(service, "dieu.ly",
                                    cri.uncompilable.get(i), configs.get(cri.uncompilable.get(i)));
                            CompilationInfo ci = t.call();
                            if (ci.compilable) {
                                cri.uncompilable.remove(i);
                                i--;
                            }
                        }
                        i++;
                    }
                }
                if (cri != null && cri.uncompilable.size() > 0) {
                    Path newRepo = dest.resolve(String.format("%s_%02d", repoName, ++run));
                    Files.copy(repo, newRepo);
                    try (Stream<Path> paths = Files.list(repo)) {
                        paths.forEach(p -> {
                            try {
                                Files.copy(p, newRepo.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                Assert.fail(e.getMessage());
                            }
                        });
                    }

                    // find uncompilable without a fix
                    int nextFix = -1, i = 0;
                    while (nextFix < 0 && i < cri.uncompilable.size()) {
                        if (!fixed.contains(cri.uncompilable.get(i))) {
                            nextFix = cri.uncompilable.get(i);
                            fixed.add(nextFix);
                        }
                        i++;
                    }
                    if (nextFix < 0) {
                        break;
                    }

                    createFixAndCommit(newRepo, configs.get(nextFix), dest.resolve("fix/dieu.ly"), maxOrder);
                }

            } catch (InterruptedException | IOException e) {
                Assert.fail(e.getMessage());
            }

        } while (cri != null && cri.uncompilable.size() != 0);
    }

    private void populateRepository(String inPath, String outPath, String repoName, int maxOrder, boolean createMetrics) throws IOException {
        //Path log = BASE_DIR.resolve("measure_" + repoName.replace(".ecco", "") + ".csv");
        //BufferedWriter wr = Files.newBufferedWriter(log, StandardCharsets.UTF_8);

        //create Repo
        Path repo = Path.of(outPath).resolve(repoName);
        ThesisAdapterTest.deleteDirectoryContents(repo); // delete old repo
        Assert.assertFalse(Files.exists(repo));

        // open repository
        EccoService service = new EccoService();
        service.setRepositoryDir(repo);
        service.init();
        ((Repository.Op)service.getRepository()).setMaxOrder(maxOrder);

        //long start = System.nanoTime();
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
            //wr.write(LocalTime.ofNanoOfDay(nanos) + ";" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000000 + "\n");
            System.out.printf("*** committed in %s, using %dMB of memory ***\n",
                    LocalTime.ofNanoOfDay(nanos),
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000000);
        }

        if (createMetrics) {
            service.removeListener(el);
        }
        //System.out.printf("%d commits in %s\n", cntCommits[0], LocalTime.ofNanoOfDay(System.nanoTime() - start));
        //wr.write(cntCommits[0] + ";" + LocalTime.ofNanoOfDay(System.nanoTime() - start) + "\n");
        //wr.close();

        // close repository
        service.close();
        System.out.println("Repository closed.");
    }

    @DataProvider(name = "readWriteSamples")
    public static Object[][] readWriteSamples() {
        return new Object[][] {
            // in path, includes path, out path
            { "F:/Uni/samples/abidewithme.ly", "F:/Uni/samples/", "F:/Uni/samples/out/" },
            { "F:/Uni/samples/assumptaestmaria.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            { "F:/Uni/samples/caligaveruntoculimei.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            { "F:/Uni/samples/darthula.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            { "F:/Uni/samples/forthelongesttimebaumgartner.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            { "F:/Uni/samples/litaney.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            { "F:/Uni/samples/mendelssohn_trio.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            { "F:/Uni/samples/nachtwacheeins.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            { "F:/Uni/samples/nessundorma.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"},
            //{ "F:/Uni/samples/Oblivion2.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"}, // missing "november2.ly"
            { "F:/Uni/samples/verleihunsfrieden.ly", "F:/Uni/samples/", "F:/Uni/samples/out/"}
        };
    }

    @Test(groups = {"correctness"}, dataProvider = "readWriteSamples")
    public void testAfterReaderWriterIsCompilable(String inPath, String includesPath, String outPath) {
        Path p = Path.of(inPath);
        String[] pInc = new String[]{ includesPath };
        if (!isCompilable(p, pInc, true)) {
            Assert.fail(p + " is not compilable");
        }

        LilypondReader rd = new LilypondReader((new MemEntityFactory()));
        Path pOut = Path.of(outPath);
        Path nm = p.getFileName();
        Path[] input = new Path[]{nm};
        Set<Node.Op> nodes = rd.read(p.getParent(), input);

        LilypondWriter lw = new LilypondWriter();
        lw.write(pOut, nodes.stream().map(op -> (Node)op).collect(Collectors.toSet()));

        Assert.assertTrue(isCompilable(pOut.resolve(nm), pInc, false));
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

        ThesisAdapterTest.deleteDirectoryContents(checkout);

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
        Path result = BASE_DIR.resolve("configurations.txt");
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
                    config.addAll(getDependencyFeatures(featureDependencies, f));
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
    @Test(groups = {"correctness"}, dataProvider = "debussyFeatureDependencies")
    public void generate50RandomDebussyConfigurations(Map<String, String> featureDependencies) {
        // open repository
        EccoService service = new EccoService();

        //open Repo
        Path repo = BASE_DIR.resolve(".eccoDebStep");
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
        Path result = BASE_DIR.resolve("debStepConfigurations.txt");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(result, StandardOpenOption.CREATE))) {
            ArrayList<Set<String>> configs = new ArrayList<>();
            while (configs.size() < 50) {
                final int rndSize = 1 + rnd.nextInt(features.size()); // pick number of features
                TreeSet<String> config = new TreeSet<>();
                while (config.size() < rndSize) {
                    final int idx = rnd.nextInt(features.size()); // pick feature
                    String f = features.get(idx).getFeature().getName()
                            .concat(".")
                            .concat(features.get(idx).getId());
                    config.add(f);

                    // fix dependencies
                    TreeSet<String> fds = getDependencyFeatures(featureDependencies, f);
                    config.addAll(fds);
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

    private TreeSet<String> getDependencyFeatures(Map<String, String> dependencies, String feature) {
        TreeSet<String> rs = new TreeSet<>();
        if (dependencies.containsKey(feature)) {
            String[] fds = dependencies.get(feature).split(",");
            for (String f : fds) {
                rs.add(f);
                if (dependencies.containsKey(f)) {
                    rs.addAll(getDependencyFeatures(dependencies, f));
                }
            }
        }
        return rs;
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

    @Test(groups = {"correctness"})
    public void testCommitConfig() {
        EccoService service = new EccoService();
        service.setRepositoryDir(BASE_DIR.resolve(".eccoDebPart_05"));
        service.open();

        Path inDir = BASE_DIR.getParent().resolve("fixes_debPart/04");
        service.setBaseDir(inDir);
        service.commit();

        System.out.println("committed to " + service.getRepositoryDir());
    }

    private void createFixAndCommit(Path repo, String config, Path file, int maxOrder) {
        HashSet<String> features = new HashSet<>(Arrays.asList(config.split(",")));
        try {
            if (Files.exists(file)) {
                Files.delete(file);
            }
            try (BufferedWriter wr = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                wr.write(DieuFeatureCode.getCode(features));
            }

            file = file.getParent().resolve(".config");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            try (BufferedWriter wr = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                wr.write(config);
            }

        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }

        EccoService service = new EccoService();
        service.setRepositoryDir(repo);
        service.open();
        service.setBaseDir(file.getParent());
        ((Repository.Op)service.getRepository()).setMaxOrder(maxOrder);
        service.commit();

        System.out.println("committed to " + service.getRepositoryDir());
    }

    @DataProvider(name = "configurationsFiles")
    public static Object[][] configurationsFiles() {
        // run, repository, resultsFile
        return new Object[][] {
                //{1, "configurations.txt", ".eccoSulzer", "factusestrepente.ly", "results.csv"}
                //{9, "debussyConfigurations.txt", ".eccoDebStep", "dieu.ly", "debstep_results.csv"}
                {16, "debussyConfigurations.txt", ".eccoDebPart", "dieu.ly", "debpart_results.csv"}
        };
    }

    @Test(groups = {"correctness"}, dataProvider = "configurationsFiles")
    public void testConfigsAreCompilable(int run, String configsFile, String repoName, String filename, String resultsFile) throws InterruptedException {
        Path pConfigs = Path.of(configsFile); // check all configs on each run (fixes possibly break others such that they are no longer compilable)
        List<String> configs = null;
        try {
            configs = Files.readAllLines(pConfigs);
        } catch (IOException ex) {
            Assert.fail(ex.getLocalizedMessage());
        }
        //runCompilationCheck(run, configs, repoName, 2, filename, resultsFile);
    }

    private CompilationRunInfo runCompilationCheck(int run, List<String> configs, Path repo, int maxOrder
            , String filename, Path resultFile) throws InterruptedException {
/*
        VBox box = new VBox();
        box.setPadding(new Insets(10));
        box.setSpacing(2);
        ProgressBar pb = new ProgressBar(-1.0);
        pb.setMaxWidth(Double.MAX_VALUE);
        Label lbInfo = new Label("open repository");
        box.getChildren().addAll(pb, lbInfo);
        Scene scene = new Scene(box, 300, 60);
        try {
            JavaFxLauncher.setScene(scene);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
*/
        // open repository
        EccoService service = new EccoService();

        //open Repo
        if (Files.notExists(repo)) {
            Assert.fail("repository " + repo + " does not exist");
            return null;
        }

        service.setRepositoryDir(repo);
        service.open();
        ((Repository.Op)service.getRepository()).setMaxOrder(maxOrder);
        System.out.println("opened repository " + repo);

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(1, 7 - maxOrder));
        ExecutorCompletionService<CompilationInfo> exSvc = new ExecutorCompletionService<>(executor);

        TreeMap<Integer, CompilationInfo> results = new TreeMap<>();
/*
        Platform.runLater(() -> {
            lbInfo.setText("checkout some configurations");
            pb.setProgress(0);
        });
*/
        ArrayList<Future<CompilationInfo>> tasks = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            tasks.add(exSvc.submit(new IsCompilableCheckoutTask(service, filename, i, configs.get(i))));
        }

        int progress = 0;
        while (!tasks.isEmpty()) {
            ListIterator<Future<CompilationInfo>> it = tasks.listIterator();
            while (it.hasNext()) {
                Future<CompilationInfo> e = it.next();
                if (e.isDone()) {
                    try {
                        CompilationInfo rs = e.get();
                        results.put(rs.configId, rs);
                    } catch (Exception ex) {
                        Assert.fail(ex.getMessage());
                    }
/*                    progress++;
                    final int p = progress;
                    Platform.runLater(() -> {
                        lbInfo.setText("checking configurations (" + p + "/" + configs.size() + ")");
                        pb.setProgress((double)p / configs.size());
                    });

*/
                    it.remove();
                }
            }

            Thread.sleep(1000);
        }

        executor.shutdown();
        try {
            Assert.assertTrue(executor.awaitTermination(15, TimeUnit.MINUTES));
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail();
        }

        List<Integer> uncompilable = new ArrayList<>();
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(resultFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND))) {

            out.write((run + ";").getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < configs.size(); i++) {
                CompilationInfo ci = results.get(i);
                long nFeatures = configs.get(i).chars().filter(c -> c == ',').count() + 1;
                if (!ci.compilable) {
                    uncompilable.add(ci.configId);
                }
                out.write(String.format("%d;%d;%d;%d", ci.compilable ? 1 : 0, nFeatures, ci.nMissing, ci.nSurplus).getBytes(StandardCharsets.UTF_8));
/*
                if (ci.compilable) {
                    out.write(String.format("1;%d;%d;%d", nFeatures, ci.nMissing, ci.nSurplus).getBytes(StandardCharsets.UTF_8));

                } else {
                    if (!checkedOutFirst) {
                        // checkout first uncompilable config to fix
                        checkedOutFirst = true;
                        Path checkout = BASE_DIR.getParent().resolve("checkout");
                        try {
                            ThesisAdapterTest.deleteDirectoryContents(checkout);
                            Files.createDirectory(checkout);
                            service.setBaseDir(checkout);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Assert.fail();
                        }

                        service.checkout(configs.get(i));
                        System.out.println("checked out uncompilable config " + (i + 1) + " to " + checkout);
                    }
                    out.write(String.format("0;%d;%d;%d", nFeatures, ci.nMissing, ci.nSurplus).getBytes(StandardCharsets.UTF_8));
                }*/
                if (i + 1 < configs.size()) {
                    out.write(";".getBytes(StandardCharsets.UTF_8));
                }
            }
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));

        } catch (IOException ex) {
            ex.printStackTrace();
            Assert.fail();
        }

        int compilable = configs.size() - uncompilable.size();
        System.out.println("run " + run + ": " + compilable + " compilable configs, " +
                uncompilable.size() + " uncompilable");

        return new CompilationRunInfo(run, uncompilable);
    }

    //@Ignore
    @Test
    public void buildDieuPartwise() {
        String[] features = new String[]{"header.1"
                , "sopNotes.1", "sopArticulation.1", "sopDynamics.1", "sopSlurs.1", "sopLyrics.1"
                , "altNotes.1", "altArticulation.1", "altDynamics.1", "altBeams.1", "altSlurs.1", "altLyrics.1"
                , "tenNotes.1", "tenArticulation.1", "tenDynamics.1", "tenBeams.1", "tenSlurs.1", "tenLyrics.1"
                , "basNotes.1", "basArticulation.1", "basDynamics.1", "basBeams.1", "basSlurs.1", "basLyrics.1"};

        LinkedList<int[]> commits = new LinkedList<>();
        commits.add(new int[] {0});

        commits.add(new int[] {0, 1});
        commits.add(new int[] {0, 1, 2});
        commits.add(new int[] {0, 1, 3});
        commits.add(new int[] {0, 1, 4});
        commits.add(new int[] {0, 1, 4, 5});
        commits.add(new int[] {0, 1, 2, 3, 4, 5});

        commits.add(new int[] {0, 6});
        commits.add(new int[] {0, 6, 7});
        commits.add(new int[] {0, 6, 8});
        commits.add(new int[] {0, 6, 9});
        commits.add(new int[] {0, 6, 10});
        commits.add(new int[] {0, 6, 9, 10, 11});
        commits.add(new int[] {0, 6, 7, 8, 9, 10, 11});

        commits.add(new int[] {0, 12});
        commits.add(new int[] {0, 12, 13});
        commits.add(new int[] {0, 12, 14});
        commits.add(new int[] {0, 12, 15});
        commits.add(new int[] {0, 12, 16});
        commits.add(new int[] {0, 12, 15, 16, 17});
        commits.add(new int[] {0, 12, 13, 14, 15, 16, 17});

        commits.add(new int[] {0, 18});
        commits.add(new int[] {0, 18, 19});
        commits.add(new int[] {0, 18, 20});
        commits.add(new int[] {0, 18, 21});
        commits.add(new int[] {0, 18, 22});
        commits.add(new int[] {0, 18, 21, 22, 23});
        commits.add(new int[] {0, 18, 19, 20, 21, 22, 23});

        commits.add(IntStream.rangeClosed(0, 23).toArray());

        Path root = Path.of("F:/Uni/ecco_lukas_dev/lytests/debussy_partwise/");
        int i = 1;
        for (int[] indexes : commits) {
            Path dest = root.resolve(String.format("%02d/", i));
            try {
                Files.createDirectories(dest);

                Path file = dest.resolve("dieu.ly");
                HashSet<String> config = new HashSet<>();
                for (int idx : indexes) {
                    config.add(features[idx]);
                }
                try (BufferedWriter wr = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    String code = DieuFeatureCode.getCode(config);
                    wr.write(code);
                }

                file = dest.resolve(".config");
                try (BufferedWriter wr = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    wr.write(String.join(", ", config));
                }

                i++;

            } catch (IOException e) {
                Assert.fail(e.getMessage());
            }

        }
    }

    @Test
    public void buildDieuStepwise() {
        String[] features = new String[]{"header.1"
                , "sopNotes.1", "sopArticulation.1", "sopDynamics.1", "sopSlurs.1", "sopLyrics.1"
                , "altNotes.1", "altArticulation.1", "altDynamics.1", "altBeams.1", "altSlurs.1", "altLyrics.1"
                , "tenNotes.1", "tenArticulation.1", "tenDynamics.1", "tenBeams.1", "tenSlurs.1", "tenLyrics.1"
                , "basNotes.1", "basArticulation.1", "basDynamics.1", "basBeams.1", "basSlurs.1", "basLyrics.1"};

        Path root = Path.of("F:/Uni/ecco_lukas_dev/lytests/debussy_stepwise/");
        for (int i = 1; i <= 24; i++) {
            Path dest = root.resolve(String.format("%02d/", i));
            try {
                Files.createDirectories(dest);

                Path file = dest.resolve("dieu.ly");
                HashSet<String> config = new HashSet<>();
                for (int idx = 0; idx < i; idx++) {
                    config.add(features[idx]);
                }
                try (BufferedWriter wr = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    wr.write(DieuFeatureCode.getCode(config));
                }

                file = dest.resolve(".config");
                try (BufferedWriter wr = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    wr.write(String.join(", ", config));
                }

            } catch (IOException e) {
                Assert.fail(e.getMessage());
            }

        }
    }

    private record CompilationInfo(int configId, int nMissing, int nSurplus, boolean compilable) {}
    private record CompilationRunInfo(int run, List<Integer> uncompilable) {}

    private class IsCompilableCheckoutTask implements Callable<CompilationInfo> {

        private final EccoService service;
        private final String filename;
        private final int configId;
        private final String config;

        public IsCompilableCheckoutTask(EccoService service, String filename, int configId, String config) {
            this.service = service;
            this.filename = filename;
            this.configId = configId;
            this.config = config;
        }

        @Override
        public CompilationInfo call() {
            boolean compilable = false;
            AtomicInteger missing = new AtomicInteger();
            AtomicInteger surplus = new AtomicInteger();
            try {
                Path checkout = Files.createTempDirectory("chk_");
                synchronized (service) {
                    service.setBaseDir(checkout);
                    service.checkout(config);
                }

                Path warningsFile = checkout.resolve(".warnings");
                if (Files.exists(warningsFile)) {
                    Stream<String> lines = Files.lines(warningsFile, StandardCharsets.UTF_8);
                    lines.forEach(l -> {
                        if (l.startsWith("MISSING")) {
                            missing.getAndIncrement();
                        } else if (l.startsWith("SURPLUS")) {
                            surplus.getAndIncrement();
                        }
                    });
                }

                compilable = isCompilable(checkout.resolve(filename), null, true);

                // cleanup
                ThesisAdapterTest.deleteDirectoryContents(checkout);

            } catch (IOException e) {
                Assert.fail(e.getLocalizedMessage());
            }

            return new CompilationInfo(configId, missing.get(), surplus.get(), compilable);
        }
    }

    private class IsCompilableTask implements Runnable {

        private final Path checkout;
        private final String filename;
        private final String config;
        private final List<String> configs;

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
                ThesisAdapterTest.deleteDirectoryContents(checkout);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testExampleCompilable() {
        for (int i = 1; i < 30; i++) {
            Path p = Path.of(String.format("F:/Uni/ecco_lukas_dev/lytests/debussy_partwise/%02d/dieu.ly", i));
            Assert.assertTrue(isCompilable(p, null, false));
            // cleanup
            p = p.getParent().resolve("dieu.pdf");
            if (Files.exists(p)) {
                try {
                    Files.delete(p.getParent().resolve("dieu.pdf"));
                } catch (IOException e) {
                    Assert.fail();
                }
            }
        }
    }

    private boolean isCompilable(Path lilyFile, String[] lilypond_searchPaths, Boolean silent) {
        if (null == lilyFile) return false;

        ProcessBuilder lilycmd=new ProcessBuilder(LILYPOND_PATH.toString());
        lilycmd.directory(lilyFile.getParent().toFile());
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
