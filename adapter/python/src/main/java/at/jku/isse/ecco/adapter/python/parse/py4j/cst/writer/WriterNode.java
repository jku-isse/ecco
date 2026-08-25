package at.jku.isse.ecco.adapter.python.parse.py4j.cst.writer;

import at.jku.isse.ecco.adapter.python.data.json.JsonArrayArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.JsonFieldArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.JsonObjectArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.value.*;
import at.jku.isse.ecco.adapter.python.data.jupyter.JupyterCellArtifactData;
import at.jku.isse.ecco.adapter.python.data.jupyter.JupyterLineArtifactData;
import at.jku.isse.ecco.adapter.python.data.python.PythonFieldArtifactData;
import at.jku.isse.ecco.adapter.python.data.python.PythonTypeArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class WriterNode {

    private final Node node;

    public WriterNode(Node node) {
        this.node = node;
    }
    // provides methods to be called from python writer script

    //region Node ------------------------------------------------------------------------------------------------------
    public boolean isOrdered() {
        return node.getArtifact().isOrdered();
    }

    public List<WriterNode> getChildren() {
        List<WriterNode> list = new ArrayList<>();
        for (Node n : node.getChildren()) {
            list.add(new WriterNode((n)));
        }
        return list;
    }
    //endregion

    //region PythonArtifacts -------------------------------------------------------------------------------------------
    // TypeArtifact
    public boolean isType() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof PythonTypeArtifactData;
    }

    public byte[] getTypeArtifactBytes() {
        PythonTypeArtifactData a = (PythonTypeArtifactData) node.getArtifact().getData();
        return a.getBytes();
    }

    // PythonFieldArtifact
    public WriterNode getField(String fieldName) throws Exception {
        for (WriterNode n : getChildren()) {
            if (n.getFieldArtifactName().equals(fieldName)) {
                return n;
            }
        }
        throw new NoSuchFieldException("Field with name " + fieldName + "not found!");
    }

    public boolean isField() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof PythonFieldArtifactData;
    }

    public String getFieldArtifactName() {
        PythonFieldArtifactData d = (PythonFieldArtifactData) node.getArtifact().getData();
        return d.getFieldName();
    }

    public String getParentFieldName() {
        PythonFieldArtifactData a = (PythonFieldArtifactData) node.getArtifact().getData();
        return a.getParentFieldName();
    }
    //endregion

    //region JsonArtifacts ---------------------------------------------------------------------------------------------
    public boolean isJsonObject() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JsonObjectArtifactData;
    }

    public String getJsonFieldName() {
        JsonFieldArtifactData d = (JsonFieldArtifactData) node.getArtifact().getData();
        return d.getFieldName();
    }

    public boolean isJsonArray() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JsonArrayArtifactData;
    }

    public boolean isJsonString() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JsonStringArtifactData;
    }

    public String getJsonString() {
        JsonStringArtifactData a = (JsonStringArtifactData) node.getArtifact().getData();
        return a.getValue();
    }

    public boolean isJsonInteger() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JsonIntegerArtifactData;
    }

    public long getJsonInteger() {
        JsonIntegerArtifactData a = (JsonIntegerArtifactData) node.getArtifact().getData();
        return a.getValue();
    }

    public boolean isJsonRealNumber() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JsonRealNumberArtifactData;
    }

    public double getJsonRealNumber() {
        JsonRealNumberArtifactData a = (JsonRealNumberArtifactData) node.getArtifact().getData();
        return a.getValue();
    }

    public boolean isJsonBoolean() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JsonBooleanArtifactData;
    }

    public boolean getJsonBoolean() {
        JsonBooleanArtifactData a = (JsonBooleanArtifactData) node.getArtifact().getData();
        return a.getValue();
    }

    public boolean isJsonNullValue() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JsonNullValueArtifactData;
    }
    //endregion

    //region JupyterArtifacts ------------------------------------------------------------------------------------------
    public boolean isJupyterCell() {
        return node != null && node.getArtifact() != null && node.getArtifact().getData() != null &&
                node.getArtifact().getData() instanceof JupyterCellArtifactData;
    }

    public String getCellType() {
        JupyterCellArtifactData a = (JupyterCellArtifactData) node.getArtifact().getData();
        return a.getCellType();
    }

    public String getParseType() {
        JupyterCellArtifactData a = (JupyterCellArtifactData) node.getArtifact().getData();
        return a.getParseType();
    }

    public String getLine() {
        JupyterLineArtifactData a = (JupyterLineArtifactData) node.getArtifact().getData();
        return a.getLine();
    }
    //endregion
}
