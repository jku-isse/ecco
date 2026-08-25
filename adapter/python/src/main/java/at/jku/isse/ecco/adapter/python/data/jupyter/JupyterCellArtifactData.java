package at.jku.isse.ecco.adapter.python.data.jupyter;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class JupyterCellArtifactData implements ArtifactData {

    private String cellType;
    private String parseType;

    public JupyterCellArtifactData(String cellType) {
        this.cellType = cellType;
    }

    public void setCellType(String cellType) {
        this.cellType = cellType;
    }
    public String getCellType() {
        return this.cellType;
    }
    public String getParseType() {
        return parseType;
    }

    public void setParseType(String parseType) {
        this.parseType = parseType;
    }

    @Override
    public String toString() {
        return this.cellType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JupyterCellArtifactData that = (JupyterCellArtifactData) o;
        return Objects.equals(cellType, that.cellType) && Objects.equals(parseType, that.parseType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cellType, parseType);
    }
}