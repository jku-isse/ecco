package at.jku.isse.ecco.rest;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;

@OpenAPIDefinition(
        info = @Info(
                title = "ECCO RESTService",
                version = "0.0.1",
                description = "Provides HTTP endpoints to an ECCO instance"
        )
)
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
