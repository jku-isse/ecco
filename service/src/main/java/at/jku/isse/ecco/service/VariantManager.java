package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.core.SerVariant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Owns variant CRUD (add/remove/update) and per-variant feature-revision edits for one
 * {@link EccoService} instance. Shares the owner's {@code repositoryDao}/{@code transactionStrategy}
 * (package-visible on {@link EccoService}) rather than getting its own Guice-injected copies, since
 * {@code SerRepositoryDao} is not bound as a singleton and a second instance would not necessarily
 * share state with the owner's.
 */
public class VariantManager {

    private final EccoService owner;

    public VariantManager(EccoService owner) {
        this.owner = owner;
    }

    public void addVariant(Configuration configuration, String name, String description) {
        owner.checkInitialized();

        checkNotNull(configuration);

        owner.listeners.setWriteInProgress(true);
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = owner.repositoryDao.load();
            ArrayList<Variant> variants = repository.getVariants();

            //storing new variant
            boolean hasConfigurarion = false;
            for (Variant v : variants) {
                if (v.getConfiguration().equals(configuration)) {
                    hasConfigurarion = true;
                }
            }
            if (!hasConfigurarion) {
                SerVariant memVariant = new SerVariant(name, configuration, UUID.randomUUID().toString());
                memVariant.setDescription(description);
                repository.addVariant(memVariant);
            }
            //

            owner.repositoryDao.store(repository);

            owner.transactionStrategy.end();

            owner.listeners.fireStatusChangedEvent();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during adding a variant.", e);
        } finally {
            owner.listeners.setWriteInProgress(false);
        }
    }

    public void removeVariant(String id) {
        owner.checkInitialized();
        checkNotNull(id);
        safeTransaction(repository -> removeVariantById(repository, id));
    }

    public void removeVariant(Configuration configuration) {
        owner.checkInitialized();
        checkNotNull(configuration);
        safeTransaction(repository -> removeVariantByConfiguration(repository, configuration));
    }

    private Repository.Op removeVariantById(Repository.Op repository, String id) {
        Variant variant = repository.getVariant(id);

        if (variant != null) {
            repository.removeVariant(variant);
        }

        return repository;
    }

    private Repository.Op removeVariantByConfiguration(Repository.Op repository, Configuration configuration) {
        Variant variant = repository.getVariant(configuration);

        if (variant != null) {
            repository.removeVariant(variant);
        }

        return repository;
    }

    private void safeTransaction(Function<Repository.Op, Repository.Op> transaction) {
        owner.listeners.setWriteInProgress(true);
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            Repository.Op repository = owner.repositoryDao.load();

            repository = transaction.apply(repository);

            owner.repositoryDao.store(repository);
            owner.transactionStrategy.end();

            owner.listeners.fireStatusChangedEvent();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during repository write transaction.", e);
        } finally {
            owner.listeners.setWriteInProgress(false);
        }
    }

    public void updateVariant(Configuration configuration, String name, String id) {
        owner.checkInitialized();

        checkNotNull(configuration);

        owner.listeners.setWriteInProgress(true);
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = owner.repositoryDao.load();
            Variant variant = repository.getVariant(id);
            if (variant != null) {
                repository.updateVariant(variant, configuration, name);
            }
            //

            owner.repositoryDao.store(repository);

            owner.transactionStrategy.end();

            owner.listeners.fireStatusChangedEvent();

        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during adding a variant.", e);
        } finally {
            owner.listeners.setWriteInProgress(false);
        }
    }

    public void updateFeatureRevision(FeatureRevision featureRevision, String featureRevisionUpdate, String id) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = owner.repositoryDao.load();

            Variant variant = owner.getRepository().getVariant(id);
            FeatureRevision[] featureRevisions = variant.getConfiguration().getFeatureRevisions();
            FeatureRevision[] newFeatureRevisions = new FeatureRevision[featureRevisions.length];
            int count = 0;
            Collection<? extends Feature> features = owner.getRepository().getFeatures();
            FeatureRevision featureRevisionToUpdate = null;
            for (Feature feature : features) {
                if (feature.getName().equals(featureRevisionUpdate.substring(0, featureRevisionUpdate.indexOf(".")))) {
                    for (FeatureRevision revision : feature.getRevisions()) {
                        if (revision.getFeatureRevisionString().equals(featureRevisionUpdate)) {
                            featureRevisionToUpdate = revision;
                        }
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (FeatureRevision fr : featureRevisions) {
                if (fr.equals(featureRevision) && featureRevisionToUpdate != null) {
                    newFeatureRevisions[count] = featureRevisionToUpdate;
                    sb.append(",").append(featureRevisionUpdate);
                } else {
                    newFeatureRevisions[count] = fr;
                    sb.append(",").append(fr);
                }
                count++;
            }
            String config = sb.length() > 0 ? sb.substring(1) : ""; // remove first ','
            variant.getConfiguration().setFeatureRevisions(newFeatureRevisions);
            Configuration newConfiguration = owner.parseConfigurationString(config);
            variant.setConfiguration(newConfiguration);

            owner.repositoryDao.store(repository);
            owner.transactionStrategy.end();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();
            throw new EccoException("Error during adding a variant.", e);
        }
    }

    public void removeFeatureRevision(FeatureRevision featureRevision, String id) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = owner.repositoryDao.load();

            Variant variant = owner.getRepository().getVariant(id);
            FeatureRevision[] featureRevisions = variant.getConfiguration().getFeatureRevisions();
            FeatureRevision[] newFeatureRevisions = new FeatureRevision[featureRevisions.length];
            int count = 0;
            StringBuilder sb = new StringBuilder();
            for (FeatureRevision fr : featureRevisions) {
                if (!fr.equals(featureRevision)) {
                    newFeatureRevisions[count] = fr;
                    sb.append(",").append(fr);
                }
                count++;
            }
            String config = sb.length() > 0 ? sb.substring(1) : ""; // remove first ','
            variant.getConfiguration().setFeatureRevisions(newFeatureRevisions);
            Configuration newConfiguration = owner.parseConfigurationString(config);
            variant.setConfiguration(newConfiguration);

            owner.repositoryDao.store(repository);

            owner.transactionStrategy.end();

        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during adding a variant.", e);
        }
    }

}
