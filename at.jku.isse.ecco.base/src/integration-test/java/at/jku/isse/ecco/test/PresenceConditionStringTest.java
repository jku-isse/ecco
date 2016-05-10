package at.jku.isse.ecco.test;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.PresenceCondition;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PresenceConditionStringTest {

	@Test(groups = {"integration", "base"})
	public void PresenceConditionString_Test() {
		EccoService eccoService = new EccoService();
		eccoService.setRepositoryDir(Paths.get("/home/user/Desktop/ecco/demo/repository/.ecco"));
		eccoService.init();
		PresenceCondition pc = eccoService.parsePresenceCondition("(test)");
		System.out.println("PC: " + pc);
		Association association = eccoService.getAssociations().iterator().next();
		association.setPresenceCondition(pc);
		eccoService.updateAssociation(association);
	}

	@Test(groups = {"integration", "base", "regex"})
	public void Regex_Test() {
		//Pattern pattern = Pattern.compile("\\(([a-zA-Z0-9]+)(\\.((\\{((\\+|\\-)?[0-9]+)(\\s*,\\s*((\\+|\\-)?[0-9]+))*\\})|((\\+|\\-)?[0-9]+))+)?(\\s*,\\s*([a-zA-Z0-9]+)(\\.((\\{((\\+|\\-)?[0-9]+)(,((\\+|\\-)?[0-9]+))*\\})|((\\+|\\-)?[0-9]+))+)?)*\\)(\\s*,\\s*\\([a-zA-Z0-9]+(\\.((\\{((\\+|\\-)?[0-9]+)(,((\\+|\\-)?[0-9]+))*\\})|((\\+|\\-)?[0-9]+))+)?(\\s*,\\s*[a-zA-Z0-9]+(\\.((\\{((\\+|\\-)?[0-9]+)(\\s*,\\s*((\\+|\\-)?[0-9]+))*\\})|((\\+|\\-)?[0-9]+))+)?)*\\))*");

		// modules
		Pattern modulePattern = Pattern.compile("\\(([^()]*)\\)");
		Matcher moduleMatcher = modulePattern.matcher("(A), (B, -C), (D.{1,+22, 333}, +E.1)");
		while (moduleMatcher.find()) {
			System.out.println("--------------");
			for (int i = 0; i < moduleMatcher.groupCount(); i++) {
				System.out.println("1 " + i + ": " + moduleMatcher.group(i));

				// module features
				Pattern moduleFeaturePattern = Pattern.compile("(\\+|\\-)?([a-zA-Z0-9]+)(.(\\{[+-]?[a-zA-Z0-9]+(\\s*,\\s*[+-]?[a-zA-Z0-9]+)*\\}|[+-]?[0-9]+))?");
				Matcher moduleFeatureMatcher = moduleFeaturePattern.matcher(moduleMatcher.group(i));
				while (moduleFeatureMatcher.find()) {
					for (int j = 0; j < moduleFeatureMatcher.groupCount(); j++) {
						System.out.println("2 " + j + ": " + moduleFeatureMatcher.group(j));
					}

					// matcher2.group(1) is the sign
					String featureName = moduleFeatureMatcher.group(2);

					// versions
					//Pattern pattern3 = Pattern.compile("\\{([0-9]+)(\\s*,\\s*([0-9]+))*\\}");
					Pattern versionPattern = Pattern.compile("[^+-0-9{}, ]*([+-]?[0-9]+)[^+-0-9{}, ]*");
					if (moduleFeatureMatcher.group(3) != null) {
						Matcher versionMatcher = versionPattern.matcher(moduleFeatureMatcher.group(4));
						while (versionMatcher.find()) {
							for (int k = 0; k < versionMatcher.groupCount(); k++) {
								System.out.println("3 " + k + ": " + versionMatcher.group(k));
							}

							int featureVersion = Integer.valueOf(versionMatcher.group(0));
						}
					}
				}
			}
		}


	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

}
