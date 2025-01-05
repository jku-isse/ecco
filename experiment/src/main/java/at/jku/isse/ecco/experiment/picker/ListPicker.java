package at.jku.isse.ecco.experiment.picker;

import java.util.Collection;
import java.util.List;

public interface ListPicker<T> {

        List<T> pickPercentage(Collection<T> source, int percentage);

        List<T> pickNumber(Collection<T> source, int numberOfPicks);

}
