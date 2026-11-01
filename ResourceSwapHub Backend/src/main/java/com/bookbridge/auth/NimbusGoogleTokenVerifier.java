package com.bookbridge.auth;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.bookbridge.config.AppProperties;
import com.bookbridge.shared.error.ApiException;

@Component
public class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {
    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final AppProperties properties;
    private volatile NimbusJwtDecoder decoder;

    public NimbusGoogleTokenVerifier(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public GoogleIdentity verify(String credential) {
        if (!properties.googleEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Google sign-in is not configured on this server.");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(credential);
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized("Google sign-in could not be verified. Please try again.");
        }
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        String email = jwt.getClaimAsString("email");
        String subject = jwt.getSubject();
        if (!Boolean.TRUE.equals(verified) || email == null || email.isBlank() || subject == null) {
            throw ApiException.unauthorized("Your Google account email is not verified.");
        }
        return new GoogleIdentity(
                subject,
                email,
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture")
        );
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder existing = decoder;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (decoder == null) {
                String clientId = properties.googleClientId();
                NimbusJwtDecoder created = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
                OAuth2TokenValidator<Jwt> issuerValidator = new JwtClaimValidator<String>(
                        "iss", iss -> iss != null && ISSUERS.contains(iss));
                OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                        "aud", aud -> aud != null && aud.contains(clientId));
                OAuth2TokenValidator<Jwt> emailValidator = new JwtClaimValidator<Boolean>(
                        "email_verified", Boolean.TRUE::equals);
                created.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        issuerValidator,
                        audienceValidator,
                        emailValidator
                ));
                decoder = created;
            }
            return decoder;
        }
    }
}
