package at.jku.isse.ecco.storage.ser;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.featuretrace.evaluation.*;
import at.jku.isse.ecco.maintree.building.AssociationMerger;
import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerCommitDao;
import at.jku.isse.ecco.storage.ser.dao.SerRemoteDao;
import at.jku.isse.ecco.storage.ser.dao.SerRepositoryDao;
import at.jku.isse.ecco.storage.ser.dao.SerTransactionStrategy;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveAdditionEvaluation;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveBasedEvaluation;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveSubtractionEvaluation;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerRetroactiveBasedEvaluation;
import at.jku.isse.ecco.storage.ser.maintree.SerAssociationMerger;
import at.jku.isse.ecco.storage.ser.maintree.SerBoostedAssociationMerger;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class SerModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(RepositoryDao.class).to(SerRepositoryDao.class);
		bind(CommitDao.class).to(SerCommitDao.class);
		bind(RemoteDao.class).to(SerRemoteDao.class);
		bind(EntityFactory.class).to(SerEntityFactory.class);
		bind(TransactionStrategy.class).to(SerTransactionStrategy.class);

		bind(EvaluationStrategy.class).annotatedWith(Names.named(ProactiveBasedEvaluation.STRATEGY_NAME)).to(SerProactiveBasedEvaluation.class);
		bind(EvaluationStrategy.class).annotatedWith(Names.named(RetroactiveBasedEvaluation.STRATEGY_NAME)).to(SerRetroactiveBasedEvaluation.class);
		bind(EvaluationStrategy.class).annotatedWith(Names.named(ProactiveAdditionEvaluation.STRATEGY_NAME)).to(SerProactiveAdditionEvaluation.class);
		bind(EvaluationStrategy.class).annotatedWith(Names.named(ProactiveSubtractionEvaluation.STRATEGY_NAME)).to(SerProactiveSubtractionEvaluation.class);

		bind(MainTreeBuildingStrategy.class).annotatedWith(Names.named(BoostedAssociationMerger.STRATEGY_NAME)).to(SerBoostedAssociationMerger.class);
		bind(MainTreeBuildingStrategy.class).annotatedWith(Names.named(AssociationMerger.STRATEGY_NAME)).to(SerAssociationMerger.class);
	}
}
