package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedList;


public class RestCommit {
    // HH, the hour of the day: hh, without AM or PM, showed 22:10 as 10:10; and a DateTimeFormatter,
    // as requests format dates at the same time and a SimpleDateFormat shared by them is not safe
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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

    public String getDate() {return  dateFormat.format(commit.getDate().toInstant().atZone(ZoneId.systemDefault())); }

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
