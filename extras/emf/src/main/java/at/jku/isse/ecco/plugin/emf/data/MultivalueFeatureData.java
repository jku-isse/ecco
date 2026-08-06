package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.List;

/**
 * Created by hhoyos on 17/07/2017.
 */
public abstract class MultivalueFeatureData<T> extends StructuralFeatureData {

    private final int size;
    private final List<T> contents; // FIXME? Loop on serialization?

    public MultivalueFeatureData(EStructuralFeature feature, boolean isSet, List<T> contents, boolean isUnique) {
        super(feature, isSet, isUnique);
        this.size = contents.size();
        this.contents = contents;
    }

    public int getSize() {
        return size;
    }

    public List<T> getContents() {
        return contents;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + size + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultivalueFeatureData)) return false;
        if (!super.equals(o)) return false;

        MultivalueFeatureData<?> that = (MultivalueFeatureData<?>) o;

        if (size != that.size) return false;
        return contents.equals(that.contents);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + size;
        result = 31 * result + contents.hashCode();
        return result;
    }
}
