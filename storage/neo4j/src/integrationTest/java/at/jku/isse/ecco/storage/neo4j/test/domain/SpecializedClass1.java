package at.jku.isse.ecco.storage.neo4j.test.domain;

import java.util.ArrayList;
import java.util.List;

public class SpecializedClass1 extends NeoEntity {

    int intValue;
    String stringValue;

    List<SpecializedClass2>  listOfObjects = new ArrayList<>();

    public SpecializedClass1() {}

    public SpecializedClass1(int intValue, String stringValue, List<SpecializedClass2> listOfObjects) {
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

    public List<SpecializedClass2> getListOfObjects() {
        return listOfObjects;
    }

    public void setListOfObjects(List<SpecializedClass2> listOfObjects) {
        this.listOfObjects = listOfObjects;
    }
}
