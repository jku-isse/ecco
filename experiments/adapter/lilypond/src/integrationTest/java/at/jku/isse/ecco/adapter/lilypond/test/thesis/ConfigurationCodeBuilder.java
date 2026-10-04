package at.jku.isse.ecco.adapter.lilypond.test.thesis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

public class ConfigurationCodeBuilder {

    private record FeaturePart(String code, String feature) {
        public void append(StringBuilder sb, HashSet<String> configuration) {
            if (configuration.contains(feature)) {
                sb.append(code);
            }
        }
    }

    private final LinkedList<FeaturePart> parts;

    public ConfigurationCodeBuilder() {
        parts = new LinkedList<>();
    }

	public ConfigurationCodeBuilder add(String code, String feature) {
        parts.add(new FeaturePart(code, feature));
        return this;
    }

    public String getCodeForConfiguration(HashSet<String> configuration) {
        Iterator<FeaturePart> it = parts.iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            FeaturePart fp = it.next();
            fp.append(sb, configuration);
        }
        return sb.toString();
    }
}
