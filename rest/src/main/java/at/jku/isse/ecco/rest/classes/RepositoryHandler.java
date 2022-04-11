package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.service.EccoService;

import java.nio.file.Path;

public class RepositoryHandler {
    private final String name;
    private final Path path;
    private EccoService service;

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public RepositoryHandler(Path path) {
        name = path.getFileName().toString();
        this.path = path;
    }

    public boolean isInitialized () {
        return service != null;
    }

    public RestRepository getRepository() {
        if(!isInitialized()) {
            service = new EccoService();
            service.setRepositoryDir(path.resolve(".ecco"));
            service.setBaseDir(path);
            service.open();
        }
        return new RestRepository(service, name);
    }

}
