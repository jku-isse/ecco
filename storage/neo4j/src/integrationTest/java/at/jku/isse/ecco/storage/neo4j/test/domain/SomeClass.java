package at.jku.isse.ecco.storage.neo4j.test.domain;

import java.util.ArrayList;
import java.util.List;

public class SomeClass extends NeoEntity {

    int intValue;
    String stringValue;

    public SomeClass() {}

    public SomeClass(int intValue, String stringValue) {
        this.intValue = intValue;
        this.stringValue = stringValue;
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
