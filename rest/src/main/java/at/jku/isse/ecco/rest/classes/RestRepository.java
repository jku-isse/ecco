package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;

//@Component
public class RestRepository implements Serializable {
    private String name;
    private EccoService service;


    public RestRepository(EccoService service, String name) {
        this.name = name;
        this.service = service;
    }

    public Collection<? extends RestFeature> getFeatures() {
        Collection<RestFeature> features = new LinkedList<>();
        for (Feature f : service.getRepository().getFeatures()) {
            features.add(new RestFeature(f));
        }
        return features;
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





