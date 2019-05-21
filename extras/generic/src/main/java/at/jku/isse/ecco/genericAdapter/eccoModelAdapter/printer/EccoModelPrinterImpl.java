package at.jku.isse.ecco.genericAdapter.eccoModelAdapter.printer;


import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.builder.BuilderArtifactData;
import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.strategy.EccoModelBuilderStrategy;
import at.jku.isse.ecco.tree.Node;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Michael Jahn
 */
public class EccoModelPrinterImpl implements EccoModelPrinter {

	private static Logger LOGGER = Logger.getLogger(EccoModelPrinterImpl.class.getName());

	@Override
	public boolean printModelToFile(String filePath, Node eccoModelRoot, EccoModelBuilderStrategy builderStrategy) {

		PrintWriter out = null;
		try {
			out = new PrintWriter(filePath);
		} catch (IOException e) {
			System.err.println("IO Error while writing to file: " + filePath);
			if (out != null) {
				out.close();
			}
			return false;
		}

		String modelText = printModelToString(eccoModelRoot, builderStrategy);
		if (modelText != null && !modelText.isEmpty()) {
			out.println(modelText);
		} else {
			out.close();
			return false;
		}
		out.close();

		return true;
	}

	@Override
	public String printModelToString(Node eccoModelRoot, EccoModelBuilderStrategy builderStrategy) {
		builderStrategy.resetNextArtifactReferenceId();
		return printNodeToString(eccoModelRoot, builderStrategy).toString();
	}

	private StringBuilder printNodeToString(Node eccoModelNode, EccoModelBuilderStrategy builderStrategy) {
		StringBuilder nodeText = new StringBuilder();
		Artifact artifact = eccoModelNode.getArtifact();
		if (!(artifact.getData() instanceof BuilderArtifactData)) {
			LOGGER.warning("The artifact: " + ((BuilderArtifactData) artifact.getData()).getIdentifier() + " is not an BuilderArtifactData and can therefore not be handled by this printer implementation!");
		} else {
			String printValues = resolvePrintValues(artifact, builderStrategy);
			if (!printValues.isEmpty()) {
				nodeText.append(printValues + "\n");
			}
			Collection<Node> childNodes = eccoModelNode.getChildren();
			for (Node childNode : childNodes) {
				nodeText.append(printNodeToString(childNode, builderStrategy));
			}

		}
		return nodeText;
	}

	private String resolvePrintValues(Artifact artifact, EccoModelBuilderStrategy builderStrategy) {
		StringBuilder resolvedPrintValues = new StringBuilder();
		int nextUsesIdx = 0;
		List<ArtifactReference> uses = artifact.getUses();
		BuilderArtifactData artifactData = (BuilderArtifactData) artifact.getData();
		for (String printingValue : artifactData.getPrintingValues()) {
			if (BuilderArtifactData.RESOLVE_USES_REF_ID.equals(printingValue)) {
				if (uses == null || uses.size() <= nextUsesIdx) {
					LOGGER.warning("Missing reference for artifact: " + artifactData.getType() + " (" + artifactData.getIdentifier() + ")");
				} else {
					Artifact targetArtifact = uses.get(nextUsesIdx).getTarget();
					if (!(targetArtifact.getData() instanceof BuilderArtifactData)) {
						LOGGER.warning("The referenced artifact: " + ((BuilderArtifactData) targetArtifact.getData()).getIdentifier() + " is not an BuilderArtifactData and can therefore not be handled by this printer implementation!");
					} else {
						BuilderArtifactData builderTargetArtifact = (BuilderArtifactData) targetArtifact.getData();
						Object refId = builderTargetArtifact.getRefId() != null ? builderTargetArtifact.getRefId() : builderStrategy.getNextArtifactReferenceId();
						builderTargetArtifact.setRefId(refId);
						if (lastCharIsAlphaNumeric(resolvedPrintValues) && firstCharIsAlphaNumeric(refId.toString())) {
							resolvedPrintValues.append(" ");
						}
						resolvedPrintValues.append(refId.toString());
					}
					nextUsesIdx++;
				}
			} else if (BuilderArtifactData.RESOLVE_OWN_REF_ID.equals(printingValue)) {
				Object refId = artifactData.getRefId() != null ? artifactData.getRefId() : builderStrategy.getNextArtifactReferenceId();
				artifactData.setRefId(refId);
				if (lastCharIsAlphaNumeric(resolvedPrintValues) && firstCharIsAlphaNumeric(refId.toString())) {
					resolvedPrintValues.append(" ");
				}
				resolvedPrintValues.append(refId.toString());
			} else {
				if (lastCharIsAlphaNumeric(resolvedPrintValues) && firstCharIsAlphaNumeric(printingValue)) {
					resolvedPrintValues.append(" ");
				}
				resolvedPrintValues.append(printingValue);
			}
		}
		return resolvedPrintValues.toString();
	}

	private boolean firstCharIsAlphaNumeric(String s) {
		return s.length() > 0 && StringUtils.isAlphanumeric(s.substring(0, 1));
	}

	private boolean lastCharIsAlphaNumeric(StringBuilder resolvedPrintValues) {
		return resolvedPrintValues.length() > 0 && StringUtils.isAlphanumeric(resolvedPrintValues.substring(resolvedPrintValues.length() - 1));
	}
}
