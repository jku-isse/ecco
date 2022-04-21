package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.core.Commit;

import java.text.DateFormat;
import java.text.SimpleDateFormat;


public class RestCommit {

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");

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

    public String getDate() {return  dateFormat.format(commit.getDate()); } // maybe outsource to client if number can be parsed?

    public RestConfiguration getConfiguration() {
        return new RestConfiguration(commit.getConfiguration());
    }
}
