package at.jku.isse.ecco.adapter.challenge.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.challenge.data.*;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public class ChallengeTest {

	// set this path to where the argouml challenge benchmark is located
	private static final Path BENCHMARK_DIR = Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\workspace\\ArgoUMLSPLBenchmark");
	//private static final Path BENCHMARK_DIR = Paths.get("D:\\ArgoUMLSPLBenchmark");

	// set this path to where the results should be stored
	private static final Path OUTPUT_DIR = Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\results");
	//private static final Path OUTPUT_DIR = Paths.get("D:\\results");

	/**
	 * Creates repository and computes results and stores them in OUTPUT_DIR for every scenario in BENCHMARK_DIR.
	 */
	@Test(groups = {"integration", "challenge"})
	public void Do_All_Scenarios() throws IOException {
		// collect all scenario folders
		List<Path> scenarioDirs = Files.list(BENCHMARK_DIR.resolve("scenarios")).filter(path -> Files.isDirectory(path)).collect(Collectors.toList());

		for (Path scenarioDir : scenarioDirs) {
			Path scenarioOutputDir = OUTPUT_DIR.resolve(scenarioDir.getFileName());
			Files.createDirectory(scenarioOutputDir);
			// create the repository for the scenario
			this.createRepo(scenarioDir, scenarioOutputDir);
			// compute the results for the scenario
			this.computeResults(scenarioOutputDir);
			// compute the metrics from the results
			MetricsCalculation.computeMetrics(BENCHMARK_DIR.resolve("groundTruth"), scenarioOutputDir);
		}
	}


	// set this path to a concrete scenario if you only want to run a specific one
	private static final Path SCENARIO_DIR = BENCHMARK_DIR.resolve("scenarios\\ScenarioAllVariants");
	// set this path to where the results for a specific scenario should go
	private static final Path SCENARIO_OUTPUT_DIR = OUTPUT_DIR.resolve("ScenarioAllVariants");

	/**
	 * Creates repository in SCENARIO_OUTPUT_DIR for specific scenario in SCENARIO_DIR.
	 */
	@Test(groups = {"integration", "challenge"})
	public void Create_Repo() throws IOException {
		this.createRepo(SCENARIO_DIR, SCENARIO_OUTPUT_DIR);
	}

	/**
	 * Computes results from repository stored in SCENARIO_OUTPUT_DIR and stores them in SCENARIO_OUTPUT_DIR.
	 */
	@Test(groups = {"integration", "challenge"})
	public void Compute_Results() throws IOException {
		this.computeResults(SCENARIO_OUTPUT_DIR);
	}

	@Test(groups = {"integration", "challenge"})
	public void Compute_Metrics() {
		MetricsCalculation.computeMetrics(BENCHMARK_DIR.resolve("groundTruth"), SCENARIO_OUTPUT_DIR);
	}


	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");

		// configure logger
		Logger logger = Logger.getLogger("at.jku.isse.ecco");
		logger.setLevel(Level.ALL);
		for (Handler handler : logger.getHandlers())
			logger.removeHandler(handler);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.ALL);
		handler.setFormatter(new SimpleFormatter());
		logger.addHandler(handler);
		logger.setUseParentHandlers(false);
		logger.info("Logging to: " + Arrays.stream(logger.getHandlers()).map(Object::toString).collect(Collectors.joining(", ")));
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}


	private void createRepo(Path scenarioDir, Path scenarioOutputDir) throws IOException {
		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(scenarioOutputDir.resolve("repo"));
		service.init();
		System.out.println("Repository initialized.");

		// commit all existing variants to the new repository
		Path variantsDir = scenarioDir.resolve("variants");
		Path configsDir = scenarioDir.resolve("configs");

		List<Long> runtimes = new ArrayList<>();
		int counter = 0;
		Collection<Path> variantsDirs = Files.list(variantsDir).collect(Collectors.toList());
		for (Path variantDir : variantsDirs) {
//			// this is to avoid overheating of my laptop for large scenarios
//			try {
//				Thread.sleep(20000);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}

			long before = System.currentTimeMillis();

			System.out.println("COUNT: " + counter);
			System.out.println("Committing: " + variantDir);

			Path configFile = configsDir.resolve(variantDir.getFileName());
			String configurationString = Files.readAllLines(configFile).stream().map(featureString -> featureString + ".1").collect(Collectors.joining(","));
			if (configurationString.isEmpty())
				configurationString = "BASE.1";
			else
				configurationString = "BASE.1," + configurationString;
			System.out.println("CONFIG: " + configurationString);

			service.setBaseDir(variantDir.resolve("src"));
			service.commit(configurationString);

			System.out.println("Committed: " + variantDir);
			counter++;

			long after = System.currentTimeMillis();
			long runtime = after - before;
			runtimes.add(runtime);
			System.out.println("TIME: " + runtime + "ms");
		}

		// close repository
		service.close();
		System.out.println("Repository closed.");

		Files.write(scenarioOutputDir.resolve("time.txt"), runtimes.stream().map(Object::toString).collect(Collectors.toList()));
	}


	private static final boolean NO_OR = false;
	private static final boolean USE_ONLY_MIN_ORDER = true;
	private static final int MAX_ORDER = 1;

	private void computeResults(Path scenarioOutputDir) throws IOException {
		// open repository
		EccoService service = new EccoService();
		service.setRepositoryDir(scenarioOutputDir.resolve("repo"));
		service.open();
		System.out.println("Repository opened.");

		Map<String, Map<String, Boolean>> results = new HashMap<>();

		// for every association create results file with name of minimal to string
		Repository repository = service.getRepository();
		System.out.println("Max Order: " + ((Repository.Op) repository).getMaxOrder());
		Collection<? extends Association> associations = repository.getAssociations();
		int assocCounter = 0;
		for (Association association : associations) {
			assocCounter++;
			System.out.println("NUM_ARTIFACTS: " + Trees.countArtifacts(association.getRootNode()));

			Condition condition = association.computeCondition();
			System.out.println("TYPE: " + condition.getType());
			System.out.println("LONG: " + condition.getModuleConditionString());
			System.out.println("SHORT: " + condition.getSimpleModuleConditionString());

			// compute modules
			Collection<Module> modules = condition.getModules().keySet();
			int minOrder = modules.isEmpty() ? 0 : modules.stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
			Collection<Module> minModules = modules.stream().filter(module -> module.getOrder() <= minOrder).collect(Collectors.toList());

			if (NO_OR && condition.getType() == Condition.TYPE.OR && minModules.size() > 1)
				continue;

			// decide which modules to use
			Collection<Module> finalModules = null;
			if (USE_ONLY_MIN_ORDER) {
				finalModules = minModules;
			} else {
				if (condition.getType() == Condition.TYPE.OR) {
					// use min modules for OR traces still
					finalModules = minModules;
				} else if (condition.getType() == Condition.TYPE.AND) {
					// use modules up to including MAX_ORDER or the minimal order if no such module exists
					finalModules = modules.stream().filter(module -> module.getOrder() <= Math.max(MAX_ORDER, minOrder)).collect(Collectors.toList());
				}
			}

			// compute results
			StringBuilder sb = new StringBuilder();
			Map<String, Boolean> lines = new HashMap<>();
			this.computeString(association.getRootNode(), sb, lines, null);
			System.out.println(sb.toString());

			// loop over modules, create filename by: removing base feature, concatenating with "_and_" or "_or" (depending on type) and prefixing "not_" for negative modules
			for (Module module : finalModules) {
				List<String> names = new ArrayList<>();

				List<String> posNames = new ArrayList<>();
				for (Feature feature : module.getPos()) {
					if (!feature.getName().equals("BASE")) {
						names.add(feature.getName());
						posNames.add(feature.getName());
					}
				}
				List<String> negNames = new ArrayList<>();
				for (Feature feature : module.getNeg()) {
					if (!feature.getName().equals("BASE")) {
						names.add(feature.getName());
						negNames.add(feature.getName());
					}
				}

				// build file name
				String filename = names.stream().sorted().map(name -> {
					if (posNames.contains(name)) return name;
					else if (negNames.contains(name)) return "not_" + name;
					else return "";
				}).collect(Collectors.joining("_and_"));

				if (filename.isEmpty())
					continue;

				// write to file (per association/trace)
				Path resultsSplitDir = scenarioOutputDir.resolve("results_split");
				if (!Files.exists(resultsSplitDir))
					Files.createDirectory(resultsSplitDir);
				Path associationDir = resultsSplitDir.resolve("A" + assocCounter);
				if (!Files.exists(associationDir))
					Files.createDirectory(associationDir);
				Files.write(associationDir.resolve(filename + ".txt"), sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

				// add lines to results
				results.computeIfAbsent(filename, s -> new HashMap<>());
				Map<String, Boolean> filenameEntries = results.get(filename);
				for (Map.Entry<String, Boolean> entry : lines.entrySet()) {
					filenameEntries.compute(entry.getKey(), (k, v) -> {
						if (v == null)
							return entry.getValue();
						else
							return v | entry.getValue();
					});
				}
			}

			System.out.println("---------");
		}

		// write to file (for all associations/traces)
		Path resultsDir = scenarioOutputDir.resolve("results");
		if (!Files.exists(resultsDir))
			Files.createDirectory(resultsDir);
		for (Map.Entry<String, Map<String, Boolean>> entry : results.entrySet()) {
			List<String> resultLines = new ArrayList<>();
			entry.getValue().forEach((k, v) -> {
				if (v)
					resultLines.add(k);
				else
					resultLines.add(k + " Refinement");
			});
			Files.write(resultsDir.resolve(entry.getKey() + ".txt"), resultLines, StandardOpenOption.CREATE);
		}

		// close repository
		service.close();
		System.out.println("Repository closed.");
	}

	private boolean checkNonMethodDescendants(Node node) {
		// get the node data and see if it exists
		if (node.getArtifact() != null && node.getArtifact().getData() != null) {
			// see if the node is an import or variable declaration child
			//if (node.getArtifact().getData() instanceof ImportArtifactData || node.getArtifact().getData() instanceof FieldArtifactData) {
			//if (!(node.getArtifact().getData() instanceof MethodArtifactData)) {
			if (node.getArtifact().getData() instanceof ImportArtifactData || node.getArtifact().getData() instanceof FieldArtifactData || node.getArtifact().getData() instanceof LineArtifactData) {
				return true;
			}
		}

		boolean nonMethodDescendants = false;
		if (node.getArtifact() == null || node.getArtifact().getData() == null || !(node.getArtifact().getData() instanceof MethodArtifactData)) {
			for (Node childNode : node.getChildren()) {
				nonMethodDescendants = nonMethodDescendants | this.checkNonMethodDescendants(childNode);
			}
		}
		return nonMethodDescendants;
	}

	private void computeString(Node node, StringBuilder sb, Map<String, Boolean> lines, String currentClassName) {
		if (node.getArtifact() != null && node.getArtifact().getData() != null) {
			if (node.getArtifact().getData() instanceof ClassArtifactData) {
//				if (currentClassName != null)
//					throw new EccoException("Encounter class within class!");
				currentClassName = ((ClassArtifactData) node.getArtifact().getData()).getName();

				boolean nonMethodDescendants = this.checkNonMethodDescendants(node);

				if (lines.containsKey(currentClassName))
					throw new EccoException("Class already exists.");
				if (node.isUnique() && (!node.getParent().isUnique() || (node.getParent().getArtifact() != null && node.getArtifact().getData() != null && !(node.getParent().getArtifact().getData() instanceof ClassArtifactData)))) {
					sb.append(currentClassName + "\n");
					lines.put(currentClassName, true);
				} else if (!node.isUnique() && nonMethodDescendants) {
					sb.append(currentClassName + " Refinement\n");
					lines.put(currentClassName, false);
				}
			} else if (node.getArtifact().getData() instanceof MethodArtifactData) {
//				String fullMethodString = ((MethodArtifactData) node.getArtifact().getData()).getSignature().replaceAll(" ", "");
//				// get method name
//                String part1 = fullMethodString.substring(0, fullMethodString.indexOf("("));
//                String methodName = part1.substring(part1.indexOf(" ") + 1);
//                // extract params
//                String[] fullParams = fullMethodString.substring(fullMethodString.indexOf("(") + 1, fullMethodString.indexOf(")")).split(",");
//                String params = Arrays.stream(fullParams).map(fullParam -> {
//                    String[] tempParams = fullParam.split(" ");
//                    if (tempParams.length - 2 >= 0)
//                        return tempParams[tempParams.length - 2];
//                    else
//                        return "";
//                }).collect(Collectors.joining(","));
//				// build method signature
//				String methodSignature = fullMethodString;

				String methodSignature = ((MethodArtifactData) node.getArtifact().getData()).getSignature().replaceAll(", ", ",");
				String fullMethodSignature = currentClassName + " " + methodSignature;
				if (lines.containsKey(fullMethodSignature))
					throw new EccoException("Method already exists.");
				if (node.isUnique() && !node.getParent().isUnique()) {
					sb.append(fullMethodSignature + "\n");
					lines.put(fullMethodSignature, true);
				} else if (!node.isUnique() && !node.getChildren().isEmpty()) { // it has unique descendants
					sb.append(fullMethodSignature + " Refinement\n");
					lines.put(fullMethodSignature, false);
				}

//				MethodArtifactData method = ((MethodArtifactData) node.getArtifact().getData());
//				String methodSignature = method.getSignature().replaceAll("\\s+", "");
//
//				if (node.isUnique()) {
//					sb.append(currentClass + " " + methodSignature + "\n");
//				} else {
//					if (!node.getChildren().isEmpty()) { // it has unique descendants
//						sb.append(currentClass + " " + methodSignature + " Refinement\n");
//					}
//				}
			}
		}


		for (Node childNode : node.getChildren()) {
			this.computeString(childNode, sb, lines, currentClassName);
		}
	}


	@Test(groups = {"integration", "challenge"})
	public void Analyze_Differences() throws IOException {
		Path GT_PATH = BENCHMARK_DIR.resolve("groundTruth");
		//Path MY_PATH = BENCHMARK_DIR.resolve("yourResults");
		Path MY_PATH = SCENARIO_OUTPUT_DIR.resolve("results");

		Set<Path> myFiles = Files.list(MY_PATH).map(Path::getFileName).filter(path -> path.toString().endsWith(".txt")).collect(Collectors.toSet());
		Set<Path> groundTruthFiles = Files.list(GT_PATH).map(Path::getFileName).filter(path -> path.toString().endsWith(".txt")).collect(Collectors.toSet());

		Set<Path> commonFiles = new HashSet<>(groundTruthFiles);
		commonFiles.retainAll(myFiles);
		Set<Path> onlyMyFiles = new HashSet<>(myFiles);
		onlyMyFiles.removeAll(groundTruthFiles);
		Set<Path> onlyGTFiles = new HashSet<>(groundTruthFiles);
		onlyGTFiles.removeAll(myFiles);

		System.out.println("ONLY MY FILES:");
		onlyMyFiles.forEach(System.out::println);
		System.out.println("ONLY GT FILES:");
		onlyGTFiles.forEach(System.out::println);

		for (Path commonFile : commonFiles) {
			System.out.println("----------------------------------------");
			System.out.println("# FILE: " + commonFile);

			List<String> GTLines = Files.readAllLines(GT_PATH.resolve(commonFile));
			Set<String> GTEntries = new HashSet<>(GTLines);
			if (GTLines.size() != GTEntries.size())
				System.out.println("THERE ARE DUPLICATE LINES IN GT FILE " + commonFile);

			List<String> MyLines = Files.readAllLines(MY_PATH.resolve(commonFile));
			Set<String> MyEntries = new HashSet<>(MyLines);
			if (MyLines.size() != MyEntries.size())
				System.out.println("THERE ARE DUPLICATE LINES IN MY FILE " + commonFile);

			Set<String> onlyMyEntries = new HashSet<>(MyEntries);
			onlyMyEntries.removeAll(GTEntries);
			Set<String> onlyGTEntries = new HashSet<>(GTEntries);
			onlyGTEntries.removeAll(MyEntries);

			System.out.println("ENTRIES ONLY IN MY FILE:");
			onlyMyEntries.forEach(System.out::println);
			System.out.println("ENTRIES ONLY IN GT FILE:");
			onlyGTEntries.forEach(System.out::println);
		}
	}


}
