package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;

import java.io.Serializable;

public class JsonCommit implements Commit, Serializable {

    private int id;
    private Configuration configuration;

    public JsonCommit() {
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}
