package at.jku.isse.ecco.web.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.*;
import java.io.IOException;

@Provider
public class CORSFilter implements ContainerResponseFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CORSFilter.class);
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, crossdomain");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");

    }
}
