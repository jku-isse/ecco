package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.adapter.java.JavaBlockReader;
import at.jku.isse.ecco.adapter.java.data.ClassArtifactData;
import at.jku.isse.ecco.adapter.java.data.FieldArtifactData;
import at.jku.isse.ecco.adapter.java.data.ImportArtifactData;
import at.jku.isse.ecco.adapter.java.data.MethodArtifactData;
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
		Path[] inputFiles = new Path[]{Paths.get("FigStateVertex.java")};

		JavaBlockReader reader = new JavaBlockReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(Paths.get("C:\\Users\\gabil\\Desktop"), inputFiles);

		System.out.println(nodes);

		//Path source = Paths.get(FILE_PATH);
		//try {
		//	Files.walkFileTree(source, new MyFileVisitor());
		//} catch (IOException e) {
		//	e.printStackTrace();
		//}
	}


	private static final Path CHALLENGE_DIR = Paths.get("C:\\Users\\user\\Desktop\\eccotest\\challenge\\v2");
	private static final Path REPO_DIR = CHALLENGE_DIR.resolve("repo\\.ecco");
	private static final Path RESULTS_DIR = CHALLENGE_DIR.resolve("results");
	;


	@Test(groups = {"integration", "challenge"})
	public void Test_Create_Repo() throws IOException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(REPO_DIR);
		service.init();
		System.out.println("Repository initialized.");

		// commit all existing variants to the new repository
		Path scenarioDir = Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\workspace\\ArgoUMLSPLBenchmark\\scenarios\\ScenarioTraditionalVariants");
		Path variantsDir = scenarioDir.resolve("variants");
		Path configsDir = scenarioDir.resolve("configs");

		int counter = 0;
		Collection<Path> variantsDirs = Files.list(variantsDir).collect(Collectors.toList());
		for (Path variantDir : variantsDirs) {
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
			if (counter >= 500)
				break;
		}

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

//    @Test(groups = {"integration", "challenge"})
//    public void Test_Compute_Results() throws IOException {
//
//        // open repository
//        EccoService service = new EccoService();
//        //service.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\splc_repo\\.ecco"));
//        service.setRepositoryDir(Paths.get("C:\\Users\\gabil\\Desktop\\SPLC\\repository2\\.ecco"));
//        service.open();
//        System.out.println("Repository opened.");
//
//        // for every association create results file with name of minimal to string
//        Repository repository = service.getRepository();
//        System.out.println("Max Order: " + ((Repository.Op) repository).getMaxOrder());
//        Collection<? extends Association> associations = repository.getAssociations();
//        int assocCounter = 0;
//        for (Association association : associations) {
//            assocCounter++;
//            System.out.println("NUM_ARTIFACTS: " + Trees.countArtifacts(association.getRootNode()));
//
//            Condition condition = association.computeCondition();
//            System.out.println("TYPE: " + condition.getType());
//            System.out.println("LONG: " + condition.getModuleConditionString());
//            System.out.println("SHORT: " + condition.getSimpleModuleConditionString());
//
//            // compute results
//            StringBuilder sb = new StringBuilder();
//            this.computeString(association.getRootNode(), sb, null);
//            System.out.println(sb.toString());
//
//            // write results to file
//            Collection<Module> modules = condition.getModules().keySet();
//            int minOrder = modules.isEmpty() ? 0 : modules.stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
//            Collection<Module> minModules = modules.stream().filter(module -> module.getOrder() <= minOrder).collect(Collectors.toList());
//            // loop over modules, create filename by: removing base feature, concatenating with "_and_" or "_or" (depending on type) and prefixing "not_" for negative modules
//            for (Module module : minModules) {
//                List<String> names = new ArrayList<>();
//
//                List<String> posNames = new ArrayList<>();
//                for (Feature feature : module.getPos()) {
//                    if (!feature.getName().equals("BASE")) {
//                        names.add(feature.getName());
//                        posNames.add(feature.getName());
//                    }
//                }
//                List<String> negNames = new ArrayList<>();
//                for (Feature feature : module.getNeg()) {
//                    if (!feature.getName().equals("BASE")) {
//                        names.add(feature.getName());
//                        negNames.add(feature.getName());
//                    }
//                }
//                // build file name
//                String filename = names.stream().sorted().map(name -> {
//                    if (posNames.contains(name)) return name;
//                    else if (negNames.contains(name)) return "not_" + name;
//                    else return "";
//                }).collect(Collectors.joining("_and_"));
//                // write to file
//                //Path resultsDir = Paths.get("C:\\Users\\user\\Desktop\\splc_repo\\results\\A" + assocCounter);
//                Path resultsDir = Paths.get("C:\\Users\\gabil\\Desktop\\SPLC\\results\\A" + assocCounter);
//                if (!Files.exists(resultsDir))
//                    Files.createDirectory(resultsDir);
//                Files.write(resultsDir.resolve(filename + ".txt"), sb.toString().getBytes(), StandardOpenOption.CREATE);
//            }
//
//
//            System.out.println("---------");
//        }
//
//        // close repository
//        service.close();
//        System.out.println("Repository closed.");
//
//    }
//
//    private boolean checkNonMethodDescendants(Node node) {
//
//        if (node.getArtifact() != null && node.getArtifact().getData() != null) {
//
//            if (node.getArtifact().getData() instanceof JavaTreeArtifactData) {
//                JavaTreeArtifactData jtad = ((JavaTreeArtifactData) node.getArtifact().getData());
//
//                // if import or variable declaration
//                if (jtad.getType() == JavaTreeArtifactData.NodeType.FIELD_DECLARATION || jtad.getType() == JavaTreeArtifactData.NodeType.SIMPLE_JUST_A_STRING) {
//                    return true;
//                }
//
//            }
//
//        }
//
//        boolean nonMethodDescendants = false;
//        for (Node childNode : node.getChildren()) {
//            nonMethodDescendants = nonMethodDescendants | this.checkNonMethodDescendants(childNode);
//        }
//        return nonMethodDescendants;
//    }
//
//    private void computeString(Node node, StringBuilder sb, String currentClass) {
//
//        if (node.getArtifact() != null && node.getArtifact().getData() != null) {
//
//            // if file (i.e. class)
//            if (node.getArtifact().getData() instanceof JavaFileArtifactData) {
//                if (currentClass != null)
//                    throw new EccoException("Encounter class within class!");
//                currentClass = ((JavaFileArtifactData) node.getArtifact().getData()).getClassName();
//
//                boolean nonMethodDescendants = this.checkNonMethodDescendants(node);
//
//                if (node.isUnique()) {
//                    sb.append(currentClass + "\n");
//                } else {
//                    if (nonMethodDescendants) {
//                        sb.append(currentClass + " Refinement\n");
//                    }
//                }
//            }
//            // if tree
//            else if (node.getArtifact().getData() instanceof JavaTreeArtifactData) {
//                JavaTreeArtifactData jtad = ((JavaTreeArtifactData) node.getArtifact().getData());
//
//                // if method
//                if (jtad.getType() == JavaTreeArtifactData.NodeType.METHOD_DECLARATION) {
//                    String fullMethodString = jtad.getDataAsString();
//                    //sb.append("AAA: " + fullMethodString);
//                    // get method name
//                    String part1 = fullMethodString.substring(0, fullMethodString.indexOf("("));
//                    String methodName = part1.substring(part1.indexOf(" ") + 1);
//                    // extract params
//                    String[] fullParams = fullMethodString.substring(fullMethodString.indexOf("(") + 1, fullMethodString.indexOf(")")).split(",");
//                    String params = Arrays.stream(fullParams).map(fullParam -> {
//                        String[] tempParams = fullParam.split(" ");
//                        if (tempParams.length - 2 >= 0)
//                            return tempParams[tempParams.length - 2];
//                        else
//                            return "";
//                    }).collect(Collectors.joining(","));
//                    // build method signature
//                    String methodSignature = methodName + "(" + params + ")";
//
//
//                    if (node.isUnique()) {
//                        sb.append(currentClass + " " + methodSignature + "\n");
//                    } else {
//                        if (!node.getChildren().isEmpty()) { // it has unique descendants
//                            sb.append(currentClass + " " + methodSignature + " Refinement\n");
//                        }
//                    }
//                }
//
//            }
//
//        }
//
//        for (Node childNode : node.getChildren()) {
//            this.computeString(childNode, sb, currentClass);
//        }
//
//    }


	@Test(groups = {"integration", "challenge"})
	public void Test_Compute_Results() throws IOException {

		// open repository
		EccoService service = new EccoService();
		service.setRepositoryDir(REPO_DIR);
		service.open();
		System.out.println("Repository opened.");

		Map<String, Set<String>> results = new HashMap<>();

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

			// compute results
			StringBuilder sb = new StringBuilder();
			List<String> lines = new ArrayList<>();
			this.computeString(association.getRootNode(), sb, lines, null);
			System.out.println(sb.toString());

			// write results to file
			Collection<Module> modules = condition.getModules().keySet();
			int minOrder = modules.isEmpty() ? 0 : modules.stream().min((m1, m2) -> m1.getOrder() - m2.getOrder()).get().getOrder();
			Collection<Module> minModules = modules.stream().filter(module -> module.getOrder() <= minOrder).collect(Collectors.toList());
			// loop over modules, create filename by: removing base feature, concatenating with "_and_" or "_or" (depending on type) and prefixing "not_" for negative modules
			for (Module module : minModules) {
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
				results.computeIfAbsent(filename, s -> new HashSet<>());
				results.get(filename).addAll(lines);
			}

			// write to file (for all)
			Path resultsAllDir = RESULTS_DIR.resolve("ALL");
			if (!Files.exists(resultsAllDir))
				Files.createDirectory(resultsAllDir);
			for (Map.Entry<String, Set<String>> entry : results.entrySet()) {
				Files.write(resultsAllDir.resolve(entry.getKey() + ".txt"), entry.getValue(), StandardOpenOption.CREATE);
			}

			System.out.println("---------");
		}

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

	private boolean checkNonMethodDescendants(Node node) {
		// get the node data and see if it exists
		if (node.getArtifact() != null && node.getArtifact().getData() != null) {
			// see if the node is an import or variable declaration child
			if (node.getArtifact().getData() instanceof ImportArtifactData || node.getArtifact().getData() instanceof FieldArtifactData) {
				return true;
			}
		}

		boolean nonMethodDescendants = false;
		for (Node childNode : node.getChildren()) {
			nonMethodDescendants = nonMethodDescendants | this.checkNonMethodDescendants(childNode);
		}
		return nonMethodDescendants;
	}

	private void computeString(Node node, StringBuilder sb, List<String> lines, String currentClassName) {

		if (node.getArtifact() != null && node.getArtifact().getData() != null) {
			// if file (i.e. class)
			if (node.getArtifact().getData() instanceof ClassArtifactData) {
				if (currentClassName != null)
					throw new EccoException("Encounter class within class!");
				currentClassName = ((ClassArtifactData) node.getArtifact().getData()).getName();

				boolean nonMethodDescendants = this.checkNonMethodDescendants(node);

				if (node.isUnique()) {
					sb.append(currentClassName + "\n");
					lines.add(currentClassName + "");
				} else if (nonMethodDescendants) {
					sb.append(currentClassName + " Refinement\n");
					lines.add(currentClassName + " Refinement");
				}
			}
			// if method
			else if (node.getArtifact().getData() instanceof MethodArtifactData) {
				String fullMethodString = ((MethodArtifactData) node.getArtifact().getData()).getSignature().replaceAll(" ", "");
				// get method name
                /*String part1 = fullMethodString.substring(0, fullMethodString.indexOf("("));
                String methodName = part1.substring(part1.indexOf(" ") + 1);
                // extract params
                String[] fullParams = fullMethodString.substring(fullMethodString.indexOf("(") + 1, fullMethodString.indexOf(")")).split(",");
                String params = Arrays.stream(fullParams).map(fullParam -> {
                    String[] tempParams = fullParam.split(" ");
                    if (tempParams.length - 2 >= 0)
                        return tempParams[tempParams.length - 2];
                    else
                        return "";
                }).collect(Collectors.joining(","));
                // build method signature
                */
				String methodSignature = fullMethodString;

				if (node.isUnique() && !node.getParent().isUnique()) {
					sb.append(currentClassName + " " + methodSignature + "\n");
					lines.add(currentClassName + " " + methodSignature + "");
				} else if (!node.isUnique() && !node.getChildren().isEmpty()) { // it has unique descendants
					sb.append(currentClassName + " " + methodSignature + " Refinement\n");
					lines.add(currentClassName + " " + methodSignature + " Refinement");
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

}
