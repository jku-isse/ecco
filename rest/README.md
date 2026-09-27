# Rest-API for Ecco

This part provides a Rest-API for ECCO  
The corresponding frontend can be found on [GitHub](https://github.com/MatthiasPreuner/ecco-client.git)

To run the API use the Gradle commands from the repository root:
- ecco-rest:build for the first initialization 
- ecco-rest:run to start the server

The server storage can be changed in the "Settings" class.  
Users can be added by changing the "DummyUserDB" class or add your own user database.
micronaut-cli.yml contains the config for possible CLI usage.

## API Documentation

This server provides an OpenAPI documentation as well as a Swagger UI.
After the server has started, it is accessible via http://localhost:8081/swagger-ui (the port can be changed with the PORT environment variable).
The raw OpenAPI documentation can be accessed via http://localhost:8081/swagger/ecco-restservice-0.0.1.yml.

## Additional documentation

- [Micronaut Guides](https://guides.micronaut.io/index.html)
- [Shadow Gradle Plugin](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow)
- [Micronaut HTTP Client documentation](https://docs.micronaut.io/latest/guide/index.html#httpClient)

### Reference Documentation

* [Official Gradle documentation](https://docs.gradle.org)
* [Micronaut user guide](https://docs.micronaut.io/latest/guide/)
* [Micronaut OpenAPI / Swagger](https://micronaut-projects.github.io/micronaut-openapi/latest/guide/)
* [Micronaut Security (JWT)](https://micronaut-projects.github.io/micronaut-security/latest/guide/)
