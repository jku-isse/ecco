package at.jku.isse.ecco.experiment.utils.picker;

import java.util.List;

public interface MemoryListPicker<T> extends ListPicker<T> {

    public List<T> getSource();
}
