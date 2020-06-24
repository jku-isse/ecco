package at.jku.isse.ecco.web.rest;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.repository.FeatureRepository;
import at.jku.isse.ecco.web.provider.RepositoryProvider;
import org.glassfish.jersey.server.ResourceConfig;
import java.nio.file.Paths;

/*
ApplicationPath hat leider keine Auswirkung auf die URI auf der die API laufen wird
siehe https://github.com/eclipse-ee4j/jersey/issues/4205
Issue ist bisher auch nicht closed hat auch keinen Workaround...
 */
//@ApplicationPath("ecco")
public class EccoApplication extends ResourceConfig {

	private EccoService eccoService = new EccoService();

	private String repositoryDirectory = "";

	public EccoApplication() {
		packages(true, "at.jku.isse.ecco.web");
		register(new RepositoryProvider());
	}

	public EccoService getEccoService() {
		return this.eccoService;
	}

	public void open() {
		this.eccoService.open();
	}

	public void init(String repositoryDir) {
		this.eccoService.setRepositoryDir(Paths.get(repositoryDir));
		this.eccoService.open();
	}

	public void close() {
		this.eccoService.close();
	}

}
