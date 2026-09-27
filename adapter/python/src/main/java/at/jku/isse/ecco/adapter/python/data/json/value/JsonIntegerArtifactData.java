package at.jku.isse.ecco.adapter.python.data.json.value;

public class JsonIntegerArtifactData extends JsonValueArtifactData<Long> {

    private static final long serialVersionUID = 8781789401917832775L;

    // using long to ensure compatibility with python
    public JsonIntegerArtifactData(long value) {
        super(value);
    }
}