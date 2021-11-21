package at.jku.isse.ecco.adapter.lilypond.test;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.ParserFactory;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.service.listener.ReadListener;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

public class PerformanceAndCorrectnessTest {

	private static final Path DATA_DIR;
	private static final Path BASE_DIR;

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
    }

    @Test(groups = {"parce", "performance"})
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
                //{BASE_DIR.getParent().resolve("lytests/debussy").toString(), ".eccoDebussy", true}
                {BASE_DIR.getParent().resolve("lytests/sulzer").toString(), ".eccoSulzer", true}
        };
    }

    @DataProvider(name = "serializedNodes")
    public static Object[][] serializedNodesPaths() {
        // input path relative to DATA_DIR, repository name and create metrics flag
        return new Object[][] {
                //{DATA_DIR.resolve("input/debussy_parce0.13_nodes").toString(), ".eccoDebussy13", true}
                {DATA_DIR.resolve("input/sulzer_parce0.13_nodes").toString(), ".eccoSulzer13", true}
                //{DATA_DIR.resolve("input/dieu_nodes").toString(), ".eccoDebussy", true}
                //{DATA_DIR.resolve("input/sulzer_nodes").toString(), ".eccoSulzer", true}
        };
    }

    @DataProvider(name = "checkoutConfigurations")
    public static Object[][] checkoutConfigurations() {
        String repo = ".eccoSulzerOhneWS";

        return new Object[][] {
                // name, filename, repository, config, Lily_searchPaths[]
/*                {"AllFeatures",
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
                        null},
*/
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
                        null}/*,

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
parttwoSopTwoNotes.1, scorePartOne.1,
partoneTenOneNotes.1, partoneBasOneNotes.1,
partoneBasTwoNotes.1, partoneSopTwoNotes.1,
parttwoSopOneNotes.1, header.1, partoneSopOneNotes.1, partoneTenTwoNotes.1, scorePartTwo.1""",
                        null},

                {"FemaleVoicesNotesOnly",
                        "factusestrepente.ly",
                        repo,
                        """
header.1, scorePartOne.1, scorePartTwo.1,
partoneSopOneNotes.1, partoneSopTwoNotes.1,
parttwoSopOneNotes.1, parttwoSopTwoNotes.1""",
                        null}*/
        };
    }

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

    // TODO: needs -Xss1024m in run configuration as VM option
    @Test(groups = {"parce", "performance"}, dataProvider = "featurePaths")
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
            System.out.printf("*** commited in %s, using %dMB of memory ***\n",
                    LocalTime.ofNanoOfDay(nanos),
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000000);
        }

        if (createMetrics) {
            service.removeListener(el);
        }
        System.out.printf("%d commits in %s\n", cntCommits[0], LocalTime.ofNanoOfDay(System.nanoTime() - start));

        // close repository
        service.close();
        System.out.println("Repository closed.");
    }

    // TODO: needs -Xss1024m in run configuration
    @Test(groups = { "parce", "lilypond" }, dataProvider = "checkoutConfigurations")
    public void testCheckoutIsCompileable(String name, String filename, String repository, String config, String[] lilypondSearchPaths) {
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

        if (isCompileable(checkout.resolve(filename), lilypondSearchPaths)) {
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

    /**
     * Configure path to lilypond executable file
     * TODO: move path configuration to gradle build file
     */
    private final Path lilypond_exe = Path.of("C:/Program Files (x86)/LilyPond/usr/bin/lilypond-windows");

    private boolean isCompileable(Path lilyFile, String[] lilypond_searchPaths) {
        if (null == lilyFile) return false;

        ProcessBuilder lilycmd=new ProcessBuilder(lilypond_exe.toString());
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
            final BufferedReader errRd = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            StringJoiner sjErr = new StringJoiner(System.getProperty("line.separator"));
            errRd.lines().iterator().forEachRemaining(sjErr::add);

            int exitCode = -1;
            if (process.waitFor(15, TimeUnit.SECONDS)) {
                exitCode = process.exitValue();
            }

            System.out.println(sjErr);
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
