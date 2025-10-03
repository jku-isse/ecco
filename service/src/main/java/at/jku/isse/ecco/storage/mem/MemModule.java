package at.jku.isse.ecco.storage.mem;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.featuretrace.evaluation.*;
import at.jku.isse.ecco.maintree.building.AssociationMerger;
import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.storage.mem.dao.*;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveAdditionEvaluation;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveBasedEvaluation;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerProactiveSubtractionEvaluation;
import at.jku.isse.ecco.storage.ser.featuretrace.evaluation.SerRetroactiveBasedEvaluation;
import at.jku.isse.ecco.storage.ser.maintree.SerAssociationMerger;
import at.jku.isse.ecco.storage.ser.maintree.SerBoostedAssociationMerger;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class MemModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(RepositoryDao.class).to(MemRepositoryDao.class);
		bind(CommitDao.class).to(MemCommitDao.class);
		bind(RemoteDao.class).to(MemRemoteDao.class);
		bind(TransactionStrategy.class).to(MemTransactionStrategy.class);

		// only differences are implementations of Persistable / Serializable
		bind(EntityFactory.class).to(SerEntityFactory.class);
		bind(EvaluationStrategy.class).annotatedWith(Names.named(ProactiveBasedEvaluation.STRATEGY_NAME)).to(SerProactiveBasedEvaluation.class);
		bind(EvaluationStrategy.class).annotatedWith(Names.named(RetroactiveBasedEvaluation.STRATEGY_NAME)).to(SerRetroactiveBasedEvaluation.class);
		bind(EvaluationStrategy.class).annotatedWith(Names.named(ProactiveAdditionEvaluation.STRATEGY_NAME)).to(SerProactiveAdditionEvaluation.class);
		bind(EvaluationStrategy.class).annotatedWith(Names.named(ProactiveSubtractionEvaluation.STRATEGY_NAME)).to(SerProactiveSubtractionEvaluation.class);
		bind(MainTreeBuildingStrategy.class).annotatedWith(Names.named(BoostedAssociationMerger.STRATEGY_NAME)).to(SerBoostedAssociationMerger.class);
		bind(MainTreeBuildingStrategy.class).annotatedWith(Names.named(AssociationMerger.STRATEGY_NAME)).to(SerAssociationMerger.class);

		//requireBinding(Key.get(String.class, Names.named("connectionString")));
		//requireBinding(Key.get(String.class, Names.named("clientConnectionString")));
		//requireBinding(Key.get(String.class, Names.named("serverConnectionString")));
	}
}
