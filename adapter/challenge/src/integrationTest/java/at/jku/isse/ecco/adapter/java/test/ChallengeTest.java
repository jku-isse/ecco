package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.adapter.java.JavaChallengeReader;
import at.jku.isse.ecco.adapter.java.data.*;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class ChallengeTest {

	@Test(groups = {"integration", "java"})
	public void Java_Adapter_Test() {
		Path[] inputFiles = new Path[]{Paths.get("AbstractFilePersister.java")};

		JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\workspace\\argouml-app\\src\\org\\argouml\\persistence"), inputFiles);

		System.out.println(nodes);

		//Path source = Paths.get(FILE_PATH);
		//try {
		//	Files.walkFileTree(source, new MyFileVisitor());
		//} catch (IOException e) {
		//	e.printStackTrace();
		//}
	}


	private static final Path CHALLENGE_DIR = Paths.get("C:\\Users\\user\\Desktop\\eccotest\\challenge\\traditional2");
	private static final Path REPO_DIR = CHALLENGE_DIR.resolve("repo\\.ecco");
	private static final Path RESULTS_DIR = CHALLENGE_DIR.resolve("results");
	private static final Path TIME_FILE = CHALLENGE_DIR.resolve("time.txt");


	@Test(groups = {"integration", "challenge"})
	public void Test_Create_Repo() throws IOException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(REPO_DIR);
		service.init();
		System.out.println("Repository initialized.");

		// commit all existing variants to the new repository
		Path scenarioDir = Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\workspace\\ArgoUMLSPLBenchmark\\scenarios\\ScenarioPairWiseVariants");
		Path variantsDir = scenarioDir.resolve("variants");
		Path configsDir = scenarioDir.resolve("configs");

		List<Long> runtimes = new ArrayList<>();
		int counter = 0;
		Collection<Path> variantsDirs = Files.list(variantsDir).collect(Collectors.toList());
		for (Path variantDir : variantsDirs) {
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

		Files.write(TIME_FILE, runtimes.stream().map(Object::toString).collect(Collectors.toList()));
	}


	private static final boolean NO_OR = false;
	private static final boolean USE_ONLY_MIN_ORDER = false;
	private static final int MAX_ORDER = 1;


	@Test(groups = {"integration", "challenge"})
	public void Test_Compute_Results() throws IOException {

		// open repository
		EccoService service = new EccoService();
		service.setRepositoryDir(REPO_DIR);
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
				}).collect(Collectors.joining("_" + condition.getType().toString().toLowerCase() + "_"));

				// write to file (per association)
				Path resultsDir = RESULTS_DIR.resolve("A" + assocCounter);
				if (!Files.exists(resultsDir))
					Files.createDirectory(resultsDir);
				Files.write(resultsDir.resolve(filename + ".txt"), sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

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

		// write to file (for all)
		Path resultsAllDir = RESULTS_DIR.resolve("ALL");
		if (!Files.exists(resultsAllDir))
			Files.createDirectory(resultsAllDir);
		for (Map.Entry<String, Map<String, Boolean>> entry : results.entrySet()) {
			List<String> resultLines = new ArrayList<>();
			entry.getValue().forEach((k, v) -> {
				if (v)
					resultLines.add(k);
				else
					resultLines.add(k + " Refinement");
			});
			Files.write(resultsAllDir.resolve(entry.getKey() + ".txt"), resultLines, StandardOpenOption.CREATE);
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
			// if file (i.e. class)
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
			}
			// if method
			else if (node.getArtifact().getData() instanceof MethodArtifactData) {
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
	public void Test_Analyze_Differences() throws IOException {
		Path GT_PATH = Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\workspace\\ArgoUMLSPLBenchmark\\groundTruth");
		Path MY_PATH = Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\workspace\\ArgoUMLSPLBenchmark\\yourResults");

		Set<Path> myFiles = Files.list(MY_PATH).map(path -> path.getFileName()).filter(path -> path.toString().endsWith(".txt")).collect(Collectors.toSet());
		Set<Path> groundTruthFiles = Files.list(GT_PATH).map(path -> path.getFileName()).filter(path -> path.toString().endsWith(".txt")).collect(Collectors.toSet());

		Set<Path> commonFiles = new HashSet<>(groundTruthFiles);
		commonFiles.retainAll(myFiles);
		Set<Path> onlyMyFiles = new HashSet<>(myFiles);
		onlyMyFiles.removeAll(groundTruthFiles);
		Set<Path> onlyGTFiles = new HashSet<>(groundTruthFiles);
		onlyGTFiles.removeAll(myFiles);

		System.out.println("ONLY MY FILES:");
		onlyMyFiles.forEach(path -> System.out.println(path));
		System.out.println("ONLY GT FILES:");
		onlyGTFiles.forEach(path -> System.out.println(path));

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
			onlyMyEntries.forEach(s -> System.out.println(s));
			System.out.println("ENTRIES ONLY IN GT FILE:");
			onlyGTEntries.forEach(s -> System.out.println(s));
		}
	}


}
