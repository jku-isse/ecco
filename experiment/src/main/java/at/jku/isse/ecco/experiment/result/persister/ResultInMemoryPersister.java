package at.jku.isse.ecco.experiment.result.persister;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.Result;

import java.util.Collection;
import java.util.LinkedList;


public class ResultInMemoryPersister implements ResultPersister {


    private final Collection<Result> results;

    public ResultInMemoryPersister(){
        this.results = new LinkedList<>();
    }

    public Collection<Result> getResults(){
        return this.results;
    }

    @Override
    public void persist(Result result, ExperimentRunConfiguration config) {
        this.results.add(result);
    }
}
