package at.jku.isse.ecco.util;

import at.jku.isse.ecco.dao.Persistable;

import java.nio.file.Path;
import java.nio.file.Paths;


// TODO: implement as property
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
        this.configurationString = configurationString;
    }

    public Location(int startLine, int endLine, Path filePath){
        this.startLine = startLine;
        this.endLine = endLine;
        this.filePath = filePath.toString();
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
        this.configurationString = configurationString;
    }
}
