# Key Establishment Protocol

## Overview
Ephemeral ECDH (P-256) key agreement between client and server to establish AES-256 session keys.

## Protocol Flow
1. **Client Hello**: Client generates ephemeral ECDH key pair, sends public key to server
2. **Server Hello**: Server authenticates itself, generates ephemeral ECDH key pair, sends public key + signature
3. **Key Derivation**: Both derive shared secret via ECDH, use HKDF-SHA-256 to derive session keys
4. **Key Binding**: Session key bound to user session ID
5. **Directional Keys**: Separate keys for client->server and server->client

## Security Properties
- Forward secrecy via ephemeral keys
- Server authentication via signed key exchange
- Key material never transmitted
- Session binding prevents cross-user reuse

## Key Derivation
- Shared secret from ECDH P-256
- HKDF with info = "ResourceSwapHub-v1-{sessionId}-{direction}"
- Salt = empty or fixed per protocol version
- Output = 32 bytes for AES-256
