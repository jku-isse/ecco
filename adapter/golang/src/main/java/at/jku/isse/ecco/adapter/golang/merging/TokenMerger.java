package at.jku.isse.ecco.adapter.golang.merging;


import at.jku.isse.ecco.adapter.golang.data.TokenArtifactData;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TokenMerger {
    public TokenMerger() {

    }

    public Conflicts findConflicts(List<TokenArtifactData> tokenList) {
        List<TokenArtifactData> workingList = new ArrayList<>(tokenList);
        Conflicts conflicts = new Conflicts();

        for (int i = 0; i < workingList.size(); i++) {
            TokenArtifactData a = workingList.get(i);

            for (int j = i + 1; j < workingList.size(); j++) {
                TokenArtifactData b = workingList.get(j);

                if (a.getColumn() == b.getColumn()) {
                    conflicts.add(a, b);
                    workingList.remove(b);
                    j--;
                }
            }
        }

        return conflicts;
    }

    public List<TokenArtifactData> sort(List<TokenArtifactData> tokenList) {
        List<TokenArtifactData> result = new ArrayList<>(tokenList);
        result.sort((a, b) -> {
            if (a.getRow() != b.getRow()) {
                // First sort by line number
                return a.getRow() - b.getRow();
            }
            // Then by column
            return a.getColumn() - b.getColumn();
        });

        return result;
    }

    private List<TokenArtifactData> mergeDuplicates(Conflicts conflicts, List<TokenArtifactData> distinctTokens) {
        List<TokenArtifactData> newRow = conflicts.getDuplicates();
        
        newRow.addAll(distinctTokens);
        
        return newRow.stream().map(
                token -> new TokenArtifactData(token.getToken(), token.getRow()+1, token.getColumn())
        ).collect(Collectors.toList());
    }

    public List<TokenArtifactData> merge(List<TokenArtifactData> tokenList) {
        List<TokenArtifactData> result = sort(tokenList);

        for (int rowNumber = 1; rowNumber < result.get(result.size() - 1).getRow(); rowNumber++) {
            final int rowNr = rowNumber;
            List<TokenArtifactData> row = result.stream()
                    .filter(token -> token.getRow() == rowNr)
                    .collect(Collectors.toList());
            Conflicts conflicts = findConflicts(row);

            row.removeAll(conflicts.getDuplicates());
            result.removeAll(conflicts.getDuplicates());

            if (conflicts.hasConflicts()) {
                List<TokenArtifactData> distinctTokens = findDistinctTokens(row, conflicts);

                result = moveFollowingRows(rowNr, result);
                result.addAll(mergeDuplicates(conflicts, distinctTokens));
                
                result = sort(result);
            }
        }
        
        return result;
    }

    private List<TokenArtifactData> findDistinctTokens(List<TokenArtifactData> tokenList, Conflicts conflicts) {
        return tokenList.stream().filter(token -> !conflicts.getConflicts().contains(token))
                .collect(Collectors.toList());
    }

    private List<TokenArtifactData> moveFollowingRows(int row, List<TokenArtifactData> tokenList) {
        return tokenList.stream()
                .map(token -> {
                    if (token.getRow() > row) {
                        return new TokenArtifactData(token.getToken(),
                                token.getRow() + 1,
                                token.getColumn());
                    }
                    return token;
                })
                .collect(Collectors.toList());
    }

}
