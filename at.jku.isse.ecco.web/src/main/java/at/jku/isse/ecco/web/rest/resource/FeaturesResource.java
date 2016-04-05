package at.jku.isse.ecco.web.rest.resource;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.web.rest.EccoApplication;
import at.jku.isse.ecco.web.rest.EccoResource;
import at.jku.isse.ecco.web.rest.dto.FeatureDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;

@Path("/feature")
public class FeaturesResource extends EccoResource {

	private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesResource.class);

	@Context
	private Application application;

	@Context
	private Configuration configuration;


	@GET
	//@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public FeatureDTO[] getFeatures(@QueryParam("filter") String filter) {
		if (!(this.application instanceof EccoApplication))
			throw new RuntimeException("No or wrong application object injected.");

		EccoService eccoService = ((EccoApplication) this.application).getEccoService();

		LOGGER.info("getFeatures(filter: " + filter + ")");

		// TODO: implement feature filtering in the feature dao (using db mechanisms) and expose it via the ecco service for better performance!

		ArrayList<FeatureDTO> features = new ArrayList<>();
		for (Feature feature : eccoService.getFeatures()) {
			if (filter == null || feature.getName().contains(filter) || feature.getDescription().contains(filter)) {
				FeatureDTO featureDTO = new FeatureDTO();
				featureDTO.setName(feature.getName());
				featureDTO.setDescription(feature.getDescription());
				features.add(featureDTO);
			}
		}

		return features.toArray(new FeatureDTO[features.size()]);
	}


	@GET
	@Path("/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	public FeatureDTO getFeature(@PathParam("name") String name) {
		if (!(this.application instanceof EccoApplication))
			throw new RuntimeException("No or wrong application object injected.");

		EccoService eccoService = ((EccoApplication) this.application).getEccoService();

		LOGGER.info("getFeature(name: " + name + ")");

		// TODO: implement feature search in the feature dao (using db mechanisms) and expose it via the ecco service for better performance!

		for (Feature feature : eccoService.getFeatures()) {
			if (feature.getName().equals(name)) {
				FeatureDTO featureDTO = new FeatureDTO();
				featureDTO.setName(feature.getName());
				featureDTO.setDescription(feature.getDescription());
				return featureDTO;
			}
		}

		return null;
	}

}
