package at.jku.isse.ecco.web.rest.dto;

import java.util.ArrayList;

public class ArtifactsGraphDTO {

	private int numNodes = 0;
	private int maxDepth = 0;
	private int maxNumArtifacts = 0;

	public int getNumNodes() {
		return numNodes;
	}

	public void setNumNodes(int numNodes) {
		this.numNodes = numNodes;
	}

	public int getMaxDepth() {
		return maxDepth;
	}

	public void setMaxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}

	public int getMaxNumArtifacts() {
		return maxNumArtifacts;
	}

	public void setMaxNumArtifacts(int maxNumArtifacts) {
		this.maxNumArtifacts = maxNumArtifacts;
	}

	//	private EdgeDTO[] edges;
//
//	private NodeDTO[] nodes;
//
//	public NodeDTO[] getNodes() {
//		return nodes;
//	}
//
//	public void setNodes(NodeDTO[] nodes) {
//		this.nodes = nodes;
//	}
//
//	public EdgeDTO[] getEdges() {
//		return edges;
//	}
//
//	public void setEdges(EdgeDTO[] edges) {
//		this.edges = edges;
//	}


	private ArrayList<EdgeDTO> edges = new ArrayList<>();

	private ArrayList<NodeDTO> nodes = new ArrayList<>();

	public ArrayList<NodeDTO> getNodes() {
		return nodes;
	}

	public void setNodes(ArrayList<NodeDTO> nodes) {
		this.nodes = nodes;
	}

	public ArrayList<EdgeDTO> getEdges() {
		return edges;
	}

	public void setEdges(ArrayList<EdgeDTO> edges) {
		this.edges = edges;
	}


	public EdgeDTO addEdge(NodeDTO n1, NodeDTO n2) {
		EdgeDTO edge = new EdgeDTO();
		edge.setSource(n1.getId());
		edge.setTarget(n2.getId());
		this.edges.add(edge);
		return edge;
	}

	public NodeDTO addNode() {
		NodeDTO node = new NodeDTO();
		node.setId(this.numNodes);
		this.nodes.add(node);
		this.numNodes++;
		return node;
	}


	public class EdgeDTO {
		private int source;
		private int target;

		public int getTarget() {
			return target;
		}

		public void setTarget(int target) {
			this.target = target;
		}

		public int getSource() {
			return source;
		}

		public void setSource(int source) {
			this.source = source;
		}
	}


	public class NodeDTO {
		private int id;
		private String associationId;
		private int numArtifacts;
		private int depth;
		private String label;

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getAssociationId() {
			return associationId;
		}

		public void setAssociationId(String associationId) {
			this.associationId = associationId;
		}

		public int getNumArtifacts() {
			return numArtifacts;
		}

		public void setNumArtifacts(int numArtifacts) {
			this.numArtifacts = numArtifacts;
		}

		public int getDepth() {
			return depth;
		}

		public void setDepth(int depth) {
			this.depth = depth;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}
	}

}
