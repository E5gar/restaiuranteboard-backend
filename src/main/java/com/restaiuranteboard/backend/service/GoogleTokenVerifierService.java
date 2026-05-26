package com.restaiuranteboard.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.restaiuranteboard.backend.dto.GoogleProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class GoogleTokenVerifierService {

    private final String clientId;
    private final String clientSecret;
    private final GoogleIdTokenVerifier idTokenVerifier;

    public GoogleTokenVerifierService(
            @Value("${app.google.client-id:}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret
    ) {
        this.clientId = clientId != null ? clientId.trim() : "";
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
        this.idTokenVerifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(this.clientId))
                .build();
    }

    public boolean isConfigured() {
        return !clientId.isBlank();
    }

    public Optional<GoogleProfile> resolveProfile(String idTokenString, String authorizationCode) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        if (idTokenString != null && !idTokenString.isBlank()) {
            return verifyIdToken(idTokenString.trim());
        }
        if (authorizationCode != null && !authorizationCode.isBlank()) {
            return exchangeCode(authorizationCode.trim());
        }
        return Optional.empty();
    }

    private Optional<GoogleProfile> verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = idTokenVerifier.verify(idTokenString);
            if (idToken == null) {
                return Optional.empty();
            }
            return Optional.of(toProfile(idToken.getPayload()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<GoogleProfile> exchangeCode(String code) {
        if (clientSecret.isBlank()) {
            return Optional.empty();
        }
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    "https://oauth2.googleapis.com/token",
                    clientId,
                    clientSecret,
                    code,
                    ""
            )
                    .setRedirectUri("postmessage")
                    .execute();
            String idTokenValue = tokenResponse.getIdToken();
            if (idTokenValue == null || idTokenValue.isBlank()) {
                return Optional.empty();
            }
            return verifyIdToken(idTokenValue);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static GoogleProfile toProfile(GoogleIdToken.Payload payload) {
        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email missing");
        }
        Boolean verified = payload.getEmailVerified();
        if (verified != null && !verified) {
            throw new IllegalArgumentException("email not verified");
        }
        String sub = payload.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("sub missing");
        }
        return new GoogleProfile(
                sub,
                email.trim().toLowerCase(),
                payload.get("given_name") != null ? String.valueOf(payload.get("given_name")) : null,
                payload.get("family_name") != null ? String.valueOf(payload.get("family_name")) : null
        );
    }
}
