package at.jku.isse.ecco.web.rest.resource;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.web.rest.EccoApplication;
import at.jku.isse.ecco.web.rest.EccoResource;
import at.jku.isse.ecco.web.rest.dto.FeatureDTO;
import at.jku.isse.ecco.web.rest.dto.FeatureVersionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;

// TODO: implement feature search/filtering in the feature dao (using db mechanisms) and expose it via the ecco service for better performance!

@Path("/feature")
public class FeaturesResource extends EccoResource {

	private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesResource.class);

	@Context
	private Application application;

	@Context
	private Configuration configuration;


	private EccoService getService() {
		if (!(this.application instanceof EccoApplication))
			throw new RuntimeException("No or wrong application object injected.");

		EccoService eccoService = ((EccoApplication) this.application).getEccoService();

		return eccoService;
	}


	@GET
	//@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public FeatureDTO[] getFeatures(@QueryParam("filter") String filter) {
		EccoService eccoService = this.getService();

		LOGGER.info("getFeatures(filter: " + filter + ")");

		ArrayList<FeatureDTO> features = new ArrayList<>();
		for (Feature feature : eccoService.getRepository().getFeatures()) {
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
		EccoService eccoService = this.getService();

		LOGGER.info("getFeature(name: " + name + ")");

		for (Feature feature : eccoService.getRepository().getFeatures()) {
			if (feature.getName().equals(name)) {
				FeatureDTO featureDTO = new FeatureDTO();
				featureDTO.setName(feature.getName());
				featureDTO.setDescription(feature.getDescription());
				return featureDTO;
			}
		}

		throw new NotFoundException();
		//return null;
	}

	@GET
	@Path("/{name}/version")
	@Produces(MediaType.APPLICATION_JSON)
	public FeatureVersionDTO[] getFeatureVersions(@PathParam("name") String name) {
		EccoService eccoService = this.getService();

		LOGGER.info("getFeatureVersions(name: " + name + ")");

		ArrayList<FeatureVersionDTO> featuresVersions = new ArrayList<>();
		for (Feature feature : eccoService.getRepository().getFeatures()) {
			if (feature.getName().equals(name)) {
				for (FeatureRevision featureVersion : feature.getRevisions()) {
					FeatureVersionDTO featureVersionDTO = new FeatureVersionDTO();
					featureVersionDTO.setVersion(featureVersion.getId());
					featureVersionDTO.setDescription(featureVersion.getDescription());
					featuresVersions.add(featureVersionDTO);
				}
				return featuresVersions.toArray(new FeatureVersionDTO[featuresVersions.size()]);
			}
		}

		throw new NotFoundException();
		//return null;
	}

	@GET
	@Path("/{name}/version/{version}")
	@Produces(MediaType.APPLICATION_JSON)
	public FeatureVersionDTO getFeatureVersion(@PathParam("name") String name, @PathParam("version") String version) {
		EccoService eccoService = this.getService();

		LOGGER.info("getFeatureVersion(name: " + name + ", version: " + version + ") ");

		for (Feature feature : eccoService.getRepository().getFeatures()) {
			if (feature.getName().equals(name)) {
				for (FeatureRevision featureVersion : feature.getRevisions()) {
					if (featureVersion.getId().equals(version)) {
						FeatureVersionDTO featureVersionDTO = new FeatureVersionDTO();
						featureVersionDTO.setVersion(featureVersion.getId());
						featureVersionDTO.setDescription(featureVersion.getDescription());
						return featureVersionDTO;
					}
					return null;
				}
			}
		}

		//throw new WebApplicationException(404);
		throw new NotFoundException();
		//return null;
	}

}
