package at.jku.isse.ecco.perst.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.dao.PerstAssociationDao;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.module.PerstPresenceCondition;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.plugin.CoreModule;
import at.jku.isse.ecco.plugin.PerstModule;
import at.jku.isse.ecco.plugin.artifact.file.FileModule;
import com.google.inject.*;
import com.google.inject.name.Names;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AssociationTest {

	@Inject
	private EntityFactory entityFactory;
	@Inject
	private PerstAssociationDao associationDao;


	@Test(groups = {"integration", "perst", "association"})
	public void Association_Test() throws EccoException {
		this.associationDao.init();

		System.out.println("EXISTING ASSOCS: ");
		Collection<Association> associations = this.associationDao.loadAllAssociations();
		for (Association a : associations) {
			System.out.println("FFF: " + a.getId());

			this.associationDao.save(a);
			//System.out.println("GGG: " + a.getId());
		}

		Association a1 = this.entityFactory.createAssociation();
		Association a2 = this.entityFactory.createAssociation();

		a1.addParent(a2);

		this.associationDao.save(a1);
		this.associationDao.save(a2);

		this.associationDao.close();
	}

	@Test(groups = {"integration", "perst", "association"})
	public void Association_Test_PC() throws EccoException {
		this.associationDao.init();

		Configuration configuration = this.parseConfigurationString("aaa");
		PresenceCondition presenceCondition = this.entityFactory.createPresenceCondition(configuration, 4);

		Association association = this.entityFactory.createAssociation();
		association.setPresenceCondition(presenceCondition);

		this.associationDao.save(association);
		System.out.println("FFF: " + association.getPresenceCondition());

		System.out.println("GGG: " + ((PerstPresenceCondition) association.getPresenceCondition()).getOid());

		System.out.println("EXISTING ASSOCS: ");
		for (Association a : this.associationDao.loadAllAssociations()) {
			System.out.println("FFF: " + a.getPresenceCondition());
		}
	}

	@Test(groups = {"integration", "perst", "association"})
	public void Association_Test_Name_Change() throws EccoException {
		this.associationDao.init();

		System.out.println("EXISTING ASSOCS: ");
		Collection<Association> associations = this.associationDao.loadAllAssociations();
		for (Association a : associations) {
			System.out.println("FFF: " + a.getId());

			this.associationDao.save(a);
			//System.out.println("GGG: " + a.getId());
		}

		Association a1 = this.entityFactory.createAssociation();
		Association a2 = this.entityFactory.createAssociation();

		a1.addParent(a2);

		a1.setName("name1");
		this.associationDao.save(a1);
		this.associationDao.save(a2);

		System.out.println("EXISTING ASSOCS: ");
		for (Association a : this.associationDao.loadAllAssociations()) {
			System.out.println("FFF: " + a.getName());
		}

		a1.setName("name2");
		this.associationDao.save(a1);

		System.out.println("EXISTING ASSOCS: ");
		for (Association a : this.associationDao.loadAllAssociations()) {
			System.out.println("FFF: " + a.getName());
		}

		this.associationDao.close();
		//this.associationDao.save(a2);

	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");

		// create modules
		final Module settingsModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(String.class).annotatedWith(Names.named("connectionString")).toInstance("data/ecco.db");
				bind(String.class).annotatedWith(Names.named("clientConnectionString")).toInstance("data/ecco.db");
				bind(String.class).annotatedWith(Names.named("serverConnectionString")).toInstance("data/ecco.db");
			}
		};
		List<Module> modules = new ArrayList<Module>();
		modules.addAll(Arrays.asList(new CoreModule(), new PerstModule(), new FileModule(), settingsModule));

		// create injector
		Injector injector = Guice.createInjector(modules);

		injector.injectMembers(this);
	}


	public Configuration parseConfigurationString(String value) throws EccoException {
		if (value == null || value.isEmpty()) throw new EccoException("No configuration string provided.");

		if (!value.matches("(\\+|\\-)?[a-zA-Z]+('?|(\\.([0-9])+)?)(|,[a-zA-Z]+('?|(\\.([0-9])+)?))"))
			throw new EccoException("Invalid configuration string provided.");

		Configuration configuration = this.entityFactory.createConfiguration();

		String[] featureInstanceStrings = value.split(",");
		for (String featureInstanceString : featureInstanceStrings) {
			if (featureInstanceString.contains(".")) {
				String[] pair = featureInstanceString.split("\\.");
				//String featureName = pair[0].replace("!", "").replace("+", "").replace("-", "");
				String featureName = pair[0];
				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
					featureName = featureName.substring(1);
				int version = Integer.parseInt(pair[1]);
				boolean featureSign = !(pair[0].startsWith("!") || pair[0].startsWith("-"));

				Feature feature = this.entityFactory.createFeature(featureName);
				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);

				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
			} else {
				String featureName = featureInstanceString;
				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
					featureName = featureName.substring(1);

				int version = -1; // TODO: how to deal with this? always use newest?
				boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

				Feature feature = this.entityFactory.createFeature(featureName);
				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);

				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
			}
		}

		return configuration;
	}


}
