package at.jku.isse.ecco.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Settings {
    /** Environment variable naming the directory the server keeps its repositories in. */
    public static final String STORAGE_DIR_ENV = "ECCO_STORAGE_DIR";
    /** System property naming that directory; wins over the environment variable. */
    public static final String STORAGE_DIR_PROPERTY = "ecco.storage-dir";

    public static final String STORAGE_LOCATION_OF_REPOSITORIES = storageLocation(
            System.getProperty(STORAGE_DIR_PROPERTY), System.getenv(STORAGE_DIR_ENV));
    private static final String EXAMPLES  = "examples";
    private static final String USER_DIR = "user.dir";
    private static final String JENKINS = "jenkins";
    private static final String USERNAME = "user.name";
    private static final String REST = "rest";
    private static final String JENKINS_PATH = "/home/jenkins/host";


    /**
     * The configured directory (system property, then environment variable) or, when neither is
     * set, the directory guessed from where the server runs (Docker, a Jenkins user, else the
     * examples folder of the source tree).
     */
    static String storageLocation(String property, String environment) {
        String configured = property != null && !property.isBlank() ? property
                : environment != null && !environment.isBlank() ? environment : null;
        String location = configured != null
                ? Path.of(configured.trim()).toAbsolutePath().normalize().toString()
                : getAutoLocation();
        System.out.println("Repositories are stored in " + location
                + (configured != null ? "" : " (set " + STORAGE_DIR_ENV + " to choose the directory)"));
        return location;
    }

    private static String getAutoLocation() {
        if(isRunningInsideDocker()){
            System.out.println("running in Docker");
            return "/media/serverRepositories";
        } else if(System.getProperty(USERNAME).equals(JENKINS)) {
            System.out.println(System.getProperty("user.name"));
            return JENKINS_PATH;
        } else {
            if(Path.of(System.getProperty(USER_DIR)).getFileName().toString().equals(REST)) {
                System.out.println("Local Server Repository");
                return Path.of(System.getProperty(USER_DIR)).getParent().resolve(EXAMPLES).toString();
            } else {
                System.out.println("Local Server Repository");
                return Path.of(System.getProperty(USER_DIR), EXAMPLES).toString();
            }
        }
    }

    private static Boolean isRunningInsideDocker() {
        if (Files.exists(Paths.get("/.dockerenv"))) {
            return true;
        } else {
            try (Stream<String> stream = Files.lines(Paths.get("/proc/1/cgroup"))) {
                return stream.anyMatch(line -> line.contains("/docker"));
            } catch (IOException e) {
                return false;
            }
        }
    }
}
