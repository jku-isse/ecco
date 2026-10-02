# Rest-API for Ecco

This part provides a Rest-API for ECCO  
The corresponding frontend can be found on [GitHub](https://github.com/MatthiasPreuner/ecco-client.git)

To run the API use the Gradle commands from the repository root:
- ecco-rest:build for the first initialization 
- ecco-rest:run to start the server

The server keeps its repositories in the directory named by the `ECCO_STORAGE_DIR` environment variable (or the `ecco.storage-dir` system property). Without it, the directory is guessed: `/media/serverRepositories` in Docker, `/home/jenkins/host` for a `jenkins` user, otherwise the `examples` folder of the source tree. The server prints the directory it uses at startup.  

### Security configuration

The server refuses to start unless it has

- `JWT_GENERATOR_SIGNATURE_SECRET`: the secret that signs login tokens, at least 32 characters (not the published default), and
- `ECCO_REST_USERS_FILE`: a users file, one user per line as `name:password-hash:Role[,Role]` (roles `Admin`, `User`; lines starting with `#` are comments).

Print a password hash with `ecco-rest --hash-password` (from `build/install/ecco-rest/bin` after `./gradlew :ecco-rest:installDist`), for example:

```
alice:pbkdf2-sha256$210000$...$...:Admin,User
```

For local development only, `ECCO_REST_DEV=true` (or `-Decco.rest.dev=true`) starts the server with the default secret and demonstration users with published passwords (see `DummyUserDB`); `./gradlew :ecco-rest:run` and the tests do this.

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
