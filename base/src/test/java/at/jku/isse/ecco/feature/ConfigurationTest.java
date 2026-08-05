package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configuration.computeModules()/computeModulesMissing() are dead code (grepped: never called
 * outside Configuration.java itself) and are deliberately not tested here.
 */
public class ConfigurationTest {

    private final EntityFactory ef = new SerEntityFactory();
    private final Repository.Op repository = repository();

    private Repository.Op repository() {
        Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(2);
        return repository;
    }

    private FeatureRevision revisionOf(String featureName) {
        Feature feature = repository.addFeature(UUID.randomUUID().toString(), featureName);
        return feature.addRevision(UUID.randomUUID().toString());
    }

    @Test
    public void containsModuleTrueWhenAllPositiveFeaturesAreSelected() {
        FeatureRevision revisionA = revisionOf("A");
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revisionA});
        Module module = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[0]);

        assertTrue(configuration.contains(module));
    }

    @Test
    public void containsModuleFalseWhenAPositiveFeatureIsNotSelected() {
        FeatureRevision revisionA = revisionOf("A");
        FeatureRevision revisionB = revisionOf("B");
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revisionA});
        Module module = repository.addModule(new Feature[]{revisionA.getFeature(), revisionB.getFeature()}, new Feature[0]);

        assertFalse(configuration.contains(module));
    }

    @Test
    public void containsModuleFalseWhenANegativeFeatureIsSelected() {
        // SerModule requires at least one positive feature (SerModule.java:32), so this needs a
        // second, satisfied positive feature alongside the negative one to isolate the negative-
        // feature check from the (separately tested) positive-feature check.
        FeatureRevision revisionA = revisionOf("A");
        FeatureRevision revisionC = revisionOf("C");
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revisionA, revisionC});
        Module module = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[]{revisionC.getFeature()});

        assertFalse(configuration.contains(module));
    }

    @Test
    public void containsModuleTrueWhenANegativeFeatureIsNotSelected() {
        FeatureRevision revisionA = revisionOf("A");
        FeatureRevision revisionC = revisionOf("C");
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revisionA});
        Module module = repository.addModule(new Feature[]{revisionA.getFeature()}, new Feature[]{revisionC.getFeature()});

        assertTrue(configuration.contains(module));
    }

    @Test
    public void containsModuleRevisionRequiresTheExactRevisionNotJustTheFeature() {
        FeatureRevision revisionA1 = revisionOf("A");
        Feature featureA = revisionA1.getFeature();
        FeatureRevision revisionA2 = featureA.addRevision(UUID.randomUUID().toString());
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revisionA1});
        Module module = repository.addModule(new Feature[]{featureA}, new Feature[0]);
        ModuleRevision moduleRevisionOfA1 = module.addRevision(new FeatureRevision[]{revisionA1}, new Feature[0]);
        ModuleRevision moduleRevisionOfA2 = module.addRevision(new FeatureRevision[]{revisionA2}, new Feature[0]);

        assertTrue(configuration.contains(moduleRevisionOfA1));
        assertFalse(configuration.contains(moduleRevisionOfA2), "the configuration selects revision A1, not A2, even though both belong to feature A");
    }

    @Test
    public void getConfigurationStringJoinsFeatureRevisionStrings() {
        FeatureRevision revisionA = revisionOf("A");
        FeatureRevision revisionB = revisionOf("B");
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revisionA, revisionB});

        String configurationString = configuration.getConfigurationString();

        assertTrue(configurationString.contains(revisionA.toString()));
        assertTrue(configurationString.contains(revisionB.toString()));
        assertTrue(configurationString.contains(", "));
    }

    @Test
    public void toAssignmentProducesOnePositiveLiteralPerFeatureRevision() {
        FeatureRevision revisionA = revisionOf("A");
        Configuration configuration = ef.createConfiguration(new FeatureRevision[]{revisionA});

        var assignment = configuration.toAssignment();

        assertEquals(1, assignment.positiveVariables().size());
        // toAssignment() replaces '.' and '-' with '_' in "name.id" (Configuration.java:175-177) so the
        // literal is a valid LogicNG identifier - UUIDs contain '-', so this is exercising real input,
        // not an edge case that happens not to occur in practice.
        String expectedLiteral = (revisionA.getFeature().getName() + "." + revisionA.getId()).replace(".", "_").replace("-", "_");
        assertEquals(expectedLiteral, assignment.positiveVariables().get(0).name());
    }
}
