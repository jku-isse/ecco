package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.EmptyModule;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The modules a configuration contains - computed by a commit (Repository.Op#addConfigurationModules,
 * which creates and counts them) and by a checkout (#getOrphanedConfigurationModules, which only
 * looks them up) - are the same as with the two copies of the enumeration they had until 2026-10-15
 * (formula review H8; the copies, verbatim, are the reference below): the same module revisions in
 * the same order, the same counts, the same ones real or temporary.
 */
public class ModuleEnumerationEquivalenceTest {

    private static final SerEntityFactory FACTORY = new SerEntityFactory();

    @Test
    @Timeout(120)
    public void commitAndCheckoutEnumerateAsTheTwoCopiesDid() {
        for (int seed = 0; seed < 300; seed++) {
            Random random = new Random(seed);
            int maxOrder = 1 + random.nextInt(4);
            int featureCount = 1 + random.nextInt(6);
            Repository.Op reference = repository(maxOrder), merged = repository(maxOrder);
            int commits = 1 + random.nextInt(6);
            for (int commit = 0; commit < commits; commit++) {
                Configuration configuration = configuration(random, featureCount, false);
                reference.addFeatureRevisions(configuration.getFeatureRevisions());
                merged.addFeatureRevisions(configuration.getFeatureRevisions());
                List<String> expected = describe(addConfigurationModules(reference, configuration), reference);
                List<String> actual = describe(merged.addConfigurationModules(configuration), merged);
                String where = "seed " + seed + ", max order " + maxOrder + ", commit " + commit + ": " + configuration;
                assertEquals(expected, actual, where);
                assertEquals(state(reference), state(merged), where + " (modules and counts)");
            }
            for (int checkout = 0; checkout < 8; checkout++) {
                Configuration configuration = configuration(random, featureCount + 1, true);
                List<String> expected = describe(getOrphanedConfigurationModules(reference, configuration), reference);
                List<String> actual = describe(merged.getOrphanedConfigurationModules(configuration), merged);
                String where = "seed " + seed + ", max order " + maxOrder + ", checkout " + checkout + ": " + configuration;
                assertEquals(expected, actual, where);
                assertEquals(state(reference), state(merged), where + " (a checkout changes nothing)");
            }
        }
    }

    private static Repository.Op repository(int maxOrder) {
        SerRepository repository = new SerRepository();
        repository.setMaxOrder(maxOrder);
        return repository;
    }

    /** Features F0..F[count-1], each in revision 1-3; for a checkout also revisions and features the repository lacks. */
    private static Configuration configuration(Random random, int count, boolean checkout) {
        List<FeatureRevision> revisions = new ArrayList<>();
        for (int f = 0; f < count; f++) {
            if (random.nextInt(3) == 0)
                continue;
            Feature feature = FACTORY.createFeature("id-F" + f, "F" + f);
            revisions.add(feature.addRevision(String.valueOf(1 + random.nextInt(checkout ? 4 : 3))));
        }
        if (revisions.isEmpty()) {
            Feature feature = FACTORY.createFeature("id-F0", "F0");
            revisions.add(feature.addRevision("1"));
        }
        return FACTORY.createConfiguration(revisions.toArray(new FeatureRevision[0]));
    }

    /** Each module revision: its features, and whether the repository holds it (or it is a temporary one). */
    private static List<String> describe(Collection<ModuleRevision> moduleRevisions, Repository.Op repository) {
        List<String> described = new ArrayList<>();
        for (ModuleRevision revision : moduleRevisions) {
            Feature[] pos = Arrays.stream(revision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);
            Module module = repository.getModule(pos, revision.getNeg());
            boolean held = module != null && module.getRevision(revision.getPos(), revision.getNeg()) == revision;
            described.add(text(revision) + (held ? "" : " (temporary)"));
        }
        return described;
    }

    /** Every module and module revision of [repository], in its order, with its count. */
    private static List<String> state(Repository.Op repository) {
        List<String> state = new ArrayList<>();
        for (int order = 0; order <= repository.getMaxOrder(); order++)
            for (Module module : repository.getModules(order)) {
                state.add(ids(module.getPos()) + " not " + ids(module.getNeg()) + " x" + module.getCount());
                for (ModuleRevision revision : module.getRevisions())
                    state.add("  " + text(revision) + " x" + revision.getCount());
            }
        return state;
    }

    private static String ids(Feature[] features) {
        return Arrays.stream(features).map(Feature::getId).collect(Collectors.joining(",", "[", "]"));
    }

    private static String text(ModuleRevision revision) {
        return Arrays.stream(revision.getPos()).map(r -> r.getFeature().getId() + "." + r.getId()).collect(Collectors.joining(",", "[", "]"))
                + " not " + ids(revision.getNeg());
    }

    // --- the reference: the two copies as they were (Repository.java, 2026-10-15), "this" as [repo] ---

    static Collection<ModuleRevision> addConfigurationModules(Repository.Op repo, Configuration configuration) {
        checkNotNull(configuration);

        // collect positive feature revisions
        Collection<FeatureRevision> pos = new ArrayList<>();
        for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
            // get feature from repository
            Feature repoFeature = repo.getFeature(featureRevision.getFeature().getId());
            if (repoFeature == null)
                throw new EccoException("ERROR: feature does not exist in repository: " + featureRevision.getFeature());
            // get feature revision from repository
            FeatureRevision repoFeatureRevision = repoFeature.getRevision(featureRevision.getId());
            if (repoFeatureRevision == null)
                throw new EccoException("ERROR: feature revision does not exist in repository: " + featureRevision);
            pos.add(repoFeatureRevision);
        }

        // collect negative features
        Collection<Feature> neg = new ArrayList<>();
        for (Feature repoFeature : repo.getFeatures()) {
            if (pos.stream().noneMatch(featureRevision -> featureRevision.getFeature().equals(repoFeature))) {
                neg.add(repoFeature);
            }
        }

        // collection of modules
        Collection<ModuleRevision> moduleRevisions = new ArrayList<>();
        Collection<ModuleRevision> finalModuleRevisions = new ArrayList<>();

        // add empty module initially
        Module emptyModule = new EmptyModule();
        ModuleRevision emptyModuleRevision = emptyModule.getRevision(new FeatureRevision[0], new Feature[0]);
        moduleRevisions.add(emptyModuleRevision); // add empty module revision to power set

        // compute powerset
        for (final FeatureRevision featureRevision : pos) {
            final Collection<ModuleRevision> toAdd = new ArrayList<>();

            for (final ModuleRevision moduleRevision : moduleRevisions) {
                if (moduleRevision.getOrder() < repo.getMaxOrder()) {
                    FeatureRevision[] posFeatureRevisions = Arrays.copyOf(moduleRevision.getPos(), moduleRevision.getPos().length + 1);
                    posFeatureRevisions[posFeatureRevisions.length - 1] = featureRevision;
                    Feature[] posFeatures = Arrays.stream(posFeatureRevisions).map(FeatureRevision::getFeature).toArray(Feature[]::new);

                    // get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
                    Module newModule = repo.getModule(posFeatures, moduleRevision.getNeg());
                    if (newModule == null) {
                        newModule = repo.addModule(posFeatures, moduleRevision.getNeg());
                    }
                    newModule.incCount();
                    ModuleRevision newModuleRevision = newModule.getRevision(posFeatureRevisions, moduleRevision.getNeg());
                    if (newModuleRevision == null) {
                        newModuleRevision = newModule.addRevision(posFeatureRevisions, moduleRevision.getNeg());
                    }
                    newModuleRevision.incCount();

                    if (newModuleRevision.getOrder() >= repo.getMaxOrder()) {
                        finalModuleRevisions.add(newModuleRevision); // TODO: ???
                    } else {
                        toAdd.add(newModuleRevision);
                    }
                }
            }

            moduleRevisions.addAll(toAdd);
        }

        // remove the empty module again
        moduleRevisions.remove(emptyModuleRevision);

        for (final Feature feature : neg) {
            final Collection<ModuleRevision> toAdd = new ArrayList<>();

            for (final ModuleRevision moduleRevision : moduleRevisions) {
                if (moduleRevision.getOrder() < repo.getMaxOrder() && moduleRevision.getPos().length > 0) {
                    Feature[] negFeatures = Arrays.copyOf(moduleRevision.getNeg(), moduleRevision.getNeg().length + 1);
                    negFeatures[negFeatures.length - 1] = feature;
                    Feature[] posFeatures = Arrays.stream(moduleRevision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);

                    // get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
                    Module newModule = repo.getModule(posFeatures, negFeatures);
                    if (newModule == null) {
                        newModule = repo.addModule(posFeatures, negFeatures);
                    }
                    newModule.incCount();
                    ModuleRevision newModuleRevision = newModule.getRevision(moduleRevision.getPos(), negFeatures);
                    if (newModuleRevision == null) {
                        newModuleRevision = newModule.addRevision(moduleRevision.getPos(), negFeatures);
                    }
                    newModuleRevision.incCount();

                    if (newModuleRevision.getOrder() >= repo.getMaxOrder()) {
                        finalModuleRevisions.add(newModuleRevision); // TODO: ???
                    } else {
                        toAdd.add(newModuleRevision);
                    }
                }
            }

            moduleRevisions.addAll(toAdd);
        }

        finalModuleRevisions.addAll(moduleRevisions);

        return finalModuleRevisions;
    }

    static Collection<ModuleRevision> getOrphanedConfigurationModules(Repository.Op repo, Configuration configuration) {
        checkNotNull(configuration);

        // collect positive feature revisions
        Collection<FeatureRevision> pos = new ArrayList<>();
        for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
            // get feature from repository
            Feature repoFeature = repo.getOrphanedFeature(featureRevision.getFeature().getId(), featureRevision.getFeature().getName());
            // get feature revision from repository
            FeatureRevision repoFeatureRevision = repoFeature.getOrphanedRevision(featureRevision.getId());
            pos.add(repoFeatureRevision);
        }

        // collect negative features
        Collection<Feature> neg = new ArrayList<>();
        for (Feature repoFeature : repo.getFeatures()) {
            if (pos.stream().noneMatch(featureRevision -> featureRevision.getFeature().equals(repoFeature))) {
                neg.add(repoFeature);
            }
        }

        // collection of modules
        Collection<ModuleRevision> moduleRevisions = new ArrayList<>();
        Collection<ModuleRevision> finalModuleRevisions = new ArrayList<>();

        // add empty module initially
        Module emptyModule = new EmptyModule();
        ModuleRevision emptyModuleRevision = emptyModule.getRevision(new FeatureRevision[0], new Feature[0]);
        moduleRevisions.add(emptyModuleRevision); // add empty module revision to power set

        // compute powerset
        for (final FeatureRevision featureRevision : pos) {
            final Collection<ModuleRevision> toAdd = new ArrayList<>();

            for (final ModuleRevision moduleRevision : moduleRevisions) {
                if (moduleRevision.getOrder() < repo.getMaxOrder()) {
                    FeatureRevision[] posFeatureRevisions = Arrays.copyOf(moduleRevision.getPos(), moduleRevision.getPos().length + 1);
                    posFeatureRevisions[posFeatureRevisions.length - 1] = featureRevision;
                    Feature[] posFeatures = Arrays.stream(posFeatureRevisions).map(FeatureRevision::getFeature).toArray(Feature[]::new);

                    // get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
                    Module newModule = repo.getOrphanedModule(posFeatures, moduleRevision.getNeg());
                    ModuleRevision newModuleRevision = newModule.getOrphanedRevision(posFeatureRevisions, moduleRevision.getNeg());

                    if (newModuleRevision.getOrder() >= repo.getMaxOrder()) {
                        finalModuleRevisions.add(newModuleRevision); // TODO: ???
                    } else {
                        toAdd.add(newModuleRevision);
                    }
                }
            }

            moduleRevisions.addAll(toAdd);
        }

        // remove the empty module again
        moduleRevisions.remove(emptyModuleRevision);

        for (final Feature feature : neg) {
            final Collection<ModuleRevision> toAdd = new ArrayList<>();

            for (final ModuleRevision moduleRevision : moduleRevisions) {
                if (moduleRevision.getOrder() < repo.getMaxOrder() && moduleRevision.getPos().length > 0) {
                    Feature[] negFeatures = Arrays.copyOf(moduleRevision.getNeg(), moduleRevision.getNeg().length + 1);
                    negFeatures[negFeatures.length - 1] = feature;
                    Feature[] posFeatures = Arrays.stream(moduleRevision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);

                    // get module revision from repository if it already exists, otherwise a new module revision is created and if necessary also a new module
                    Module newModule = repo.getOrphanedModule(posFeatures, negFeatures);
                    ModuleRevision newModuleRevision = newModule.getOrphanedRevision(moduleRevision.getPos(), negFeatures);

                    if (newModuleRevision.getOrder() >= repo.getMaxOrder()) {
                        finalModuleRevisions.add(newModuleRevision); // TODO: ???
                    } else {
                        toAdd.add(newModuleRevision);
                    }
                }
            }

            moduleRevisions.addAll(toAdd);
        }

        finalModuleRevisions.addAll(moduleRevisions);

        return finalModuleRevisions;
    }
}
