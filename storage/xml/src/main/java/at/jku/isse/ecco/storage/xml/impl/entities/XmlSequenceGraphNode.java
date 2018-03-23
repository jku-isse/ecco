package at.jku.isse.ecco.storage.xml.impl.entities;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class XmlSequenceGraphNode implements SequenceGraph.Node.Op, Serializable {
    private HashMap<Artifact.Op<?>, Op> children = new HashMap<>(); // maybe use linked hash map?

    private boolean pol;

    public XmlSequenceGraphNode(boolean pol) {
        this.pol = pol;
    }

    @Override
    public boolean getPol() {
        return this.pol;
    }

    @Override
    public void setPol(boolean pol) {
        this.pol = pol;
    }

    @Override
    public Map<Artifact.Op<?>, Op> getChildren() {
        return this.children;
    }
}
