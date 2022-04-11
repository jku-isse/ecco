package at.jku.isse.ecco.rest.classes;

public class RepoHeader {
    private final int rId;
    private final String name;
    public RepoHeader(int rId, String name) {
        this.rId = rId;
        this.name = name;
    }

    public int getRId() {
        return rId;
    }

    public String getName() {
        return name;
    }
}
