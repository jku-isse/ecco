package at.jku.isse.ecco.test;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.commons.io.FileUtils;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Literal;
import org.logicng.io.parsers.ParserException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionToLogicTest {

    // Counter-representation of MIN:
    //  Module-Counter == Association-Counter
    //  -> For all ModuleRevision-Counters: they are equal to the count of the respective ModuleRevision (???)
    // ...  everytime the artifact appeared in a variant, the module(-revision) also appeared (association-counter would be bigger than module-counter otherwise)
    // ...  everytime the module(-revision) appeared in a variant, the artifact also appeared (the counter of the module would be different than the module-counter otherwise)
    //      (Rules 1 and 2)

    // Counter-representation of MAX:
    //  -> Module-Counter > 0
    //  -> For some ModuleRevision-Counter: its equal to the count of the respective ModuleRevision (???)
    // ...  the module appeared together with the artifact
    //      (Rules 4 and 5)

    // Counter-representation of NOT:
    // ...  All modules that did not appear when artifacts appeared are not existing as counters.
    //      So the modules appeared when the artifacts did not and vice versa
    //      (Rule 3)


    // there are unique counter-objects for every association
    // During slicing, intersections get add all counter-counts of the intersected associations


    // Counter-Increments Association-Counter:
    //  -> new association starts with 1
    // Counter-Increments Module-Counter:
    //  -> When the association is created, all module-counters are incremented (set to 1)
    // Counter-Increments Module-Revision-Counter:
    //  -> When the association is created, all module-revision-counters are incremented (set to 1)

    // Counter-Increments Counter of Module:
    //  -> everytime the module is in the powerset of a committed configuration
    // Counter-Increments Counter of ModuleRevision:
    //  -> everytime the module-revision is in the powerset of a committed configuration

    // Counter-representation of NOT:
    //  -> a counter is not present (is 0) in an association

    @BeforeEach
    public void cleanupService(){
        deleteDir(eccoServicePath);
        createDir(eccoServicePath);
    }

    Path eccoServicePath = getResourceFolderPath("service");

    @Test
    public void rule1Test() throws ParserException {
        // Rule 1: Common artifacts at least trace to common modules.

        // create service
        EccoService eccoService = createEccoService(eccoServicePath);

        // commit the variants
        Path variantAPath = getResourceFolderPath("variants/Variant_A");
        Path variantABPath = getResourceFolderPath("variants/Variant_AB");
        eccoService.setBaseDir(variantAPath.toAbsolutePath());
        eccoService.commit();
        eccoService.setBaseDir(variantABPath.toAbsolutePath());
        eccoService.commit();

        // create conditions
        Repository.Op repo = (Repository.Op) eccoService.getRepository();
        Collection<? extends Association.Op> associations = repo.getAssociations();

        // this association is an intersection containing artifacts tracing to FeatureA
        Association.Op association = associations.stream().filter(a -> a.getCounter().getCount() == 2).findFirst().get();

        FormulaFactory factory = new FormulaFactory();

        // create condition and condition formula
        Condition condition = association.computeCondition();
        Formula conditionFormula = factory.parse(condition.toLogicString());

        // create logical formula
        Collection<? extends Feature> features = repo.getFeatures();
        FeatureRevision featureRevisionBase = features.stream().filter(f -> f.getName().contains("BASE")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionA = features.stream().filter(f -> f.getName().contains("FEATUREA")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionB = features.stream().filter(f -> f.getName().contains("FEATUREB")).findFirst().get().getLatestRevision();
        Literal baseLiteral = factory.literal(featureRevisionBase.getLogicLiteralRepresentation(), true);
        Literal aLiteral = factory.literal(featureRevisionA.getLogicLiteralRepresentation(), true);
        Literal bLiteral = factory.literal(featureRevisionB.getLogicLiteralRepresentation(), true);

        // configuration: BASE
        // The module "BASE" was in both variants.
        Configuration configuration = eccoService.parseConfigurationString("BASE");
        Assignment assignment = new Assignment(baseLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));

        // configuration: A
        // The module "FEATUREA" was in both variants.
        configuration = eccoService.parseConfigurationString("FEATUREA");
        assignment = new Assignment(aLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));

        // configuration: BASE, A
        // The module "BASE, FEATUREA" was in both variants.
        configuration = eccoService.parseConfigurationString("BASE, FEATUREA");
        assignment = new Assignment(baseLiteral);
        assignment.addLiteral(aLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));

        // configuration: BASE, B
        // The module "BASE" was in both variants and is in the configuration.
        configuration = eccoService.parseConfigurationString("BASE, FEATUREB");
        assignment = new Assignment(baseLiteral);
        assignment.addLiteral(bLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));

        // configuration: BASE, A, B
        // The module "BASE" was in both variants and is in the configuration.
        configuration = eccoService.parseConfigurationString("BASE, FEATUREA, FEATUREB");
        assignment = new Assignment(baseLiteral);
        assignment.addLiteral(aLiteral);
        assignment.addLiteral(bLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));
    }

    @Test
    public void rule2Test() throws ParserException {
        // Rule 2: Artifacts in A and not B at least trace to modules in A and not B

        // create service
        EccoService eccoService = createEccoService(eccoServicePath);

        // commit the variants
        Path variantAPath = getResourceFolderPath("variants/Variant_A");
        Path variantABPath = getResourceFolderPath("variants/Variant_AB");
        eccoService.setBaseDir(variantAPath.toAbsolutePath());
        eccoService.commit();
        eccoService.setBaseDir(variantABPath.toAbsolutePath());
        eccoService.commit();

        // create conditions
        Repository.Op repo = (Repository.Op) eccoService.getRepository();
        Collection<? extends Association.Op> associations = repo.getAssociations();

        // this association is the rest of a slicing operation where artifacts tracing to FEATUREB are contained
        Association.Op association = associations.stream().filter(a -> a.getCounter().getCount() == 1).findFirst().get();

        FormulaFactory factory = new FormulaFactory();

        // create condition and condition formula
        Condition condition = association.computeCondition();
        Formula conditionFormula = factory.parse(condition.toLogicString());

        // create logical formula
        Collection<? extends Feature> features = repo.getFeatures();
        FeatureRevision featureRevisionBase = features.stream().filter(f -> f.getName().contains("BASE")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionA = features.stream().filter(f -> f.getName().contains("FEATUREA")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionB = features.stream().filter(f -> f.getName().contains("FEATUREB")).findFirst().get().getLatestRevision();
        Literal baseLiteral = factory.literal(featureRevisionBase.getLogicLiteralRepresentation(), true);
        Literal aLiteral = factory.literal(featureRevisionA.getLogicLiteralRepresentation(), true);
        Literal bLiteral = factory.literal(featureRevisionB.getLogicLiteralRepresentation(), true);

        // configuration: B
        // The module "FEATUREB" was in one variant, but not the other.
        Configuration configuration = eccoService.parseConfigurationString("FEATUREB");
        Assignment assignment = new Assignment(bLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));
    }

    @Test
    public void rule3Test() throws ParserException {
        // Rule 3: Artifacts in A and not B cannot trace to models in B and not A

        // create service
        EccoService eccoService = createEccoService(eccoServicePath);

        // commit the variants
        Path variantAPath = getResourceFolderPath("variants/Variant_A");
        Path variantABPath = getResourceFolderPath("variants/Variant_AB");
        eccoService.setBaseDir(variantAPath.toAbsolutePath());
        eccoService.commit();
        eccoService.setBaseDir(variantABPath.toAbsolutePath());
        eccoService.commit();

        // create conditions
        Repository.Op repo = (Repository.Op) eccoService.getRepository();
        Collection<? extends Association.Op> associations = repo.getAssociations();

        // this association is an intersection containing artifacts tracing to FeatureA
        Association.Op association = associations.stream().filter(a -> a.getCounter().getCount() == 2).findFirst().get();

        FormulaFactory factory = new FormulaFactory();

        // create condition and condition formula
        Condition condition = association.computeCondition();
        Formula conditionFormula = factory.parse(condition.toLogicString());

        // create logical formula
        Collection<? extends Feature> features = repo.getFeatures();
        FeatureRevision featureRevisionBase = features.stream().filter(f -> f.getName().contains("BASE")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionA = features.stream().filter(f -> f.getName().contains("FEATUREA")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionB = features.stream().filter(f -> f.getName().contains("FEATUREB")).findFirst().get().getLatestRevision();
        Literal baseLiteral = factory.literal(featureRevisionBase.getLogicLiteralRepresentation(), true);
        Literal aLiteral = factory.literal(featureRevisionA.getLogicLiteralRepresentation(), true);
        Literal bLiteral = factory.literal(featureRevisionB.getLogicLiteralRepresentation(), true);

        // configuration: B
        // The module "FEATUREB" was in one variant, but not the other.
        Configuration configuration = eccoService.parseConfigurationString("FEATUREB");
        Assignment assignment = new Assignment(bLiteral);
        assertFalse(condition.holds(configuration));
        assertFalse(conditionFormula.evaluate(assignment));
    }

    @Test
    public void rule4Test() throws ParserException {
        // Rule 4: Artifacts in A and not B can at most trace to modules in A

        // create service
        EccoService eccoService = createEccoService(eccoServicePath);

        // commit the variants
        Path variantAPath = getResourceFolderPath("variants/Baseless_Variant_AC");
        Path variantABPath = getResourceFolderPath("variants/Baseless_Variant_B");
        eccoService.setBaseDir(variantAPath.toAbsolutePath());
        eccoService.commit();
        eccoService.setBaseDir(variantABPath.toAbsolutePath());
        eccoService.commit();

        // create conditions
        Repository.Op repo = (Repository.Op) eccoService.getRepository();
        Collection<? extends Association.Op> associations = repo.getAssociations();

        // this association is an intersection containing artifacts tracing to FeatureA


        // there is only one assocation as the artifacts of the variants are the same
        Association.Op association = associations.iterator().next();

        FormulaFactory factory = new FormulaFactory();

        // create condition and condition formula
        Condition condition = association.computeCondition();
        Formula conditionFormula = factory.parse(condition.toLogicString());

        // create logical formula
        Collection<? extends Feature> features = repo.getFeatures();
        FeatureRevision featureRevisionA = features.stream().filter(f -> f.getName().contains("FEATUREA")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionB = features.stream().filter(f -> f.getName().contains("FEATUREB")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionC = features.stream().filter(f -> f.getName().contains("FEATUREC")).findFirst().get().getLatestRevision();
        Literal aLiteral = factory.literal(featureRevisionA.getLogicLiteralRepresentation(), true);
        Literal bLiteral = factory.literal(featureRevisionB.getLogicLiteralRepresentation(), true);
        Literal cLiteral = factory.literal(featureRevisionC.getLogicLiteralRepresentation(), true);

        // configuration: C
        // Feature C was in only one variant and there are no MIN-modules in the association
        Configuration configuration = eccoService.parseConfigurationString("FEATUREC");
        Assignment assignment = new Assignment(cLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));
    }

    @Test
    public void rule5Test() throws ParserException {
        // Rule 5: Artifacts in A and B at most trace to modules in A or B

        // create service
        EccoService eccoService = createEccoService(eccoServicePath);

        // commit the variants
        Path variantAPath = getResourceFolderPath("variants/Baseless_Variant_AC");
        Path variantABPath = getResourceFolderPath("variants/Baseless_Variant_B");
        eccoService.setBaseDir(variantAPath.toAbsolutePath());
        eccoService.commit();
        eccoService.setBaseDir(variantABPath.toAbsolutePath());
        eccoService.commit();

        // create conditions
        Repository.Op repo = (Repository.Op) eccoService.getRepository();
        Collection<? extends Association.Op> associations = repo.getAssociations();

        // this association is an intersection containing artifacts tracing to FeatureA


        // there is only one assocation as the artifacts of the variants are the same
        Association.Op association = associations.iterator().next();

        FormulaFactory factory = new FormulaFactory();

        // create condition and condition formula
        Condition condition = association.computeCondition();
        Formula conditionFormula = factory.parse(condition.toLogicString());

        // create logical formula
        Collection<? extends Feature> features = repo.getFeatures();
        FeatureRevision featureRevisionA = features.stream().filter(f -> f.getName().contains("FEATUREA")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionB = features.stream().filter(f -> f.getName().contains("FEATUREB")).findFirst().get().getLatestRevision();
        FeatureRevision featureRevisionC = features.stream().filter(f -> f.getName().contains("FEATUREC")).findFirst().get().getLatestRevision();
        Literal aLiteral = factory.literal(featureRevisionA.getLogicLiteralRepresentation(), true);
        Literal bLiteral = factory.literal(featureRevisionB.getLogicLiteralRepresentation(), true);
        Literal cLiteral = factory.literal(featureRevisionC.getLogicLiteralRepresentation(), true);

        // configuration: A
        // Feature A was in only one variant and there are no MIN-modules in the association
        Configuration configuration = eccoService.parseConfigurationString("FEATUREA");
        Assignment assignment = new Assignment(aLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));

        // configuration: B
        // Feature B was in only one variant and there are no MIN-modules in the association
        configuration = eccoService.parseConfigurationString("FEATUREB");
        assignment = new Assignment(bLiteral);
        assertTrue(condition.holds(configuration));
        assertTrue(conditionFormula.evaluate(assignment));
    }

    public static EccoService createEccoService(Path repositoryPath){
        EccoService eccoService = new EccoService();
        eccoService.setRepositoryDir(repositoryPath.resolve(".ecco").toAbsolutePath());
        eccoService.init();
        return eccoService;
    }

    public static String getResourceFolderPathAsString(String relativePath) {
        try {
            URI configURI = Objects.requireNonNull(ConditionToLogicTest.class.getClassLoader().getResource(relativePath)).toURI();
            return Paths.get(configURI).toString();
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    public static Path getResourceFolderPath(String relativePath){
        try {
            URI configURI = Objects.requireNonNull(ConditionToLogicTest.class.getClassLoader().getResource(relativePath)).toURI();
            return Paths.get(configURI);
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void createDir(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void deleteDir(Path path) {
        try {
            File dir = path.toFile();
            if (dir.exists()) FileUtils.deleteDirectory(dir);
        }
        catch (IOException e){
            throw new RuntimeException(String.format("Could not delete directory %s: ", path) + e.getMessage());
        }
    }
}
