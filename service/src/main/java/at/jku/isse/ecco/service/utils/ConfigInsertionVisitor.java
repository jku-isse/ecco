package at.jku.isse.ecco.service.utils;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Insert configuration-strings into the location of all tree-nodes with a location.
 */
public class ConfigInsertionVisitor implements Node.Op.NodeVisitor{

    String configurationString;

    public ConfigInsertionVisitor(Configuration configuration){
        String[] configElements = configuration.getOriginalConfigString().split(", ");
        List<String> configElementsList = Arrays.stream(configElements).sorted().collect(Collectors.toList());
        this.configurationString = String.join(", ", configElementsList);
    }

    @Override
    public void visit(Node.Op node) {
        Location location = node.getLocation();
        if (location == null) { return; }
        if (this.configurationString == null){
            throw new RuntimeException("There is no original configuration string stored!");
        }
        location.setConfigurationString(this.configurationString);
    }
}
