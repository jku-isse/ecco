package at.jku.isse.ecco.rest;

import java.nio.file.Path;

public class Settings {
    //when running directly
    //public static final String STORAGE_LOCATION_OF_REPOSITORIES = Path.of(System.getProperty("user.dir"), "examples").toString();

    //running over gradle
    //public static final String STORAGE_LOCATION_OF_REPOSITORIES = Path.of(System.getProperty("user.dir")).getParent().resolve("examples").toString();

    //automatically choose the example path
    public static final String STORAGE_LOCATION_OF_REPOSITORIES = getAutoLocation();

    private static String getAutoLocation() {
        if(System.getProperty("user.name").equals("jenkins")) {
            System.out.println(System.getProperty("user.name"));
            return "/home/jenkins/host";
        } else {
            if(Path.of(System.getProperty("user.dir")).getFileName().toString().equals("rest")) {
                System.out.println("Local Server Repository");
                return Path.of(System.getProperty("user.dir")).getParent().resolve("examples").toString();
            } else {
                System.out.println("Local Server Repository");
                return Path.of(System.getProperty("user.dir"), "examples").toString();
            }
        }
    }
}
