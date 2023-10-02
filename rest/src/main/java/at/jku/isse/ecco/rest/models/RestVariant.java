package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.core.Variant;

public class RestVariant {

    private final Variant variant;

    public RestVariant(Variant feature) {
        this.variant = feature;
    }

    public String getId() {
        return variant.getId();
    }

    public String getName() {
        return variant.getName();
    }

    public String getDescription() {return  variant.getDescription(); }

    public RestConfiguration getConfiguration() {
        return new RestConfiguration(variant.getConfiguration());
    }
}
