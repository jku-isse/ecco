package at.jku.isse.ecco.web;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ApplicationState implements ServletContextListener {

	public void contextInitialized(ServletContextEvent event) {
		ServletContext c = event.getServletContext();
		System.out.println("TESTOUT: " + c.getInitParameter("repositoryDir"));
		if (c != null) {
			if (c.getInitParameter("repositoryDir") != null) {
				c.setAttribute("repositoryDir", c.getInitParameter("repositoryDir"));
			}
		}
	}

	public void contextDestroyed(ServletContextEvent event) {

	}

}
