package at.jku.isse.ecco.web.server;

import at.jku.isse.ecco.web.controller.ApplicationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class SomeOtherFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SomeOtherFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOGGER.info("NEW REQUEST INCOMING!");
        LOGGER.info(requestContext.getMethod());
        LOGGER.info(requestContext.getHeaders().toString());
    }
}
