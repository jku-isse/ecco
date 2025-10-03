package at.jku.isse.ecco.adapter.rust.extractor;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class Feature {
    String name;
    List<Integer> codeLines;

    @Override
    public String toString() {
        return "Feature{" +
                "name='" + name + '\'' +
                ", codeLines=" + codeLines +
                '}';
    }

}