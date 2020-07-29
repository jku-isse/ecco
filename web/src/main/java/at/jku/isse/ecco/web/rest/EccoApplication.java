package at.jku.isse.ecco.web.rest;

import at.jku.isse.ecco.service.EccoService;
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

	private final EccoService eccoService = new EccoService();
	public EccoApplication() {
		packages(true, "at.jku.isse.ecco.web");
	}

	public EccoService getEccoService() {
		return this.eccoService;
	}
	public void open(String repositoryDir) {
		this.eccoService.setRepositoryDir(Paths.get(repositoryDir));
		this.eccoService.open();
	}
	public void init(String repositoryDir) {
		this.eccoService.setRepositoryDir(Paths.get(repositoryDir));
		this.eccoService.init();
	}
	public void close() {
		this.eccoService.close();
	}

}
