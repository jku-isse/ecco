package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Created by hhoyos on 17/07/2017.
 */
public abstract class SinglevalueFeatureData<T> extends StructuralFeatureData {

    private final T value;

    public SinglevalueFeatureData(EStructuralFeature feature, boolean isSet, T value, boolean isUnique) {
        super(feature, isSet, isUnique);
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SinglevalueFeatureData)) return false;
        if (!super.equals(o)) return false;

        SinglevalueFeatureData<?> that = (SinglevalueFeatureData<?>) o;

        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
