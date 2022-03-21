package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.service.EccoService;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;

//@Component
public class RestRepository implements Serializable {
    private String name = "Test";
    private Collection<RestFeature> featureList =  new LinkedList<>();

    public RestRepository(String name, Collection<RestFeature> features) {
        this.name = name;
        this.featureList = features;
    }

    public Collection<? extends RestFeature> getFeatures(EccoService service) {
        return featureList;
    }




    public String getName() {
        return name;
    }

/*    public Collection<Commit> getCommits () {
        return service.getCommits();
    }*/

    //Commits

    //Varianten


}
    //private Collection<Association.Op> associations;
    //private ArrayList<Variant> variants;
    //private List<Map<MemModule, MemModule>> modules;
    //private Collection<Commit> commits;





