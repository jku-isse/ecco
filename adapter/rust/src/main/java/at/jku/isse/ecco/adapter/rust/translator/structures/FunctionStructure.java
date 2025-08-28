package at.jku.isse.ecco.adapter.rust.translator.structures;


import java.util.ArrayList;
import java.util.List;

public class FunctionStructure extends Structure {
    private final int startLine;
    private final int endLine;
    private final String functionSignature;
    private List<String> attributes = new ArrayList<>();


    public FunctionStructure(int startLine, int endLine, String functionSignature) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.functionSignature = functionSignature;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getFunctionSignature() {
        return functionSignature;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public void addAttribute(String attribute) {
        this.attributes.add(attribute);
    }
}