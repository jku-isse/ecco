package at.jku.isse.ecco.rest.authorisation;


import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Singleton
public class AuthenticationProviderUserPassword implements AuthenticationProvider {
    //Adapted from https://guides.micronaut.io/latest/micronaut-security-jwt-gradle-java.html

    DummyUserDB userDB = new DummyUserDB();

    @Override
    public Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> httpRequest, AuthenticationRequest<?, ?> authenticationRequest) {
        return Flux.create(emitter -> {
            if (userDB.findUser(authenticationRequest.getIdentity().toString()) != null) {
                User user = userDB.findUser(authenticationRequest.getIdentity().toString());
                if (authenticationRequest.getIdentity().equals(user.getName()) &&
                        authenticationRequest.getSecret().equals(user.getPassword())) {
                    emitter.next(AuthenticationResponse.success((String) authenticationRequest.getIdentity(), user.getRoles()));
                    System.out.println((authenticationRequest.getIdentity()).toString() + " logged-in");
                    emitter.complete();
                } else {
                    emitter.error(AuthenticationResponse.exception("Wrong password"));
                }
            } else {
                emitter.error(AuthenticationResponse.exception("User not found"));
            }
        }, FluxSink.OverflowStrategy.ERROR);
    }
}