package at.jku.isse.ecco.web.rest;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.server.CorsFilter;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;
import java.nio.file.Paths;

@ApplicationPath("ecco")
public class EccoApplication extends ResourceConfig {

	private EccoService eccoService = new EccoService();

	public EccoApplication() {
		packages("at.jku.isse.ecco.web.rest");

		property("eccoService", this.eccoService);

		register(CorsFilter.class);
	}

	public EccoService getEccoService() {
		return this.eccoService;
	}

	public void init(String repositoryDir) {
		this.eccoService.setRepositoryDir(Paths.get(repositoryDir));
		this.eccoService.open();
	}

	public void destroy() {
		this.eccoService.close();
	}

}
