package at.jku.isse.ecco.web.rest.resource;

import at.jku.isse.ecco.EccoService;
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

		LOGGER.info("getArtifactsGraph(maxChildren: " + maxChildren + ")");

		// TODO: cache the graph somewhere (in the application? or even the service?)

		// TODO: compute graph

		return null;
	}

}
