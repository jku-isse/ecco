package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.core.Commit;

import java.util.Date;

public class RestCommit {

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

    public Date getDate() {return  commit.getDate(); }

    public RestConfiguration getConfiguration() {
        return new RestConfiguration(commit.getConfiguration());
    }
}
