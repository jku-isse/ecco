package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collection;

@Component
public class RestRepository implements Serializable {
    private String Name = "Test";
    private final EccoService service;

    public RestRepository(EccoService service) {
        this.service = service;
        this.Name = service.getBaseDir().getFileName().toString();
    }


    public Collection<? extends Feature> getFeatures() {
        Repository r = service.getRepository();
        Collection<? extends Feature> Test = service.getRepository().getFeatures();
        return Test;
    }

    @JsonIgnore
    public Collection<? extends Commit> getCommits() {
        return service.getCommits();
    }



    public String getName() {
        return Name;
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





