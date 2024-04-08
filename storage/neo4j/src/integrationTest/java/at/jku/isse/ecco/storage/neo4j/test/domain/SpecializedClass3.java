package at.jku.isse.ecco.storage.neo4j.test.domain;

public class SpecializedClass3 extends NeoEntity {

    int intValue;
    String stringValue;

    public SpecializedClass3() {}

    public SpecializedClass3(int intValue, String stringValue) {
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
