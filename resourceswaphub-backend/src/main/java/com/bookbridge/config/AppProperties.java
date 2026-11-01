package com.bookbridge.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookbridge")
public record AppProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String refreshCookieName,
        boolean cookieSecure,
        String frontendOrigin,
        List<String> corsAllowedOrigins,
        String storageDir,
        String googleClientId
) {
    public AppProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(7);
        }
        if (refreshCookieName == null || refreshCookieName.isBlank()) {
            refreshCookieName = "bb_refresh";
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
            corsAllowedOrigins = List.of("http://localhost:3000");
        }
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "./storage";
        }
    }

    public boolean googleEnabled() {
        return googleClientId != null && !googleClientId.isBlank();
    }
}
