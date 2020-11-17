package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.feature.Feature;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BugzillaTest {

	private Path outputDir = Paths.get("reports/integrationTest/output");
	private Path repositoryDir = outputDir.resolve("repository");
	private Path inputDir = Paths.get("resources/integrationTest/input");

	private static final String[] FEATURE_ORDER = new String[]{"base", "Usestatuswhiteboard", "Letsubmitterchoosepriority", "Specificsearchallowempty", "Addproduct", "Addcomponent",
			"Addversion", "Simplebugworkflow", "Unconfirmedstate", "CommentonBugcreation", "CommentonAlltransitions", "CommentonchangeResolution",
			"Commentonduplicate", "Noresolveonopenblockers", "DuplicateormovebugstatusVerified", "DuplicateormovebugstatusClosed"};

	private static int indexOf(String fName, final String[] order) {
		for (int i = 0; i < order.length; i++) {
			if (order[i].equals(fName)) {
				return i;
			}
		}
		return -1;
	}

	private static Comparator<String> featureComparator = new Comparator<String>() {
		@Override
		public int compare(String o1, String o2) {
			int i1 = indexOf(o1, FEATURE_ORDER);
			int i2 = indexOf(o2, FEATURE_ORDER);
			if (i1 >= 0 && i2 >= 0) {
				return i1 - i2;
			}
			return 0;
		}
	};

	private static Comparator<List<String>> productComparator = new Comparator<List<String>>() {
		@Override
		public int compare(List<String> p1, List<String> p2) {
			p1.sort(featureComparator);
			p2.sort(featureComparator);
			Iterator<String> it1 = p1.iterator();
			Iterator<String> it2 = p2.iterator();
			while (it1.hasNext() && it2.hasNext()) {
				String f1 = it1.next();
				String f2 = it2.next();
				int fCompare = featureComparator.compare(f1, f2);
				if (fCompare != 0) {
					return fCompare;
				}
			}
			return 0;
		}
	};

	@Test(groups = {"integration", "base", "service", "bugzilla"})
	public void Bugzilla_Test() throws IOException {

		// create new repository
		EccoService service = new EccoService();
		service.setRepositoryDir(Paths.get("C:\\Users\\user\\Desktop\\TEST2\\repo\\.ecco"));
		service.init();
		//service.open();
		System.out.println("Repository initialized.");

		// commit all existing variants to the new repository
		File variantsDir = new File("C:\\Users\\user\\Desktop\\TEST2\\");
		for (File variant : variantsDir.listFiles()) {
			if (variant.isDirectory() && variant.getName().startsWith("Teclo-")) {
				File src = new File(variant, "src");
				service.setBaseDir(Paths.get(src.getCanonicalPath()));
				service.commit();
				System.out.println("Committed: " + variant.getName());
			}
		}

		Set<Feature> optionalFeatures = new HashSet<>();
		for (Feature f : service.getRepository().getFeatures()) {
			if (!f.getName().equals("base")) {
				optionalFeatures.add(f);
			}
		}

		// checkout all possible combinations of 2 features from the new repository
		Set<Set<Feature>> pairs = powerSet(optionalFeatures);
		pairs.removeIf((Set<Feature> set) -> set.size() != 2);

		List<List<String>> products = new LinkedList<>();
		for (Set<Feature> partialConfiguration : pairs) {
			List<String> p = new LinkedList<>();
			p.add("base");
			for (Feature f : partialConfiguration) {
				p.add(f.getName());
			}
			products.add(p);
		}
		products.sort(productComparator);

		System.out.println("Configurations:");
		int i = 1;
		for (List<String> p : products) {
			Set<String> configuration = new HashSet();
			for (String f : p) {
				configuration.add(f + ".1");
			}
			String pName = "Teclo-";
			for (String f : p) {
				pName += indexOf(f, FEATURE_ORDER);
				pName += "-";
			}
			for (String f : p) {
				pName += f;
				pName += "-";
			}
			pName = pName.substring(0, pName.length() - 1);

			service.setBaseDir(Paths.get("D:\\Work\\SCCH\\ECCO Selenium Tests\\ECCO output\\" + pName + "\\src"));
			Files.createDirectories(service.getBaseDir());

			String configurationString = configuration.toString();
			configurationString = configurationString.substring(1, configurationString.length() - 1);

			System.out.println(i + " / " + products.size());
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
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
