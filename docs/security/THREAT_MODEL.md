# Threat Model - Request Encryption Layer

## Assets
- Authentication credentials (passwords, tokens)
- Personal profile data (PII)
- Private messages
- Session tokens
- User identities

## Threats Considered
- **MITM attacks**: Mitigated by TLS; app-layer encryption adds defense-in-depth
- **Replay attacks**: Prevented via unique request IDs, timestamps, nonce tracking
- **Tampering**: Prevented by AES-GCM authentication tags
- **Key compromise**: Ephemeral keys, session binding, rotation
- **Session hijacking**: Key bound to authenticated user session
- **Cross-user attacks**: Session keys scoped per user/session

## Assumptions
- TLS is used in production
- Server private keys for key exchange are protected
- Browser supports Web Crypto API
- Java Cryptography extension available

## Limitations
- Browser-based crypto has limitations
- Key exchange still requires trust in server
- Not a replacement for proper authentication/authorization
