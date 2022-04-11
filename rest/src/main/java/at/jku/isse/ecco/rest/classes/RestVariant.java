package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.core.Variant;

import java.util.Collection;
import java.util.LinkedList;

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
