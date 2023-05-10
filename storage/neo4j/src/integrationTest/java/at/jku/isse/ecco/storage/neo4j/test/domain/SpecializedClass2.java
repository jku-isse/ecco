package at.jku.isse.ecco.storage.neo4j.test.domain;

import java.util.ArrayList;
import java.util.List;

public class SpecializedClass2 extends NeoEntity {

    int intValue;
    String stringValue;

    List<SpecializedClass3> listOfObjects = new ArrayList<>();

    public SpecializedClass2() {}

    public SpecializedClass2(int intValue, String stringValue, List<SpecializedClass3> listOfObjects) {
        this.intValue = intValue;
        this.stringValue = stringValue;
        this.listOfObjects = listOfObjects;
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

    public List<SpecializedClass3> getListOfObjects() {
        return listOfObjects;
    }

    public void setListOfObjects(List<SpecializedClass3> listOfObjects) {
        this.listOfObjects = listOfObjects;
    }
}
