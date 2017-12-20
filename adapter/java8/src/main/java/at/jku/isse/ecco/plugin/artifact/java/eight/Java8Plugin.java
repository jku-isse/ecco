package at.jku.isse.ecco.plugin.artifact.java.eight;

import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import com.google.inject.Module;

/**
 * The activator class controls the plug-in life cycle
 */
public class Java8Plugin extends ArtifactPlugin {

    private static final String[] fileTypes = new String[]{"java"};

    private JavaModule module = new JavaModule();

    public String[] getFileTypes() {
        return fileTypes;
    }

    @Override
    public String getPluginId() {
        return getPluginIdStatic();
    }

    public static String getPluginIdStatic() {
        return Java8Plugin.class.getName();
    }

    @Override
    public Module getModule() {
        return this.module;
    }

    @Override
    public String getName() {
        return "Java8ArtifactPlugin";
    }

    @Override
    public String getDescription() {
        return "Java Artifact Plugin for Java 8";
    }

}
