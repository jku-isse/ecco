package at.jku.isse.ecco.web.resource;

import at.jku.isse.ecco.web.dto.FeatureDTO;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/features")
public class FeaturesResource {

	@GET
	@Path("/all")
	@Produces(MediaType.APPLICATION_JSON)
	public FeatureDTO[] getFeatures() {
		FeatureDTO[] features = new FeatureDTO[10];

		features[0] = new FeatureDTO();
		features[0].setName("TEST");

		return features;
	}

}
