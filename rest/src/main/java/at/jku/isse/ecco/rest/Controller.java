package at.jku.isse.ecco.rest;

import io.micronaut.http.HttpResponse;


@Controller("/api")
public class Controller {

    @Get()
    public HttpResponse<?> getPersons() {
        return HttpResponse.status(HttpStatus.OK).body(this.personRepository.findAll());
    }

}
