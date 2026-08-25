package at.jku.isse.ecco.adapter.python.parse.py4j.cst.reader;

import at.jku.isse.ecco.adapter.python.data.json.JsonArrayArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.JsonFieldArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.JsonObjectArtifactData;
import at.jku.isse.ecco.adapter.python.data.json.value.*;
import at.jku.isse.ecco.adapter.python.data.jupyter.JupyterCellArtifactData;
import at.jku.isse.ecco.adapter.python.data.jupyter.JupyterLineArtifactData;
import at.jku.isse.ecco.adapter.python.data.python.PythonFieldArtifactData;
import at.jku.isse.ecco.adapter.python.data.python.PythonTypeArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

@SuppressWarnings("unused")
public class ReaderNode {

    private final Node.Op node;

    public ReaderNode(Node.Op node) {
        this.node = node;
    }

    // provides methods to be called from python reader script
    private static EntityFactory entityFactory;
    private static ReaderEntryPoint readerEntryPoint;

    public static void reset(EntityFactory entityFactory, ReaderEntryPoint readerEntryPoint) {
        ReaderNode.entityFactory = entityFactory;
        ReaderNode.readerEntryPoint = readerEntryPoint;
    }

    //region Node ------------------------------------------------------------------------------------------------------
    private ReaderNode addNode(ArtifactData artifactData) {
        Artifact.Op<?> artifact = entityFactory.createArtifact(artifactData);
        Node.Op child = entityFactory.createNode(artifact);
        readerEntryPoint.nNodes++;

        if (readerEntryPoint.root == null || node == null) {
            readerEntryPoint.root = child;
        } else {
            node.addChild(child);
        }

        return new ReaderNode(child);
    }

    public void makeOrdered() {
        node.getArtifact().setOrdered(true);
    }
    //endregion

    //region PythonArtifacts -------------------------------------------------------------------------------------------
    // PythonTypeArtifact
    public ReaderNode addTypeNode() {
        return addNode(new PythonTypeArtifactData());
    }

    public void setBytes(byte[] bytes) throws ClassCastException {
        PythonTypeArtifactData a = (PythonTypeArtifactData) node.getArtifact().getData();
        a.setBytes(bytes);
    }

    // PythonFieldArtifact
    public ReaderNode addFieldNode(String fieldName) {
        return addNode(new PythonFieldArtifactData(fieldName));
    }

    public void setParentFieldName(String parentFieldName) throws ClassCastException {
        PythonFieldArtifactData a = (PythonFieldArtifactData) node.getArtifact().getData();
        a.setParentFieldName(parentFieldName);
    }
    //endregion

    //region JsonArtifacts ---------------------------------------------------------------------------------------------
    public ReaderNode addJsonObjectNode() {
        return addNode(new JsonObjectArtifactData());
    }

    public ReaderNode addJsonFieldNode(String fieldName) {
        return addNode(new JsonFieldArtifactData(fieldName));
    }

    public ReaderNode addJsonArrayNode() {
        ReaderNode node = addNode(new JsonArrayArtifactData());
        node.makeOrdered();
        return node;
    }

    public ReaderNode addJsonBooleanNode(boolean value) {
        return addNode(new JsonBooleanArtifactData(value));
    }

    public ReaderNode addJsonStringNode(String value) {
        return addNode(new JsonStringArtifactData(value));
    }

    public ReaderNode addJsonIntegerNode(long value) {
        return addNode(new JsonIntegerArtifactData(value));
    }

    public ReaderNode addJsonRealNumberNode(double value) {
        return addNode(new JsonRealNumberArtifactData(value));
    }

    public ReaderNode addJsonNullValueNode() {
        return addNode(new JsonNullValueArtifactData());
    }
    //endregion

    //region JupyterArtifacts ------------------------------------------------------------------------------------------
    public ReaderNode addJupyterCellNode(String cell_type) {
        return addNode(new JupyterCellArtifactData(cell_type));
    }

    public void setParseType(String parseType) throws ClassCastException {
        JupyterCellArtifactData a = (JupyterCellArtifactData) node.getArtifact().getData();
        a.setParseType(parseType);
    }

    // JupyterLineArtifact
    public ReaderNode addJupyterLineNode(String line) {
        return addNode(new JupyterLineArtifactData(line));
    }
    //endregion
}
