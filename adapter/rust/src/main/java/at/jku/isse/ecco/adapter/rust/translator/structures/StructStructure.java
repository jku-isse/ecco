package at.jku.isse.ecco.adapter.rust.translator.structures;

import lombok.Data;

@Data
public class StructStructure extends Structure {
    private final int startLine;
    private final int endLine;
    private final String structSignature;
}
