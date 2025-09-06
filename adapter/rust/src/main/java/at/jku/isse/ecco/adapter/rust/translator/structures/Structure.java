package at.jku.isse.ecco.adapter.rust.translator.structures;

import lombok.Data;

@Data
public class Structure {
    private final int startLine;
    private final int endLine;
    private final String content;
    private final Type type;
    private final String attributes;
    private final String publicModifier;
}
