# Request Encryption Protocol

## Algorithm
- **Cipher**: AES-256-GCM
- **Key size**: 256 bits
- **Nonce**: 96 bits (12 bytes), cryptographically random, never reused
- **Tag**: 128 bits
- **KDF**: HKDF-SHA-256

## Encrypted Envelope Format
```json
{
  "version": 1,
  "keyId": "session-uuid",
  "nonce": "base64url",
  "timestamp": 1234567890,
  "requestId": "uuid",
  "ciphertext": "base64url",
  "tag": "base64url" // or included in GCM output
}
```

## Additional Authenticated Data (AAD)
AAD includes: version, keyId, timestamp, requestId to prevent metadata tampering.

## Flow
1. Client establishes session key via ECDH
2. For sensitive requests: serialize payload to JSON
3. Generate random nonce
4. Compute AAD from envelope metadata
5. Encrypt with AES-GCM using session key + nonce + AAD
6. Send envelope as request body to encrypted endpoint or wrapped
7. Server validates metadata, checks replay, decrypts with session key
8. Pass decrypted payload to controller

## What is Encrypted
- Authentication: login, register (bootstrap special handling)
- Profile updates
- Private messages
- Listings create/update
- Sensitive user data

## What is NOT Encrypted
- Public GET requests (discovery, public listings)
- Institution search (public)
- Static assets
- Health checks
