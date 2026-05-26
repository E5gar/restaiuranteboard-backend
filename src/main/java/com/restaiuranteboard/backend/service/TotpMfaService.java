package com.restaiuranteboard.backend.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TotpMfaService {

    private static final int WINDOW_SIZE = 3;

    private final GoogleAuthenticator googleAuthenticator;
    private final String issuer;

    public TotpMfaService(@Value("${app.mfa.issuer}") String issuer) {
        this.issuer = issuer;
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30))
                .setWindowSize(WINDOW_SIZE)
                .build();
        this.googleAuthenticator = new GoogleAuthenticator(config);
    }

    public String generateSecret() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        return key.getKey();
    }

    public String buildOtpAuthUri(String email, String secret) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthURL(issuer, email, new GoogleAuthenticatorKey.Builder(secret).build());
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null) {
            return false;
        }
        String digits = code.replaceAll("\\D", "");
        if (digits.length() != 6) {
            return false;
        }
        try {
            return googleAuthenticator.authorize(secret, Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
