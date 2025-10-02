package at.jku.isse.ecco.util;

import at.jku.isse.ecco.dao.Persistable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Location implements Persistable {

    private int startLine;
    private int endLine;
    private String filePath;
    // original configurationString of committed variant
    private String configurationString;

    public Location(int startLine, int endLine, Path filePath, String configurationString){
        this.startLine = startLine;
        this.endLine = endLine;
        this.filePath = filePath.toString();
        this.setConfigurationString(configurationString);
    }

    public int getStartLine(){
        return this.startLine;
    }

    public int getEndLine(){
        return this.endLine;
    }

    public Path getFilePath(){
        return Paths.get(this.filePath);
    }

    public String getConfigurationString() {
        return configurationString;
    }

    public void setConfigurationString(String configurationString){
        this.configurationString = sortConfigurationString(configurationString);
    }

    public static String sortConfigurationString(String configString){
        String[] configElements = configString.split(",");
        for (int i = 0; i < configElements.length; i++){ configElements[i] = configElements[i].trim(); }
        List<String> sortedList = Arrays.stream(configElements).sorted().toList();
        return String.join(", ", sortedList);
    }
}
