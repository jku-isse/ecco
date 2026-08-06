package at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.*;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.main.GrammarSerializationService;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.BlockDefinition;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.ChildRelation;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.Node;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.NonTerminalNode;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Michael Jahn
 */
public class StructureInferenceServiceImpl implements StructureInferenceService {

    private static final String STRING_REGEX = "((?:[^\\\\])\\\")|(\\A\\\")";
    private static final String COMMENT_LINE_REGEX = "(?:[^:])//";
    private static final String ROOT_NODE = "root";
    private static final String META_ROOT = "metaRoot";
    private static final String END_LABEL_NODE = "END_";
    private static final String END_LABEL = "END";


    @Override
    public NonTerminalNode parseBaseStructure(String filePath, List<BlockDefinition> blockDefinitions) throws IOException {
        return inferBaseStructureFromFiles(filePath, blockDefinitions, true);
    }

    @Override
    public NonTerminalNode inferBaseStructureFromFiles(String filePath, List<BlockDefinition> blockDefinitions) throws IOException {
        return inferBaseStructureFromFiles(filePath, blockDefinitions, false);
    }

    @Override
    public Node inferBaseStructureFromFiles(List<String> filePaths, List<BlockDefinition> blockDefinitions) throws IOException {
        List<NonTerminalNode> rootNodes = inferFileStructures(filePaths, blockDefinitions);
        return inferBaseStructure(rootNodes, blockDefinitions);
    }

    @Override
    public Node inferBaseStructure(List<NonTerminalNode> rootNodes, List<BlockDefinition> blockDefinitions) {

        // merge all rootNodes to one tree
        Set<String> allUniqueLabels = new HashSet<>();
        Set<String> allRootLabels = new HashSet<>();
        for (NonTerminalNode rootNode : rootNodes) {
            allUniqueLabels.addAll(rootNode.getAllLabelsRecurisve());
            allRootLabels.addAll(rootNode.getAllChildLabels());
        }

        // stores the created label nodes uniquely
        Map<String, Node> uniqueLabelsMap = new HashMap<>();
        // stores for every label the different occurrences of child labels
        Map<String, List<List<String>>> childrenOccurrenceLists = new HashMap<>();
        // stores the number of occurrences per label
        Map<String, Integer> nrLabelOccurrences = new HashMap<>();

        for (String curLabel : allUniqueLabels) {
            Node curLabelNode = null;
            List<List<String>> curChildrenOccurrenceLists;
            if (childrenOccurrenceLists.get(curLabel) == null) {
                childrenOccurrenceLists.put(curLabel, new ArrayList<>());
            }
            curChildrenOccurrenceLists = childrenOccurrenceLists.get(curLabel);
            for (NonTerminalNode rootNode : rootNodes) {
                List<Node> curLabelNodes = rootNode.getAllChildNodesPerLabelRecursive(curLabel);
                for (Node labelNode : curLabelNodes) {
                    List<String> curChildrenOccurrenceList = new ArrayList<>();

                    if (curLabelNode == null) {
                        curLabelNode = new NonTerminalNode(labelNode.getLabel(), ((NonTerminalNode) labelNode).getBlockDefinition());
                    }
                    for (ChildRelation childRelation : labelNode.getChildren()) {
                        if (!curLabelNode.getChildren().stream().anyMatch(c -> childRelation.getLabel().equals(c.getLabel()))) {
                            curLabelNode.addChild(new ChildRelation(new NonTerminalNode(childRelation.getLabel(), ((NonTerminalNode) childRelation.getChildNode()).getBlockDefinition())));
                        } else {
                            int nrChildrenPerLabel = labelNode.getAllChildrenPerLabel(childRelation.getLabel()).size();
                            NonTerminalNode curLabelNodeChild = ((NonTerminalNode) curLabelNode.getAllChildrenPerLabel(childRelation.getLabel()).get(0));
                            if (curLabelNodeChild.getMaxOccurences() < nrChildrenPerLabel) {
                                curLabelNodeChild.setMaxOccurences(nrChildrenPerLabel);
                            }
                        }
                        curChildrenOccurrenceList.add(childRelation.getLabel());
                    }
                    if (nrLabelOccurrences.get(curLabel) == null) {
                        nrLabelOccurrences.put(curLabel, 1);
                    } else {
                        nrLabelOccurrences.put(curLabel, nrLabelOccurrences.get(curLabel) + 1);
                    }
                    curChildrenOccurrenceLists.add(curChildrenOccurrenceList);
                }
            }

            // check all children occurrences for fixed ordering and compulsory nodes
            List<String> firstOccurrenceList = curChildrenOccurrenceLists.get(0);
            for (List<String> occurrenceList : curChildrenOccurrenceLists) {
                boolean[] matched = new boolean[firstOccurrenceList.size()];
                for (int i = 0; i < occurrenceList.size(); i++) {
                    final String curChildLabel = occurrenceList.get(i);
                    if (firstOccurrenceList.size() <= i || !curChildLabel.equals(firstOccurrenceList.get(i)) && firstOccurrenceList.contains(curChildLabel)) {
                        curLabelNode.getChildren().stream().filter(childNode -> childNode.getLabel().equals(curChildLabel)).findFirst().get().setHasFixedOrder(false);
                    } else if (!firstOccurrenceList.contains(curChildLabel)) {
                        ChildRelation curChildRelation = curLabelNode.getChildren().stream().filter(childNode -> childNode.getLabel().equals(curChildLabel)).findFirst().get();
                        curChildRelation.setHasFixedOrder(false);
                        curChildRelation.setExactlyOnce(false);
                    } else {
                        matched[i] = true;
                    }
                    if (occurrenceList.stream().filter(it -> it.equals(curChildLabel)).count() > 1 || !firstOccurrenceList.contains(curChildLabel)) {
                        curLabelNode.getChildren().stream().filter(childNode -> childNode.getLabel().equals(curChildLabel)).forEach(current -> current.setExactlyOnce(false));
                    }
                }
                for (int i = 0; i < firstOccurrenceList.size(); i++) {
                    if (!matched[i]) {
                        String curChildLabel = firstOccurrenceList.get(i);
                        ChildRelation curChildRelation = curLabelNode.getChildren().stream().filter(childNode -> childNode.getLabel().equals(curChildLabel)).findFirst().get();
                        curChildRelation.setHasFixedOrder(false);
                        if(occurrenceList.stream().filter(it -> it.equals(curChildLabel)).count() != 1) {
                            curChildRelation.setExactlyOnce(false);
                        }
                    }
                }
            }

            uniqueLabelsMap.put(curLabel, curLabelNode);
            if(ParameterSettings.INFO_OUTPUT)
                System.out.println("all model nodes merged: \n" + (curLabelNode != null ? curLabelNode.subTreeToString() : ""));
        }

        // connect all nodes with each other
        for (Map.Entry<String, Node> nodeEntry : uniqueLabelsMap.entrySet()) {
            List<ChildRelation> newChildren = new ArrayList<>();
            for (ChildRelation childRelation : nodeEntry.getValue().getChildren()) {
                if (childRelation.getLabel().equals(nodeEntry.getKey())) {
                    nodeEntry.getValue().setRecursiveNode(new ChildRelation(nodeEntry.getValue(), false, false));
                } else if (uniqueLabelsMap.containsKey(childRelation.getLabel())) {
                    ChildRelation newChildRelation = new ChildRelation(uniqueLabelsMap.get(childRelation.getLabel()));
                    newChildRelation.setExactlyOnce(childRelation.isExactlyOnce());
                    newChildRelation.setHasFixedOrder(childRelation.hasFixedOrder());
                    newChildren.add(newChildRelation);
                } else {
                    newChildren.add(childRelation);
                }
            }
            nodeEntry.getValue().setChildren(newChildren);
        }

        Node returnRootNode = uniqueLabelsMap.get(ROOT_NODE);

        if(ParameterSettings.INFO_OUTPUT)
            System.out.println("Final inferred graph grammar: " + returnRootNode.subTreeToString());

        return returnRootNode;

    }

    @Override
    public List<NonTerminalNode> inferFileStructures(List<String> filePaths, List<BlockDefinition> blockDefinitions) throws IOException {

        List<NonTerminalNode> rootNodes = new ArrayList<>();

        for (String file : filePaths) {
            NonTerminalNode metaRootNode = new NonTerminalNode(META_ROOT, null);
            metaRootNode.addChild(new ChildRelation(inferBaseStructureFromFiles(file, blockDefinitions)));
            rootNodes.add(metaRootNode);
        }

        return rootNodes;
    }

    @Override
    public NonTerminal inferGraphGrammar(Node baseStructure, List<BlockDefinition> blockDefinitions) {

        Map<String, StructureNonTerminal> labelsNonTerminalMap = new HashMap<>();
        labelsNonTerminalMap.put(ROOT_NODE, NonTerminalFactory.createNewStructureNonTerminal());
        for (String label : baseStructure.getAllLabelsRecurisve()) {
            labelsNonTerminalMap.put(label, NonTerminalFactory.createNewStructureNonTerminal(
                    blockDefinitions.stream().filter(blockDefinition -> blockDefinition.getName().toUpperCase().equals(label.toUpperCase())).findFirst().get()));
        }


        Map<String, BlockDefinition> blockDefinitionMap = new HashMap<>();
        for (BlockDefinition blockDefinition : blockDefinitions) {
            blockDefinitionMap.put(blockDefinition.getName(), blockDefinition);
        }

        Set<String> allLabels = baseStructure.getAllLabelsRecurisve();
        allLabels.add(ROOT_NODE);
        NonTerminal rootSymbol = null;

        for (String label : allLabels) {

            // children
            Node labelNode = baseStructure.getFirstChildPerLabelRecursive(label);
            if(label.equals(ROOT_NODE)) {
                labelNode = baseStructure;
            }
            NonTerminal unorderedChildrenNonTerminal = NonTerminalFactory.createNewStructureNonTerminal();

            NonTerminal orderedChildrenNonTerminal = NonTerminalFactory.createNewStructureNonTerminal();
            List<Symbol> orderedChildRule = new ArrayList<>();
            orderedChildrenNonTerminal.addRule(new Rule(orderedChildRule));

            List<ChildRelation> childRelations = new ArrayList<>(labelNode.getChildren());
            if(labelNode.isRecursiveNode()) {
                childRelations.add(labelNode.getRecursiveNodeRelation());
            }
            for (ChildRelation childRelation : childRelations) {

                if(childRelation.hasFixedOrder()) {
                    // ordered children
                    if(!childRelation.isExactlyOnce()) {
                        NonTerminal orderedArbitraryOftenNonTerminal = NonTerminalFactory.createNewStructureNonTerminal();
                        orderedArbitraryOftenNonTerminal.addRule(new Rule(Arrays.asList(labelsNonTerminalMap.get(childRelation.getLabel()), orderedArbitraryOftenNonTerminal)));
                        orderedArbitraryOftenNonTerminal.addRule(new Rule(new ArrayList<>()));
                        orderedChildRule.add(orderedArbitraryOftenNonTerminal);
                    } else {
                        // exactly once
                        orderedChildRule.add(labelsNonTerminalMap.get(childRelation.getLabel()));
                    }
                } else {
                    // unordered children
                    if(!childRelation.isExactlyOnce()) {
                        unorderedChildrenNonTerminal.addRule(new Rule(Arrays.asList(labelsNonTerminalMap.get(childRelation.getLabel()), unorderedChildrenNonTerminal)));
                        unorderedChildrenNonTerminal.addRule(new Rule(new ArrayList<>()));
                    } else {
                        // exactly once
                        NonTerminal unorderedOnceNonTerminal = NonTerminalFactory.createNewStructureNonTerminal();
                        unorderedOnceNonTerminal.addRule(new Rule(Arrays.asList(labelsNonTerminalMap.get(childRelation.getLabel()), unorderedChildrenNonTerminal)));
                        for (Rule rule : unorderedChildrenNonTerminal.getRules()) {
                            if(rule.getSymbols().size() > 0) {
                                unorderedOnceNonTerminal.addRule(new Rule(Arrays.asList(rule.getSymbols().get(0), unorderedOnceNonTerminal)));
                            }
                        }
                        unorderedChildrenNonTerminal = unorderedOnceNonTerminal;
                    }
                }
            }

            // label
            StructureNonTerminal labelNonTerminal = labelsNonTerminalMap.get(label);
            List<Symbol> labelRule = new ArrayList<>();
            if(label.equals(ROOT_NODE)) {
                rootSymbol = labelNonTerminal;
            } else {
                labelRule.add(new Terminal(label.toUpperCase(), label.toUpperCase()));
            }
            if (orderedChildrenNonTerminal.getRules().size() > 0 && orderedChildrenNonTerminal.getRules().get(0).getSymbols().size() > 0) {
                labelRule.add(orderedChildrenNonTerminal);
            }
            if (unorderedChildrenNonTerminal.getRules().size() > 0 && unorderedChildrenNonTerminal.getRules().get(0).getSymbols().size() > 0) {
                labelRule.add(unorderedChildrenNonTerminal);
            }
            if(!label.equals(ROOT_NODE) && blockDefinitionMap.get(label).getEndRegex() != null) {
                labelRule.add(new Terminal(END_LABEL, END_LABEL));
//                labelRule.add(new Terminal(END_LABEL_NODE + label.toUpperCase(), END_LABEL_NODE + label.toUpperCase()));
            }
            labelNonTerminal.setLabelRule(new Rule(labelRule));

            /*if (labelNode.isRecursiveNode()) {
                // TODO this needs to be handled as child, as all the others with respect to relation properties
                labelNonTerminal.addRule(new Rule(Arrays.asList(labelNonTerminal)));
            }*/
        }

        // optimize nonTerminals
        boolean foundOptimization;
        do {
            foundOptimization = false;
            for (NonTerminal nonTerminal : labelsNonTerminalMap.values()) {

//                if(!nonTerminal.equals(rootSymbol)) {
                // if it contains only one rule with one nonTerminal symbol, reduce it
                if (nonTerminal.getRules().size() == 1) {
                    Rule nonTerminalRule = nonTerminal.getRules().get(0);
                    if (nonTerminalRule.getSymbols().size() == 1 && nonTerminalRule.getSymbols().get(0).isNonTerminal()) {
                        NonTerminal replaceNonTerminal = (NonTerminal) nonTerminalRule.getSymbols().get(0);
                        if(replaceNonTerminal.getRules().size() == 1 && replaceNonTerminal.getRules().get(0).getSymbols().size() == 1) {
                            foundOptimization = true;
                            nonTerminal.getRules().get(0).replaceSymbols(0, 1, replaceNonTerminal.getRules().get(0).getSymbols());
                            if (replaceNonTerminal.getRules().size() > 1) {
                                for (int i = 1; i < replaceNonTerminal.getRules().size(); i++) {
                                    nonTerminal.addRule(replaceNonTerminal.getRules().get(i));
                                }
                            }
                        }
                    }
                }
//                }
            }
        } while (foundOptimization);

        if(ParameterSettings.INFO_OUTPUT)
            System.out.println(rootSymbol.subTreeToString());

        return rootSymbol;
    }

    @Override
    public NonTerminal inferGraphGrammar(List<String> filePaths, List<BlockDefinition> blockDefinitions) throws IOException {
        Node baseStructure = inferBaseStructureFromFiles(filePaths, blockDefinitions);

        return inferGraphGrammar(baseStructure, blockDefinitions);
    }


    /**
     * Private Methods
     */

    private NonTerminalNode inferBaseStructureFromFiles(String filePath, List<BlockDefinition> blockDefinitions, boolean storeContents) throws IOException {

        Collections.sort(blockDefinitions, (o1, o2) -> o2.compareTo(o1));
        Pattern stringPattern = Pattern.compile(STRING_REGEX);
        Pattern commentLinePattern = Pattern.compile(COMMENT_LINE_REGEX);

        NonTerminalNode rootNode = new NonTerminalNode(ROOT_NODE, new BlockDefinition(ROOT_NODE, 0, "", "", false));

        Deque<NonTerminalNode> curOpenNodes = new ArrayDeque<>();
        curOpenNodes.push(rootNode);

        File file = new File(filePath);
        FileReader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);
        String line;
        StringBuilder content = new StringBuilder();
        boolean withinString = false;
        boolean ignoreLine = false;
        while ((line = br.readLine()) != null) {
            if(storeContents) {
                content.append(line + "\n");
            }

            if (commentLinePattern.matcher(line).find()) {
                Matcher matcher = commentLinePattern.matcher(line);
                matcher.find();
                line = line.substring(0, matcher.start());
            }

            // ignore characters that are within a string to avoid recognizing blocks within them
            if (withinString && stringPattern.matcher(line).find()) {
                Matcher matcher = stringPattern.matcher(line);
                matcher.find();
                line = line.substring(matcher.start() + 1);
                withinString = false;
                ignoreLine = false;
            } else if (!withinString && stringPattern.matcher(line).find()) {
                Matcher stringMatcher = stringPattern.matcher(line);
                int count = 0;
                if (stringMatcher.find()) {
                    do {
                        count++;
                    } while (stringMatcher.find(stringMatcher.start() + 1));
                }

                if (count % 2 != 0) {
                    Matcher matcher = stringPattern.matcher(line);
                    matcher.find();
                    line = line.substring(0, matcher.start());
                    withinString = true;
                    ignoreLine = false;
                }
            } else if (withinString) {
                ignoreLine = true;
            }

            if (!ignoreLine) {
                Map<Integer, BlockMatching> foundStartBlocks = new HashMap<>();
                SortedSet<Integer> foundIndices = new TreeSet<>();

                // search for starting blocks and add them to the foundStartBlocksMap
                for (BlockDefinition blockDefinition : blockDefinitions) {
                    if (blockDefinition.matchesStartRegex(line)) {
                        // found block start
                        Matcher matcher = blockDefinition.getStartRegex().matcher(line);
                        while (matcher.find()) {
                            BlockMatching blockMatching;
                            if (blockDefinition.isMatchGroups()) {
                                List<String> groupValues = new ArrayList<>();
                                for (int i = 1; i <= matcher.groupCount(); i++) {
                                    String groupResult = matcher.group(i);
                                    if (groupResult != null) {
                                        groupValues.add(groupResult);
                                    }
                                }
                                blockMatching = new BlockMatching(groupValues, blockDefinition);
                            } else {
                                blockMatching = new BlockMatching(new ArrayList<>(), blockDefinition);
                            }
                            foundStartBlocks.put(matcher.start(), blockMatching);
                            foundIndices.add(matcher.start());
                        }
                    }
                }

                // check for closing node
//                if (!checkForClosingNode(curOpenNodes, line, line.length(), content)) return null;

                // process found startBlocks
                int lastFoundIdx = 0;
                for (Integer foundIndice : foundIndices) {

                    // check for closing nodes between last match and current match
                    int lastEndCheckIdx = checkForClosingNode(curOpenNodes, line, lastFoundIdx, foundIndice + 1, content);
                    while (lastEndCheckIdx < foundIndice) {
                        lastEndCheckIdx = checkForClosingNode(curOpenNodes, line, lastEndCheckIdx + 1, foundIndice + 1, content);
                    }
                    lastFoundIdx = foundIndice;

                    // process next found start block
                    if (foundStartBlocks.get(foundIndice) != null) {

                        // open block
                        BlockMatching startBlock = foundStartBlocks.get(foundIndice);
                        NonTerminalNode newNode;

                        Matcher lineMatcher = startBlock.getBlockDefinition().getStartRegex().matcher(content);
                        if(lineMatcher.find()) {
                            content = new StringBuilder(lineMatcher.replaceAll(""));
                        }

                        if (startBlock.getBlockDefinition().isMatchGroups()) {
                            newNode = new NonTerminalNode(startBlock.getBlockDefinition().getName(), startBlock.blockDefinition, startBlock.getMatchedGroupValues());
                        } else {
                            newNode = new NonTerminalNode(startBlock.getBlockDefinition().getName(), startBlock.blockDefinition);
                        }

                        if(content.length() > 0 && !StringUtils.isWhitespace(content) && curOpenNodes.size() > 0) {
                            String stringContent = content.toString().trim();
                            Matcher contentStartBlockMatcher = Pattern.compile(startBlock.getBlockDefinition().getStartRegexString(),Pattern.MULTILINE).matcher(stringContent);
                            if(contentStartBlockMatcher.find()) {
                                stringContent = contentStartBlockMatcher.replaceAll("");
                            }
                            curOpenNodes.peek().appendContent(stringContent.trim());
                        }
                        content.setLength(0);

                        curOpenNodes.peek().addChild(new ChildRelation(newNode));
                        curOpenNodes.push(newNode);
                    }
                }

                // check for closing nodes
                int lastEndCheckIdx = checkForClosingNode(curOpenNodes, line, lastFoundIdx, line.length(), content);
                while (lastEndCheckIdx < line.length()) {
                    lastEndCheckIdx = checkForClosingNode(curOpenNodes, line, lastEndCheckIdx + 1, line.length(), content);
                }
            }
        } // end read lines

        br.close();
        fr.close();

        if (!curOpenNodes.peek().getLabel().equals(ROOT_NODE) && curOpenNodes.size() == 1) {
            System.err.println("There are still some open blocks at the end of the file! (" + curOpenNodes.peek().getLabel() + ")");
        }

        if(ParameterSettings.INFO_OUTPUT)
            System.out.println("Base structure for: " + filePath.substring(filePath.lastIndexOf("/") + 1) + "\n" + rootNode.subTreeToString());

        return rootNode;
    }

    private int checkForClosingNode(Deque<NonTerminalNode> curOpenNodes, String line, int minIdx, int maxIdx, StringBuilder curContent) {
        if(minIdx >= line.length()) {
            return line.length();
        }
        Optional<NonTerminalNode> optional = curOpenNodes.stream().filter(block -> block.getBlockDefinition().getEndRegexString() != null).findFirst();
        if (optional.isPresent()) {
            NonTerminalNode curOpenNode = optional.get();
            BlockDefinition curOpenBlock = curOpenNode.getBlockDefinition();
            if (!curOpenNode.getLabel().equals(ROOT_NODE) && curOpenBlock.matchesEndRegex(line.substring(minIdx))) {

                Matcher endBlockMatcher = curOpenBlock.getEndRegex().matcher(line);
                endBlockMatcher.find(minIdx);
                if (endBlockMatcher.start() < maxIdx) {
                    int foundEndIdx = endBlockMatcher.start();
                    // found closing block of curOpenNode before any other block was started -> check if it matches the identifiers
                    if (curOpenBlock.isMatchGroups()) {

                        List<String> groupValues = new ArrayList<>();
                        for (int i = 1; i <= endBlockMatcher.groupCount(); i++) {
                            groupValues.add(endBlockMatcher.group(i));
                        }
                        if (!ListUtils.isEqualList(curOpenNode.getIdentifiers(), groupValues)) {
                            System.err.println("INVALID end detected in: \"" + line + "\" expected: " +
                                    (curOpenNode.getIdentifiers().size() > 0 ? curOpenNode.getIdentifiers().get(0) : "") + " but found: " +
                                    (groupValues.size() > 0 ? groupValues.get(0) : ""));
                            return -1;
                        }
                    }
                    String stringContent = curContent.toString();
                    curContent.setLength(0);
                    Matcher contentEndBlockMatcher = Pattern.compile(curOpenBlock.getEndRegexString(),Pattern.MULTILINE).matcher(stringContent);

                    if(contentEndBlockMatcher.find()) {
                        stringContent = contentEndBlockMatcher.replaceAll("");
                    }

                    // close block
                    while (!curOpenNodes.peek().equals(curOpenNode)) {
                        curOpenNodes.peek().appendContent(stringContent);
                        stringContent = null;
                        curOpenNodes.pop();
                    }
                    curOpenNodes.peek().appendContent(stringContent);
                    stringContent = null;
                    curOpenNodes.pop();
                    return endBlockMatcher.end();
                }
            }
        }
        return maxIdx;
    }

    /**
     * Private class to capture current open blocks
     */
    private class BlockMatching {
        private final List<String> matchedGroupValues;
        private final BlockDefinition blockDefinition;

        private BlockMatching(List<String> matchedGroupValues, BlockDefinition blockDefinition) {
            this.matchedGroupValues = matchedGroupValues;
            this.blockDefinition = blockDefinition;
        }

        public List<String> getMatchedGroupValues() {
            return matchedGroupValues;
        }

        public BlockDefinition getBlockDefinition() {
            return blockDefinition;
        }
    }
}
