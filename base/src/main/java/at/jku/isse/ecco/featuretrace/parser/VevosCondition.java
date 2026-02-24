package at.jku.isse.ecco.featuretrace.parser;

import java.nio.file.Path;
import java.nio.file.Paths;

public class VevosCondition {
    private String rawTextLine;
    private Path filePath;
    private String fileConditionString;
    private String blockConditionString;
    private String presenceConditionString;
    private String lineType;
    private int startLine;
    private int endLine;

    public VevosCondition(String vevosFileLine){
        // VEVOS file entry structure: Path;File Condition;Block Condition;Presence Condition;start;end
        String[] lineParts = vevosFileLine.split(";");
        if (lineParts.length < 6){
            throw new IllegalArgumentException(
                    String.format("VEVOS file entry has less tha" +
                            "n 6 comma-separated parts: %s", vevosFileLine));
        }
        this.rawTextLine = vevosFileLine;
        this.filePath = Paths.get(lineParts[0]);
        this.fileConditionString = this.prepareConditionString(lineParts[1]);
        this.blockConditionString = this.prepareConditionString(lineParts[2]);
        this.presenceConditionString = this.prepareConditionString(lineParts[3]);
        this.lineType = lineParts[4];
        this.startLine = Integer.parseInt(lineParts[5]);
        this.endLine = Integer.parseInt(lineParts[6]);
    }

    private String prepareConditionString(String stringCondition){
        // replace operands (!; ||; &&)
        stringCondition = stringCondition.replace("!", "~");
        stringCondition = stringCondition.replace("||", "|");
        stringCondition = stringCondition.replace("&&", "&");
        stringCondition = stringCondition.replace("True", "$true");
        stringCondition = stringCondition.replace("False", "$false");
        return stringCondition;
    }

    public String getRawTextLine(){ return this.rawTextLine; }
    public Path getFilePath(){
        return this.filePath;
    }
    public String getFileConditionString(){
        return this.fileConditionString;
    }
    public String getBlockConditionString(){
        return this.blockConditionString;
    }
    public String getConditionString(){
        return this.presenceConditionString;
    }
    public String getLineType(){ return this.lineType; }
    public int getStartLine(){
        return this.startLine;
    }
    public int getEndLine(){
        return this.endLine;
    }


}
