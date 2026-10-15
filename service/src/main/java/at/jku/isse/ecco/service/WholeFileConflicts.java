package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Files stored whole (the file adapter: binaries, files of no other adapter such as a Makefile) of
 * which a checkout combines more than one version: there is nothing to merge, and the checkout
 * failed as a whole (known gap #54). Such a file is not written - FileWriter skips it - and is
 * reported as a CONFLICT naming the commits each version comes from.
 */
final class WholeFileConflicts {

	private WholeFileConflicts() {
	}

	/** Adds a description of each such file in [checkout]'s tree to its conflicts. */
	static void find(Checkout checkout, Repository repository) {
		if (checkout.getNode() == null)
			return;
		find(checkout.getNode(), repository, checkout.getConflicts());
	}

	private static void find(Node node, Repository repository, List<String> conflicts) {
		for (Node child : node.getChildren()) {
			if (child.getArtifact() != null && child.getArtifact().getData() instanceof PluginArtifactData plugin) {
				if (DispatchReader.FILE_PLUGIN_ID.equals(plugin.getPluginId()) && child.getChildren().size() > 1)
					conflicts.add(describe(plugin, child, repository));
			} else {
				find(child, repository, conflicts);
			}
		}
	}

	private static String describe(PluginArtifactData plugin, Node file, Repository repository) {
		List<String> versions = new ArrayList<>();
		for (Node version : file.getChildren())
			versions.add(commitsOf(version, repository));
		return plugin.getPath() + " has " + versions.size() + " versions in this combination, not written: "
				+ String.join("; ", versions) + " -- suggested fix: check out a configuration with one of them, or commit the merged file";
	}

	/** The commits a version of a file comes from, e.g. {@code "commit 3f2a9c1e 'second'"}. */
	private static String commitsOf(Node version, Repository repository) {
		Association association = version.getArtifact() == null || version.getArtifact().getContainingNode() == null
				? null : version.getArtifact().getContainingNode().getContainingAssociation();
		List<String> commits = new ArrayList<>();
		if (association != null)
			for (Commit commit : repository.getCommits())
				if (commit.containsAssociation(association))
					commits.add("commit " + commit.getId().substring(0, Math.min(8, commit.getId().length())) + " '" + commit.getCommitMessage() + "'");
		return commits.isEmpty() ? "a version of unknown origin" : "from " + String.join(", ", commits);
	}
}
