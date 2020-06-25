package at.jku.isse.ecco.web.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class NewCrossOriginResourceSharingFilter implements ContainerResponseFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewCrossOriginResourceSharingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, crossdomain");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");

        LOGGER.info("NEW REQUEST IN NEWCROSSORIGINFILTER INCOMING!!!");
        LOGGER.info(requestContext.getHeaders().toString());
        LOGGER.info("NEW RESPONSE IN NEWCROSSORIGINFILTER INCOMING!!!");
        LOGGER.info(responseContext.getHeaders().toString());
    }
}