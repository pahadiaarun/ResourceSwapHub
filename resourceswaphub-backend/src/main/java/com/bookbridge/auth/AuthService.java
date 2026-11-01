package com.bookbridge.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.auth.dto.AuthResponse;
import com.bookbridge.auth.dto.GoogleLoginRequest;
import com.bookbridge.auth.dto.LoginRequest;
import com.bookbridge.auth.dto.RegisterRequest;
import com.bookbridge.config.AppProperties;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserDto;
import com.bookbridge.user.UserRepository;

@Service
public class AuthService {
    public record IssuedAuth(String refreshToken, AuthResponse response) {
    }

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, AccessTokenService accessTokenService,
                       GoogleTokenVerifier googleTokenVerifier, AppProperties properties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.properties = properties;
    }

    @Transactional
    public IssuedAuth register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account already exists for this email.");
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.saveAndFlush(user);
        return issue(user);
    }

    @Transactional
    public IssuedAuth login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect."));
        if (user.getPasswordHash() == null) {
            throw ApiException.badRequest("OAUTH_ONLY", "This account uses Google sign-in. Continue with Google.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Email or password is incorrect.");
        }
        return issue(user);
    }

    @Transactional
    public IssuedAuth loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdentity identity = googleTokenVerifier.verify(request.credential());
        String email = normalizeEmail(identity.email());
        User user = userRepository.findByGoogleSubject(identity.subject()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (user != null && user.getGoogleSubject() != null
                    && !user.getGoogleSubject().equals(identity.subject())) {
                throw ApiException.conflict("GOOGLE_ACCOUNT_MISMATCH",
                        "This email is linked to a different Google account.");
            }
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setDisplayName(displayNameFrom(identity, email));
            }
            user.setGoogleSubject(identity.subject());
            if (user.getAvatarUrl() == null && identity.pictureUrl() != null) {
                user.setAvatarUrl(identity.pictureUrl());
            }
            user = userRepository.saveAndFlush(user);
        }
        return issue(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public IssuedAuth refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Your session has expired. Please sign in again.");
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Your session has expired. Please sign in again."));
        Instant now = Instant.now();
        if (token.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllActive(token.getUser().getId(), now);
            throw ApiException.unauthorized("Your session was no longer valid. Please sign in again.");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            token.setRevokedAt(now);
            throw ApiException.unauthorized("Your session has expired. Please sign in again.");
        }
        token.setRevokedAt(now);
        return issue(token.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
            }
        });
    }

    private IssuedAuth issue(User user) {
        String accessToken = accessTokenService.issue(user);
        String rawRefresh = generateRefreshToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(hash(rawRefresh));
        refresh.setExpiresAt(Instant.now().plus(properties.refreshTokenTtl()));
        refreshTokenRepository.save(refresh);
        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                properties.accessTokenTtl().toSeconds(),
                UserDto.from(user)
        );
        return new IssuedAuth(rawRefresh, response);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String displayNameFrom(GoogleIdentity identity, String email) {
        String name = identity.name();
        if (name == null || name.isBlank()) {
            int at = email.indexOf('@');
            name = at > 0 ? email.substring(0, at) : "ResourceSwapHub reader";
        }
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    public User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Your account could not be found."));
    }
}
