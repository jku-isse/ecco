package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Parses configuration strings ("A, B.2, C'") and feature revision strings against the repository -
 * extracted from EccoService (which delegates to it, synchronized) like VariantManager and
 * ConstraintService. Parsing never changes the repository: revisions that don't exist yet are
 * created on detached copies of their features.
 */
class ConfigurationParser {

    /** The length revision ids are truncated to for display (FeatureRevision#getFeatureRevisionString). */
    private static final int DISPLAYED_ID_LENGTH = 7;

    private final EccoService owner;

    ConfigurationParser(EccoService owner) {
        this.owner = owner;
    }

    /**
     * Parses the given configuration string (see {@link Configuration#CONFIGURATION_STRING_REGULAR_EXPRESSION} to create a configuration object.
     * The configuration object contains feature revision object instances of this repository in case they already exist, otherwise temporary feature and feature revision objects are created.
     *
     * @param configurationString The configuration string to parse.
     * @return The configuration object.
     */
    Configuration parseConfigurationString(String configurationString) {
        checkNotNull(configurationString);

        if (!configurationString.matches(Configuration.CONFIGURATION_STRING_REGULAR_EXPRESSION))
            throw new EccoException("Invalid configuration string provided: " + configurationString);

        if (configurationString.isEmpty()) {
            return owner.entityFactory.createConfiguration(new FeatureRevision[0]);
        }

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Repository.Op repository = owner.repositoryDao.load();

            Set<FeatureRevision> featureRevisions = new HashSet<>();

            String[] featureRevisionStrings = configurationString.split(",");
            for (String featureRevisionString : featureRevisionStrings) {
                featureRevisionString = featureRevisionString.trim();

                if (featureRevisionString.contains(".")) { // use specified feature revision
                    String[] pair = featureRevisionString.split("\\.");
                    String featureName = pair[0];
                    String featureRevisionId = pair[1];

                    Feature feature = getFeature(repository, featureName);

                    FeatureRevision featureRevision = feature.getRevision(featureRevisionId);
                    if (featureRevision == null) {
                        // FeatureRevision#getFeatureRevisionString() (used wherever a configuration is
                        // displayed, e.g. Configuration#toString()) truncates the id to 7 characters,
                        // git-short-hash style - not round-trippable through an exact getRevision()
                        // lookup. Resolve a truncated id against existing revisions the same way git
                        // resolves an abbreviated hash, before falling back to treating it as brand new.
                        featureRevision = findRevisionByIdPrefix(feature, featureRevisionId);
                    }
                    if (featureRevision == null) {
                        featureRevision = this.temporaryCopyOf(feature).addRevision(featureRevisionId);
                    }

                    featureRevisions.add(featureRevision);
                } else if (featureRevisionString.endsWith("'")) { // create new feature revision for feature
                    String featureName = featureRevisionString.substring(0, featureRevisionString.length() - 1);

                    Feature feature = getFeature(repository, featureName);

                    FeatureRevision featureRevision = this.temporaryCopyOf(feature).addRevision(UUID.randomUUID().toString());
                    featureRevisions.add(featureRevision);
                } else { // use most recent feature revision of feature (or create a new one if none existed so far)
                    String featureName = featureRevisionString;

                    Feature feature = getFeature(repository, featureName);

                    FeatureRevision featureRevision = feature.getLatestRevision();
                    if (featureRevision == null) {
                        featureRevision = this.temporaryCopyOf(feature).addRevision(UUID.randomUUID().toString());
                    }

                    featureRevisions.add(featureRevision);
                }
            }

            Configuration configuration = owner.entityFactory.createConfiguration(featureRevisions.toArray(new FeatureRevision[0]));
            configuration.setOriginalConfigString(configurationString);

            owner.transactionStrategy.end();

            return configuration;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error parsing configuration string: " + configurationString, e);
        }
    }

    /**
     * Finds the unique revision of {@code feature} whose full id starts with {@code idPrefix}, or
     * null if none or more than one match (an ambiguous prefix is treated the same as no match -
     * callers fall back to creating a new revision rather than guessing).
     * <p>
     * Only prefixes at least as long as a displayed id count: shorter ids are displayed in full (and
     * found exactly), and a short id like the "2" of "A.2" names a new revision - matched as a prefix
     * it hit a random revision id starting with "2" one time in 16, and the commit went to that
     * revision instead.
     */
    private FeatureRevision findRevisionByIdPrefix(Feature feature, String idPrefix) {
        if (idPrefix.length() < DISPLAYED_ID_LENGTH)
            return null;
        FeatureRevision match = null;
        for (FeatureRevision candidate : feature.getRevisions()) {
            if (candidate.getId().startsWith(idPrefix)) {
                if (match != null) {
                    return null;
                }
                match = candidate;
            }
        }
        return match;
    }

    /**
     * A detached copy of {@code feature} to hang a not-yet-committed revision on. getFeature() can
     * return the repository's live Feature, and adding the revision to that (as parsing used to)
     * persisted it with the next write even if it was never committed - see
     * ParseConfigurationPhantomRevisionTest. Committing adds the revision to the repository by id,
     * exactly as for features given by [id], which were always resolved to such a copy.
     */
    private Feature temporaryCopyOf(Feature feature) {
        return owner.entityFactory.createFeature(feature.getId(), feature.getName());
    }

    private Feature getFeature(Repository.Op repository, String featureName) {
        Feature feature;
        if (featureName.startsWith("[") && featureName.endsWith("]")) { // feature id
            featureName = featureName.substring(1, featureName.length() - 1);
            feature = repository.getFeature(featureName);
            if (feature == null) {
                //throw new EccoException("Feature id does not exist. Use feature name instead if you want to create a new feature.");
                // create temporary feature object
                feature = owner.entityFactory.createFeature(featureName, featureName);
            } else {
                feature = owner.entityFactory.createFeature(feature.getId(), feature.getName());
            }
        } else { // feature name
            Collection<Feature> features = repository.getFeaturesByName(featureName);
            if (features.isEmpty()) {
                //feature = this.addFeature(UUID.randomUUID().toString(), featureName);
                // create temporary feature object
                feature = owner.entityFactory.createFeature(UUID.randomUUID().toString(), featureName);
            } else if (features.size() == 1) {
                feature = features.iterator().next();
            } else {
                throw new EccoException("Feature name is not unique. Use feature id instead.");
            }
        }
        return feature;
    }


    Collection<FeatureRevision> parseFeatureRevisionsString(String featureRevisionsString) {
        if (featureRevisionsString == null)
            throw new EccoException("No feature revisions string provided.");

        if (!featureRevisionsString.matches("(((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+)(\\s*,\\s*((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+))*)?"))
            throw new EccoException("Invalid feature revisions string provided.");

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Collection<FeatureRevision> featureRevisions = new ArrayList<>();

            if (featureRevisionsString.isEmpty()) {
                owner.transactionStrategy.end();
                return featureRevisions;
            }

            Repository.Op repository = owner.repositoryDao.load();

            String[] featureRevisionsStrings = featureRevisionsString.split(",");
            for (String featureRevisionString : featureRevisionsStrings) {
                featureRevisionString = featureRevisionString.trim();

                String[] pair = featureRevisionString.split("\\.");
                String featureName = pair[0];
                String featureRevisionId = pair[1];

                Feature feature;
                if (featureName.startsWith("[") && featureName.endsWith("]")) { // id
                    feature = repository.getFeature(featureName.substring(1, featureName.length() - 1));
                    if (feature == null) {
                        throw new EccoException("Feature with id does not exist: " + featureName);
                    }
                } else { // name
                    Collection<Feature> features = repository.getFeaturesByName(featureName);
                    if (features.isEmpty()) {
                        throw new EccoException("Feature with name does not exist: " + featureName);
                    } else if (features.size() == 1) {
                        feature = features.iterator().next();
                    } else {
                        throw new EccoException("Feature name is not unique. Use feature id instead.");
                    }
                }

                FeatureRevision featureRevision = feature.getRevision(featureRevisionId);
                if (featureRevision == null) {
                    // the displayed, truncated id - see parseConfigurationString
                    featureRevision = findRevisionByIdPrefix(feature, featureRevisionId);
                }
                if (featureRevision != null) {
                    featureRevisions.add(featureRevision);
                } else {
                    throw new EccoException("Feature revision with id does not exist: " + featureRevisionId);
                }
            }

            owner.transactionStrategy.end();

            return featureRevisions;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error parsing feature revisions string: " + featureRevisionsString, e);
        }
    }
}
