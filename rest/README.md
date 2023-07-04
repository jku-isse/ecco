# Rest-API for Ecco

This part provides a Rest-API for ECCO  
The corresponding frontend can be found on [GitHub](https://github.com/MatthiasPreuner/ecco-client.git)

To run the API use the Gradle commands under ecco-rest:
- ecco-rest:build for the first initialization 
- ecco-rest:run to start the server

The server storage can be changed in the "Setting" class.  
Users can be added by chancing the "DummyUserDB" class or add your own user database.
mirconaut-cli.yml contains the config for possible CLI usage.

## API Documentation

This server provides an OpenAPI documentation as well as a Swagger UI.
After the server has started, it is accessible via http://localhost:8080/swagger-ui.
The raw OpenAPI documentation can be accessed via http://localhost:8080/swagger/ecco-restservice-0.0.1.yml.

## Additional documentation

- [Micronaut Guides](https://guides.micronaut.io/index.html)
- [Shadow Gradle Plugin](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow)
- [Micronaut HTTP Client documentation](https://docs.micronaut.io/latest/guide/index.html#httpClient)

### Reference Documentation

For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.6.4/gradle-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/2.6.4/gradle-plugin/reference/html/#build-image)
* [Rest Repositories](https://docs.spring.io/spring-boot/docs/2.6.4/reference/htmlsingle/#howto-use-exposing-spring-data-repositories-rest-endpoint)
* [Spring Web](https://docs.spring.io/spring-boot/docs/2.6.4/reference/htmlsingle/#boot-features-developing-web-applications)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/docs/2.6.4/reference/htmlsingle/#using-boot-devtools)

### Guides

The following guides illustrate how to use some features concretely:

* [Accessing JPA Data with REST](https://spring.io/guides/gs/accessing-data-rest/)
* [Accessing Neo4j Data with REST](https://spring.io/guides/gs/accessing-neo4j-data-rest/)
* [Accessing MongoDB Data with REST](https://spring.io/guides/gs/accessing-mongodb-data-rest/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/bookmarks/)

### Additional Links

These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)

