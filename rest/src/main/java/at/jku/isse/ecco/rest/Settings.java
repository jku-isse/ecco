package at.jku.isse.ecco.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Settings {
    //automatically choose the example path
    public static final String STORAGE_LOCATION_OF_REPOSITORIES = getAutoLocation();
    private static final String EXAMPLES  = "examples";
    private static final String USER_DIR = "user.dir";
    private static final String JENKINS = "jenkins";
    private static final String USERNAME = "user.name";
    private static final String REST = "rest";
    private static final String JENKINS_PATH = "/home/jenkins/host";


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
