package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.LinkedList;


public class RestCommit {
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    private final Commit commit;

    public RestCommit(Commit commit) {
        this.commit = commit;
    }

    public String getId() {
        return commit.getId();
    }

    public String getCommitMessage() {return commit.getCommitMessage(); }

    public String getUsername() {
        return commit.getUsername();
    }

    public String getDate() {return  dateFormat.format(commit.getDate()); }

    public RestConfiguration getConfiguration() {
        return new RestConfiguration(commit.getConfiguration());
    }

    public Collection<? extends RestAssociation> getAssociations() {
        Collection<RestAssociation> associations = new LinkedList<>();
        for (Association a : commit.getAssociations()) {
            associations.add(new RestAssociation(a));
        }
        return associations;
    }
}
