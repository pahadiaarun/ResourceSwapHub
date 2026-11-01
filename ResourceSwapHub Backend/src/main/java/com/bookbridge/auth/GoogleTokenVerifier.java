package com.bookbridge.auth;

public interface GoogleTokenVerifier {
    GoogleIdentity verify(String credential);
}
