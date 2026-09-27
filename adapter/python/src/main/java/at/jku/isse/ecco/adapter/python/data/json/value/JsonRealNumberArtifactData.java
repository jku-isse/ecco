package at.jku.isse.ecco.adapter.python.data.json.value;

public class JsonRealNumberArtifactData extends JsonValueArtifactData<Double> {

    private static final long serialVersionUID = -4581678124061420827L;

    // using double to ensure compatibility with python
    public JsonRealNumberArtifactData(double value) {
        super(value);
    }
}