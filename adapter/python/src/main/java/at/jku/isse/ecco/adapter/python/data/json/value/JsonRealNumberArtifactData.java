package at.jku.isse.ecco.adapter.python.data.json.value;

public class JsonRealNumberArtifactData extends JsonValueArtifactData<Double> {

    // using double to ensure compatibility with python
    public JsonRealNumberArtifactData(double value) {
        super(value);
    }
}