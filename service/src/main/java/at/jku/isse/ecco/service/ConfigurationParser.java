package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.ConfigurationText;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
     * Parses the given configuration string (see {@link ConfigurationText}) to create a configuration object.
     * The configuration object contains feature revision object instances of this repository in case they already exist, otherwise temporary feature and feature revision objects are created.
     *
     * @param configurationString The configuration string to parse.
     * @return The configuration object.
     */
    Configuration parseConfigurationString(String configurationString) {
        checkNotNull(configurationString);
        // spaces around the whole (" A, B ", a pasted line) are no part of it
        configurationString = configurationString.strip();
        List<ConfigurationText.Entry> entries;
        try {
            entries = ConfigurationText.parse(configurationString);
        } catch (IllegalArgumentException e) {
            throw new EccoException(e.getMessage());
        }

        if (entries.isEmpty()) {
            return owner.entityFactory.createConfiguration(new FeatureRevision[0]);
        }

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Repository.Op repository = owner.repositoryDao.load();

            Set<FeatureRevision> featureRevisions = new HashSet<>();
            // the features of this string not in the repository yet, by name: "X.1, X.2" names one
            // new feature twice, not two features named X
            Map<String, Feature> newFeatures = new HashMap<>();
            // a configuration has one revision per feature (a commit refused two, a checkout took both)
            Map<String, String> revisionByFeatureId = new HashMap<>();

            Set<ConfigurationText.Entry> mentioned = new HashSet<>();
            for (ConfigurationText.Entry entry : entries) {
                if (!mentioned.add(entry))
                    continue; // "A, A": the same once

                if (entry.revision() != null) { // use specified feature revision
                    String featureRevisionId = entry.revision();

                    Feature feature = getFeature(repository, entry, newFeatures);

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
                } else if (entry.newRevision()) { // create new feature revision for feature
                    Feature feature = getFeature(repository, entry, newFeatures);

                    FeatureRevision featureRevision = this.temporaryCopyOf(feature).addRevision(UUID.randomUUID().toString());
                    featureRevisions.add(featureRevision);
                } else { // use most recent feature revision of feature (or create a new one if none existed so far)
                    Feature feature = getFeature(repository, entry, newFeatures);

                    FeatureRevision featureRevision = feature.getLatestRevision();
                    if (featureRevision == null) {
                        featureRevision = this.temporaryCopyOf(feature).addRevision(UUID.randomUUID().toString());
                    }

                    featureRevisions.add(featureRevision);
                }
            }
            for (FeatureRevision featureRevision : featureRevisions) {
                String other = revisionByFeatureId.put(featureRevision.getFeature().getId(), featureRevision.getId());
                if (other != null)
                    throw new EccoException("Feature " + featureRevision.getFeature().getName() + " is selected twice, in different revisions: a configuration selects one revision of a feature.");
            }

            Configuration configuration = owner.entityFactory.createConfiguration(featureRevisions.toArray(new FeatureRevision[0]));
            configuration.setOriginalConfigString(configurationString);

            owner.transactionStrategy.end();

            return configuration;
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
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
     * ParseConfigurationPhantomRevisionTest. Committing adds the revision to the repository by id.
     */
    private Feature temporaryCopyOf(Feature feature) {
        return owner.entityFactory.createFeature(feature.getId(), feature.getName());
    }

    private Feature getFeature(Repository.Op repository, ConfigurationText.Entry entry, Map<String, Feature> newFeatures) {
        Feature feature;
        String featureName = entry.feature();
        if (entry.byId()) { // feature id
            feature = repository.getFeature(featureName);
            if (feature == null) {
                // an unknown id silently created a feature named after the id - see UnknownFeatureIdTest
                throw new EccoException("Feature id does not exist: " + featureName + ". Use the feature name to create a new feature.");
            }
            // the repository's feature, as for a name: a copy without its revisions made "[id]" and
            // "[id].<rev>" resolve to a new revision; callers copy it (temporaryCopyOf) before adding one
        } else { // feature name
            Collection<Feature> features = repository.getFeaturesByName(featureName);
            if (features.isEmpty()) {
                if (featureName.startsWith("-")) {
                    // "-B" read like "without B" (the Git import's feature suggestions write a removal
                    // so) and silently committed a new feature named "-B"
                    throw new EccoException("\"" + featureName + "\" is no feature: a configuration lists the selected features - to not select "
                            + featureName.substring(1) + ", leave it out.");
                }
                // a temporary feature object, the same for each mention in this configuration
                feature = newFeatures.computeIfAbsent(featureName, name -> owner.entityFactory.createFeature(UUID.randomUUID().toString(), name));
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

        List<ConfigurationText.Entry> entries;
        try {
            entries = ConfigurationText.parseRevisions(featureRevisionsString);
        } catch (IllegalArgumentException e) {
            throw new EccoException("Invalid feature revisions string provided.");
        }

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Collection<FeatureRevision> featureRevisions = new ArrayList<>();

            if (entries.isEmpty()) {
                owner.transactionStrategy.end();
                return featureRevisions;
            }

            Repository.Op repository = owner.repositoryDao.load();

            for (ConfigurationText.Entry entry : entries) {
                String featureName = entry.feature();
                String featureRevisionId = entry.revision();

                Feature feature;
                if (entry.byId()) { // id
                    feature = repository.getFeature(featureName);
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
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error parsing feature revisions string: " + featureRevisionsString, e);
        }
    }
}
