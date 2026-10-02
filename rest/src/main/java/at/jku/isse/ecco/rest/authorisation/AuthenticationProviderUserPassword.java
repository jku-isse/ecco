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

import java.util.Optional;

@Singleton
public class AuthenticationProviderUserPassword implements AuthenticationProvider<HttpRequest<?>> {
    //Adapted from https://guides.micronaut.io/latest/micronaut-security-jwt-gradle-java.html
    private final RestSecurity security;

    public AuthenticationProviderUserPassword(RestSecurity security) {
        this.security = security;
    }

    @Override
    public Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> httpRequest, AuthenticationRequest<?, ?> authenticationRequest) {
        return Flux.create(emitter -> {
            String name = String.valueOf(authenticationRequest.getIdentity());
            Optional<User> user = security.findUser(name);
            // the same answer for an unknown user and a wrong password, so names can't be probed
            if (user.isPresent() && user.get().passwordMatches(String.valueOf(authenticationRequest.getSecret()))) {
                emitter.next(AuthenticationResponse.success(name, user.get().getRoles()));
                System.out.println(name + " logged in");
                emitter.complete();
            } else {
                emitter.error(AuthenticationResponse.exception("Invalid user name or password"));
            }
        }, FluxSink.OverflowStrategy.ERROR);
    }
}
