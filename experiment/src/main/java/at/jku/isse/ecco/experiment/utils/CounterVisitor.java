package at.jku.isse.ecco.experiment.utils;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;

import java.util.Optional;

public class CounterVisitor implements Node.Op.NodeVisitor {

    private int nodeCount = 0;
    private int someConditionCount = 0;
    private int retroactiveConditionCount = 0;
    private int proactiveConditionCount = 0;
    private int locationCount = 0;

    @Override
    public void visit(Node.Op node) {
        this.nodeCount++;

        Optional<Location> optionalLocation = node.getProperty("Location");
        if (optionalLocation.isPresent()){
            this.locationCount++;
        }

        FeatureTrace trace = node.getFeatureTrace();
        if (trace == null){
            return;
        }

        if (trace.containsProactiveCondition()){
            this.proactiveConditionCount++;
        }

        String retroactiveCondition = trace.getRetroactiveConditionString();
        if (retroactiveCondition != null){
            this.retroactiveConditionCount++;
        }

        if (trace.containsProactiveCondition() || retroactiveCondition != null){
            this.someConditionCount++;
        }
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getSomeConditionCount() {
        return someConditionCount;
    }

    public int getRetroactiveConditionCount() {
        return retroactiveConditionCount;
    }

    public int getProactiveConditionCount() {
        return proactiveConditionCount;
    }

    public int getLocationCount() {
        return locationCount;
    }

    public void printEverything(){
        System.out.println(
                "NodeCount: " + this.nodeCount + "\n"
                + "SomeConditionCount: " + this.someConditionCount + "\n"
                + "RetroactiveConditionCount: " + this.retroactiveConditionCount + "\n"
                + "ProactiveConditionCount: " + this.proactiveConditionCount + "\n"
                + "LocationCount: " + this.locationCount
        );
    }
}
