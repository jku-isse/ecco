package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class AbstractArtifactData implements ArtifactData {

        private String leadingComment = "";
        private String leadingText = "";
        private String trailingComment = "";

    public String getLeadingComment() {
        return leadingComment;
    }

    public void setLeadingComment(String leadingComment) {
        this.leadingComment = leadingComment;
    }

    public String getTrailingComment() {
        return trailingComment;
    }

    public void setTrailingComment(String trailingComment) {
        this.trailingComment = trailingComment;
    }

    public String getLeadingText() {
        return leadingText;
    }

    public void setLeadingText(String leadingText) {
        this.leadingText = leadingText;
    }
}
