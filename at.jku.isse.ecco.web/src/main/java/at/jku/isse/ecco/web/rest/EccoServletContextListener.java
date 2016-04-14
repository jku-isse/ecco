package at.jku.isse.ecco.web.rest;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;

@WebListener
public class EccoServletContextListener implements ServletContextListener {

	@Context
	private Application application;

	@Context
	public Configuration configuration;

	public void contextInitialized(ServletContextEvent event) {
		if (!(this.application instanceof EccoApplication))
			throw new RuntimeException("No or wrong application object injected.");

		ServletContext servletContext = event.getServletContext();
		String repositoryDir = servletContext.getInitParameter("repositoryDir");
		if (repositoryDir == null)
			throw new RuntimeException("No repository directory provided.");

		((EccoApplication) this.application).init(repositoryDir);
	}

	public void contextDestroyed(ServletContextEvent event) {
		if (!(this.application instanceof EccoApplication))
			throw new RuntimeException("No or wrong application object injected.");

		((EccoApplication) this.application).destroy();
	}

}
