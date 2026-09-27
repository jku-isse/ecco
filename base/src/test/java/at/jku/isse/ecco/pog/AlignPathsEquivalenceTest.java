package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph.Node;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * PartialOrderGraph.Op.alignPaths() used to be an LCS whose every DP cell held a full copy of a
 * sequence-number -> index map (O(n * m * LCS) time), which made committing a changed large ordered
 * artifact (e.g. a text file with a few thousand lines) take tens of seconds. It is now an int DP
 * matrix plus traceback. The alignment result feeds every POG merge, so the new implementation must
 * reproduce the old one EXACTLY - including which of several equally long alignments it picks when
 * content repeats. The old implementation is kept below, verbatim, as the oracle.
 */
public class AlignPathsEquivalenceTest {

	private static final String[] ALPHABET = {"a", "b", "c", "d", "e", "}", "{", ""};

	@Test
	@Timeout(120)
	public void matchesTheOriginalImplementationOnRandomPaths() {
		Random random = new Random(20260927L);
		PartialOrderGraph.Op pog = new SerPartialOrderGraph();
		for (int round = 0; round < 20_000; round++) {
			int alphabetSize = 1 + random.nextInt(ALPHABET.length);
			Node.Op[] thisNodes = randomPath(random, random.nextInt(25), alphabetSize, true);
			Node.Op[] otherNodes = randomPath(random, random.nextInt(25), alphabetSize, false);
			assertSameAlignment(referenceAlignPaths(thisNodes, otherNodes), pog.alignPaths(thisNodes, otherNodes), round);
		}
	}

	@Test
	@Timeout(120)
	public void matchesTheOriginalImplementationOnLongerMostlyEqualPaths() {
		Random random = new Random(42L);
		PartialOrderGraph.Op pog = new SerPartialOrderGraph();
		for (int round = 0; round < 200; round++) {
			int length = 50 + random.nextInt(250);
			List<String> base = new ArrayList<>();
			for (int i = 0; i < length; i++) base.add(ALPHABET[random.nextInt(ALPHABET.length)] + (random.nextInt(4) == 0 ? "" : String.valueOf(i)));
			List<String> edited = new ArrayList<>(base);
			for (int edit = 0; edit < 1 + length / 20; edit++) {
				int pos = random.nextInt(edited.size() + 1);
				switch (random.nextInt(3)) {
					case 0 -> edited.add(pos, "inserted" + edit);
					case 1 -> { if (pos < edited.size()) edited.remove(pos); }
					default -> { if (pos < edited.size()) edited.set(pos, ALPHABET[random.nextInt(ALPHABET.length)]); }
				}
			}
			Node.Op[] thisNodes = path(base, true);
			Node.Op[] otherNodes = path(edited, false);
			assertSameAlignment(referenceAlignPaths(thisNodes, otherNodes), pog.alignPaths(thisNodes, otherNodes), round);
		}
	}

	@Test
	public void matchesTheOriginalImplementationWhenSequenceNumbersRepeat() {
		// not expected in a consistent graph, but the old implementation's map semantics (a repeated
		// key overwrites instead of growing the LCS) must still be reproduced if it ever happens
		Random random = new Random(7L);
		PartialOrderGraph.Op pog = new SerPartialOrderGraph();
		for (int round = 0; round < 2_000; round++) {
			Node.Op[] thisNodes = randomPath(random, 1 + random.nextInt(15), 3, true);
			for (Node.Op node : thisNodes) node.setSequenceNumber(random.nextInt(4));
			Node.Op[] otherNodes = randomPath(random, random.nextInt(15), 3, false);
			assertSameAlignment(referenceAlignPaths(thisNodes, otherNodes), pog.alignPaths(thisNodes, otherNodes), round);
		}
	}

	private static void assertSameAlignment(MutableIntObjectMap<Node.Op> expected, MutableIntObjectMap<Node.Op> actual, int round) {
		assertEquals(expected.keySet(), actual.keySet(), "round " + round + ": matched sequence numbers differ");
		expected.forEachKeyValue((key, node) -> assertSame(node, actual.get(key), "round " + round + ": sequence number " + key + " matched to a different node"));
	}

	private static Node.Op[] randomPath(Random random, int length, int alphabetSize, boolean assignSequenceNumbers) {
		List<String> contents = new ArrayList<>();
		for (int i = 0; i < length; i++) contents.add(ALPHABET[random.nextInt(alphabetSize)]);
		Node.Op[] nodes = path(contents, assignSequenceNumbers);
		// head/tail-like nodes without an artifact never match
		if (length > 0 && random.nextInt(5) == 0) nodes[0] = new SerPartialOrderGraphNode(null);
		if (length > 1 && random.nextInt(5) == 0) nodes[length - 1] = new SerPartialOrderGraphNode(null);
		return nodes;
	}

	private static Node.Op[] path(List<String> contents, boolean assignSequenceNumbers) {
		List<Integer> sequenceNumbers = new ArrayList<>();
		for (int i = 0; i < contents.size(); i++) sequenceNumbers.add(i + 1);
		Collections.shuffle(sequenceNumbers, new Random(contents.size()));
		Node.Op[] nodes = new Node.Op[contents.size()];
		for (int i = 0; i < nodes.length; i++) {
			Artifact.Op<?> artifact = new SerArtifact<>(new TestArtifactData(contents.get(i)));
			nodes[i] = new SerPartialOrderGraphNode(artifact);
			if (assignSequenceNumbers) nodes[i].setSequenceNumber(sequenceNumbers.get(i));
		}
		return nodes;
	}


	// ---- the original implementation, verbatim, as the oracle ----

	private static MutableIntObjectMap<Node.Op> referenceAlignPaths(Node.Op[] thisNodesArray, Node.Op[] otherNodesArray) {
		if (thisNodesArray.length == 0 || otherNodesArray.length == 0) {
			return IntObjectMaps.mutable.empty();
		}
		MutableIntIntMap[] lastColumn;
		MutableIntIntMap[] currentColumn = new IntIntHashMap[otherNodesArray.length];
		int currentColumnNumber = 0;
		for (int i = 0; i < thisNodesArray.length; i++) {
			lastColumn = currentColumn;
			currentColumn = new IntIntHashMap[otherNodesArray.length];
			for (int j = 0; j < otherNodesArray.length; j++) {
				lcsStep(thisNodesArray, otherNodesArray, currentColumnNumber, j, lastColumn, currentColumn);
			}
			currentColumnNumber++;
		}
		MutableIntIntMap resultIndexMap = currentColumn[otherNodesArray.length - 1];
		MutableIntObjectMap<Node.Op> resultMap = IntObjectMaps.mutable.empty();
		if (resultIndexMap == null) {
			return resultMap;
		}
		resultIndexMap.forEachKey(key -> {
			int value = resultIndexMap.get(key);
			resultMap.put(key, otherNodesArray[value]);
		});
		return resultMap;
	}

	private static void lcsStep(Node.Op[] thisNodesArray, Node.Op[] otherNodesArray, int thisIndex, int otherIndex, MutableIntIntMap[] lastColumn, MutableIntIntMap[] currentColumn) {
		Node.Op thisNode = thisNodesArray[thisIndex];
		Node.Op otherNode = otherNodesArray[otherIndex];
		Artifact<?> thisArtifact = thisNode.getArtifact();
		Artifact<?> otherArtifact = otherNode.getArtifact();
		if (thisArtifact != null && thisArtifact.getData() != null && otherArtifact != null && thisArtifact.getData().equals(otherArtifact.getData())) {
			lcsMatchStep(thisNode.getSequenceNumber(), otherIndex, thisIndex, lastColumn, currentColumn);
		} else {
			lcsNonMatchStep(otherIndex, thisIndex, lastColumn, currentColumn);
		}
	}

	private static void lcsMatchStep(int sequenceNumber, int otherIndex, int thisIndex, MutableIntIntMap[] lastColumn, MutableIntIntMap[] currentColumn) {
		MutableIntIntMap sequenceNumberMap;
		if (thisIndex == 0 || otherIndex == 0) {
			sequenceNumberMap = new IntIntHashMap();
		} else {
			sequenceNumberMap = new IntIntHashMap(lastColumn[otherIndex - 1]);
		}
		sequenceNumberMap.put(sequenceNumber, otherIndex);
		currentColumn[otherIndex] = sequenceNumberMap;
	}

	private static void lcsNonMatchStep(int otherIndex, int thisIndex, MutableIntIntMap[] lastColumn, MutableIntIntMap[] currentColumn) {
		MutableIntIntMap sequenceNumberMap;
		MutableIntIntMap lastThisCurrentOtherMap = thisIndex == 0 ? null : lastColumn[otherIndex];
		MutableIntIntMap currentThisLastOtherMap = otherIndex == 0 ? null : currentColumn[otherIndex - 1];
		if (lastThisCurrentOtherMap == null && currentThisLastOtherMap == null) {
			sequenceNumberMap = new IntIntHashMap();
		} else if (currentThisLastOtherMap == null || (lastThisCurrentOtherMap != null && lastThisCurrentOtherMap.size() > currentThisLastOtherMap.size())) {
			sequenceNumberMap = new IntIntHashMap(lastThisCurrentOtherMap);
		} else {
			sequenceNumberMap = new IntIntHashMap(currentThisLastOtherMap);
		}
		currentColumn[otherIndex] = sequenceNumberMap;
	}
}
