package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Remote;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ServiceTest {

	private Path outputDir = Paths.get("C:\\Users\\user\\Desktop\\transfer\\output");
	private Path repositoryDir = outputDir.resolve("repository");
	private Path inputDir = Paths.get("C:\\Users\\user\\Desktop\\transfer\\input");


	@Test(groups = {"integration", "base", "service", "init"})
	public void Init_Test() throws EccoException, IOException {
		EccoService service = new EccoService(inputDir.resolve(Paths.get("V1")), repositoryDir.resolve(Paths.get(".ecco")));

		service.init();

		service.close();

		service.open();
	}

	@Test(groups = {"integration", "base", "service", "commit"})
	public void Commit_Test() throws EccoException, IOException {
		EccoService service = new EccoService(inputDir.resolve(Paths.get("V1")), repositoryDir.resolve(Paths.get(".ecco")));
		service.init();

		System.out.println("Commit 1:");
		service.commit();
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
		}

		System.out.println("Commit 2:");
		service.setBaseDir(inputDir.resolve(Paths.get("V2")));
		service.commit();
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
		}

		service.close();
	}


	@Test(groups = {"integration", "base", "service", "fork"})
	public void Fork_Test() throws IOException {
		// create parent repo
		EccoService parentService = new EccoService();
		parentService.setRepositoryDir(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		parentService.init();
		parentService.open();
		parentService.setBaseDir(inputDir.resolve(Paths.get("V1")));
		parentService.commit();
		System.out.println("OUTPUT1:");
		for (Association a : parentService.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
		}
		parentService.close();

		parentService.open();
		System.out.println("OUTPUT2:");
		for (Association a : parentService.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
		}
		parentService.close();

		// create child repo
		EccoService service = new EccoService();
		service.setRepositoryDir(outputDir.resolve(Paths.get("forked_repo/.ecco")));
		service.fork(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		System.out.println("OUTPUT3:");
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
		}
		service.close();
	}


	@Test(groups = {"integration", "base", "service", "pull"})
	public void Pull_Test() throws IOException {
		// create parent repo
		EccoService parentService = new EccoService();
		parentService.setRepositoryDir(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		parentService.init();

		// commit first variant to parent
		parentService.setBaseDir(inputDir.resolve(Paths.get("V1")));
		parentService.commit();
		parentService.close();

		// create child repo and fork it from parent
		EccoService service = new EccoService();
		service.setRepositoryDir(outputDir.resolve(Paths.get("forked_repo/.ecco")));
		service.fork(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		//service.init();
		//service.addRemote("origin", outputDir.resolve(Paths.get("parent_repo/.ecco")).toString());
		//service.pull("origin");
		System.out.println("OUTPUT0:");
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
		}
		service.close();

		System.out.println("---");

		// commit second variant to parent
		parentService = new EccoService();
		parentService.setRepositoryDir(outputDir.resolve(Paths.get("parent_repo/.ecco")));
		parentService.open();
		parentService.setBaseDir(inputDir.resolve(Paths.get("V2")));
		parentService.commit();
		System.out.println("OUTPUT1:");
		for (Association a : parentService.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
			a.getRootNode().print();
		}
		parentService.close();

		System.out.println("---");

		// pull changes from parent to child
		service = new EccoService();
		service.setRepositoryDir(outputDir.resolve(Paths.get("forked_repo/.ecco")));
		service.open();
		service.pull("origin");
		System.out.println("OUTPUT2:");
		for (Association a : service.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
			a.getRootNode().print();
		}
		service.close();
	}




	@Test(groups = {"integration", "base", "service", "pull"})
	public void Selective_Pull_Test() throws IOException { // TODO: make proper test here

		EccoService service2 = new EccoService();
		service2.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\ecco_pull_tests_3\\PPUSimpleClone\\.force_repo"));
		service2.init();

		service2.addRemote("blubb", "C:\\Users\\user\\Desktop\\ecco_pull_tests_3\\SimplePPU\\.force_repo", Remote.Type.LOCAL);
		System.out.println("REMOTE ADDED.");
		service2.pull("blubb", "SimplePPU__Alarm.-1");
		System.out.println("PULLED.");

		System.out.println("OUTPUT:");
		for (Association a : service2.getRepository().getAssociations()) {
			System.out.println("A(" + a.getRootNode().countArtifacts() + "): " + a.computeCondition().toString());
		}

		service2.close();
	}





	@Test(groups = {"integration", "base", "service", "bugzilla"})
	public void Bugzilla_Test() throws IOException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\ecco_bugzilla\\.ecco"));
		service.init();
		System.out.println("Repository initialized.");

		// commit all existing variants to the new repository
		String[] variants = new String[] { "Teclo-00-Base", "Teclo-01-Usestatuswhiteboard", "Teclo-02-Letsubmitterchoosepriority", "Teclo-03-Specificsearchallowempty", "Teclo-04-Addproduct", "Teclo-07-Simplebugworkflow" };
		for (String variant : variants) {
			service.setBaseDir(Paths.get("C:\\Users\\user\\_ISSE\\GitHub\\rramler\\teclo\\" + variant + "\\src"));
			service.commit();
			System.out.println("Committed: " + variant);
		}

		// checkout all possible combinations of features from the new repository
		String[] mandatoryFeatures = new String[] { "base.1" };
		String[] optionalFeatures = new String[] { "addproduct.1", "specificsearchallowempty.1", "letsubmitterchoosepriority.1", "usestatuswhiteboard.1", "simplebugworkflow.1" };
		Set<String> mandatoryFeaturesSet = new HashSet(Arrays.asList(mandatoryFeatures));
		Set<String> optionalFeaturesSet = new HashSet(Arrays.asList(optionalFeatures));
		Set<Set<String>> powerSet = powerSet(optionalFeaturesSet);
		System.out.println("Configurations:");
		int i = 0;
		for (Set<String> partialConfiguration : powerSet) {
			Set<String> configuration = new HashSet(mandatoryFeaturesSet);
			configuration.addAll(partialConfiguration);

			service.setBaseDir(Paths.get("C:\\Users\\user\\Desktop\\ecco_bugzilla\\V" + i + "\\src"));
			Files.createDirectories(service.getBaseDir());

			String configurationString = configuration.toString();
			configurationString = configurationString.substring(1, configurationString.length() - 1);
			System.out.println("Configuration: " + configurationString);

			service.checkout(configurationString);

			System.out.println("Checked out: " + configurationString);
			i++;
		}

		// close repository
		service.close();
		System.out.println("Repository closed.");

	}

	// https://stackoverflow.com/questions/1670862/obtaining-a-powerset-of-a-set-in-java
	public static <T> Set<Set<T>> powerSet(Set<T> originalSet) {
		Set<Set<T>> sets = new HashSet<Set<T>>();
		if (originalSet.isEmpty()) {
			sets.add(new HashSet<T>());
			return sets;
		}
		List<T> list = new ArrayList<T>(originalSet);
		T head = list.get(0);
		Set<T> rest = new HashSet<T>(list.subList(1, list.size()));
		for (Set<T> set : powerSet(rest)) {
			Set<T> newSet = new HashSet<T>();
			newSet.add(head);
			newSet.addAll(set);
			sets.add(newSet);
			sets.add(set);
		}
		return sets;
	}







	@BeforeTest(alwaysRun = true)
	public void beforeTest() throws IOException {
		System.out.println("BEFORE");

		// delete files and directories
		Files.deleteIfExists(this.repositoryDir.resolve(".ecco/.ignores"));
		Files.deleteIfExists(this.repositoryDir.resolve(".ecco/ecco.db"));
		Files.deleteIfExists(this.repositoryDir.resolve(".ecco"));

		Files.deleteIfExists(this.outputDir.resolve("parent_repo/.ecco/.ignores"));
		Files.deleteIfExists(this.outputDir.resolve("parent_repo/.ecco/ecco.db"));
		Files.deleteIfExists(this.outputDir.resolve("parent_repo/.ecco"));
		Files.deleteIfExists(this.outputDir.resolve("forked_repo/.ecco/.ignores"));
		Files.deleteIfExists(this.outputDir.resolve("forked_repo/.ecco/ecco.db"));
		Files.deleteIfExists(this.outputDir.resolve("forked_repo/.ecco"));

		// create directories
		Files.createDirectories(this.repositoryDir);
		Files.createDirectories(this.outputDir.resolve("parent_repo"));
		Files.createDirectories(this.outputDir.resolve("forked_repo"));
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
