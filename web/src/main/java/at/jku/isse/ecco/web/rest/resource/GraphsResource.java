package at.jku.isse.ecco.web.rest.resource;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.web.rest.EccoApplication;
import at.jku.isse.ecco.web.rest.dto.ArtifactsGraphDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Path("/graph")
public class GraphsResource {

	private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesResource.class);

	@Context
	private Application application;

	@Context
	private Configuration configuration;


	@GET
	@Path("/artifacts")
	@Produces(MediaType.APPLICATION_JSON)
	public ArtifactsGraphDTO getArtifactsGraph(@QueryParam("maxChildren") int maxChildren) {
		if (!(this.application instanceof EccoApplication))
			throw new RuntimeException("No or wrong application object injected.");

		EccoService eccoService = ((EccoApplication) this.application).getEccoService();
		this.service = eccoService;

		LOGGER.info("getArtifactsGraph(maxChildren: " + maxChildren + ")");

		// TODO: cache the graph somewhere (in the application? or even the service?)


		// compute graph
		if (maxChildren > 0)
			this.maxChildren = maxChildren;
		this.updateGraph();
		return this.graph;
	}


	private EccoService service;
	private ArtifactsGraphDTO graph;

	private void updateGraph() {
		this.graph = new ArtifactsGraphDTO();

		this.maxSuccessorsCount = 0;
		this.maxDepth = 0;

		// traverse trees and add nodes
		LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
		for (Association association : this.service.getRepository().getAssociations()) {
			compRootNode.addOrigNode(association.getRootNode());
		}
		this.traverseTree(compRootNode, 0);

		this.graph.setMaxNumArtifacts(this.maxSuccessorsCount);
		this.graph.setMaxDepth(this.maxDepth);
	}


	private int maxChildren = 100;

	private int maxSuccessorsCount = 0;
	private int maxDepth = 0;

	private void groupArtifactsByAssocRec(at.jku.isse.ecco.tree.Node eccoNode, Map<Association, Integer> groupMap) {
		for (at.jku.isse.ecco.tree.Node eccoChildNode : eccoNode.getChildren()) {
			if (eccoChildNode.getArtifact() != null) {
				Association childContainingAssociation = eccoChildNode.getArtifact().getContainingNode().getContainingAssociation();
				if (childContainingAssociation != null) {
					if (groupMap.containsKey(childContainingAssociation)) {
						int groupCount = groupMap.get(childContainingAssociation);
						groupCount++;
						groupMap.put(childContainingAssociation, groupCount);
					} else {
						groupMap.put(childContainingAssociation, 1);
					}
				}
			}
			this.groupArtifactsByAssocRec(eccoChildNode, groupMap);
		}
	}

	private ArrayList<ArtifactsGraphDTO.NodeDTO> nodes;
	private ArrayList<ArtifactsGraphDTO.EdgeDTO> edges;

	private ArtifactsGraphDTO.NodeDTO traverseTree(at.jku.isse.ecco.tree.Node eccoNode, int depth) {
		String assocId;

		ArtifactsGraphDTO.NodeDTO graphNode = null;
		if (eccoNode.getArtifact() != null) {

			//graphNode = this.graph.addNode(this.artifactCount);
			graphNode = this.graph.addNode();

			graphNode.setNumArtifacts(1);
			graphNode.setLabel(eccoNode.toString());
			graphNode.setDepth(depth);
			assocId = eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId();
			graphNode.setAssociationId(assocId);

			if (this.maxDepth < depth)
				this.maxDepth = depth;

			if (eccoNode.getChildren().size() >= maxChildren) {
				// group children by association
				Map<Association, Integer> groupMap = new HashMap<>();
				this.groupArtifactsByAssocRec(eccoNode, groupMap);
				// add one child node per group
				for (Map.Entry<Association, Integer> entry : groupMap.entrySet()) {
					//ArtifactsGraphDTO.NodeDTO graphChildNode = this.graph.addNode(this.artifactCount);
					ArtifactsGraphDTO.NodeDTO graphChildNode = this.graph.addNode();

					graphChildNode.setLabel("[" + entry.getValue() + "]");
					graphChildNode.setNumArtifacts(entry.getValue());
					graphChildNode.setDepth(depth + 1);
					graphChildNode.setAssociationId(entry.getKey().getId());

					if (this.maxSuccessorsCount < entry.getValue())
						this.maxSuccessorsCount = entry.getValue();

					ArtifactsGraphDTO.EdgeDTO edge = this.graph.addEdge(graphNode, graphChildNode);
				}
			}

			if (eccoNode.getArtifact().getData() instanceof PluginArtifactData) {
				graphNode.setLabel(((PluginArtifactData) eccoNode.getArtifact().getData()).getPath().toString());
			} else if (eccoNode.getArtifact().getData() instanceof DirectoryArtifactData) {
				graphNode.setLabel(((DirectoryArtifactData) eccoNode.getArtifact().getData()).getPath().toString());
			}
		}


		if (eccoNode.getChildren().size() < maxChildren) {
			for (at.jku.isse.ecco.tree.Node eccoChildNode : eccoNode.getChildren()) {
				ArtifactsGraphDTO.NodeDTO graphChildNode = this.traverseTree(eccoChildNode, depth + 1);

				if (graphChildNode != null && graphNode != null) {
					ArtifactsGraphDTO.EdgeDTO edge = this.graph.addEdge(graphNode, graphChildNode);

				}
			}
		}

		return graphNode;
	}

}
