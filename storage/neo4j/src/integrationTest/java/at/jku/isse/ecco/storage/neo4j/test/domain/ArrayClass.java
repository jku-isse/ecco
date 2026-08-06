package at.jku.isse.ecco.storage.neo4j.test.domain;

import org.neo4j.ogm.annotation.Relationship;

public class ArrayClass extends NeoEntity {
    int intValue;
    String stringValue;

    @Relationship(type = "hasFeatures")
    SomeClass[] features;

    public ArrayClass() {}

    public ArrayClass(int intValue, String stringValue, SomeClass[] features) {
        this.intValue = intValue;
        this.stringValue = stringValue;
        this.features = features;
    }

    public SomeClass[] getFeatures() {
        return features;
    }

    public void setFeatures(SomeClass[] features) {
        this.features = features;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }
}
