package at.jku.isse.ecco.web.rest;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.ArtefactGraphModel;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/*
ApplicationPath hat leider keine Auswirkung auf die URI auf der die API laufen wird
siehe https://github.com/eclipse-ee4j/jersey/issues/4205
Issue ist bisher auch nicht closed hat auch keinen Workaround...
 */
//@ApplicationPath("ecco")
public class EccoApplication extends ResourceConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(EccoApplication.class);
	private static final String ECCO_REPOSITORY_DIRECTORY = "/.ecco";

	private final EccoService eccoService = new EccoService();

	private ArtefactGraphModel backendGraph = null;
	private ArtefactGraphModel frontendGraph = null;

	public EccoApplication() {
		packages(true, "at.jku.isse.ecco.web");
		register(MultiPartFeature.class);
	}

	public EccoService getEccoService() {
		return this.eccoService;
	}
	public void open(String baseDirectory) {
		this.eccoService.setBaseDir(Paths.get(baseDirectory));
		this.eccoService.setRepositoryDir(Paths.get(baseDirectory + ECCO_REPOSITORY_DIRECTORY));
		this.eccoService.open();
	}
	public void init(String baseDirectory) {
		this.eccoService.setBaseDir(Paths.get(baseDirectory));
		this.eccoService.setRepositoryDir(Paths.get(baseDirectory + ECCO_REPOSITORY_DIRECTORY));
		this.eccoService.init();
	}
	public void close() {
		this.eccoService.close();
	}

	public ArtefactGraphModel getBackendGraph() {
		return backendGraph;
	}

	public void setBackendGraph(ArtefactGraphModel backendGraph) {
		this.backendGraph = backendGraph;
	}

	public ArtefactGraphModel getFrontendGraph() {
		return frontendGraph;
	}

	public void setFrontendGraph(ArtefactGraphModel frontendGraph) {
		this.frontendGraph = frontendGraph;
	}
}
