package at.jku.isse.ecco.adapter.python.data.json.value;

public class JsonIntegerArtifactData extends JsonValueArtifactData<Long> {

    // using long to ensure compatibility with python
    public JsonIntegerArtifactData(long value) {
        super(value);
    }
}