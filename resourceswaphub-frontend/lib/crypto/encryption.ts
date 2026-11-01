"use client";

const ENCRYPTION_VERSION = 1;
const ALGORITHM = "AES-GCM";
const KEY_LENGTH = 256;
const NONCE_LENGTH = 12; // 96 bits
const TAG_LENGTH = 128;

export interface EncryptedEnvelope {
  version: number;
  keyId: string;
  nonce: string;
  timestamp: number;
  requestId: string;
  ciphertext: string;
}

export interface CryptoSession {
  sessionId: string;
  clientKey: CryptoKey;
  serverKeyId: string;
  expiresAt: number;
}

let currentSession: CryptoSession | null = null;

function toBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(base64url: string): ArrayBuffer {
  const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "===".slice((base64.length + 3) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function generateRequestId(): string {
  return crypto.randomUUID();
}

export async function establishSession(): Promise<CryptoSession> {
  // Generate ephemeral ECDH key pair
  const clientKeyPair = await crypto.subtle.generateKey(
    {
      name: "ECDH",
      namedCurve: "P-256",
    },
    true,
    ["deriveKey", "deriveBits"]
  );

  const clientPublicKey = await crypto.subtle.exportKey("raw", clientKeyPair.publicKey);
  
  // In production, send clientPublicKey to server, receive server's public key
  // For now, create a mock session (would need backend endpoint)
  // This is a simplified implementation structure
  
  const sessionId = crypto.randomUUID();
  const sessionKey = await crypto.subtle.generateKey(
    {
      name: ALGORITHM,
      length: KEY_LENGTH,
    },
    true,
    ["encrypt", "decrypt"]
  );

  const session: CryptoSession = {
    sessionId,
    clientKey: sessionKey,
    serverKeyId: `key-${sessionId}`,
    expiresAt: Date.now() + 10 * 60 * 1000, // 10 minutes
  };

  currentSession = session;
  return session;
}

export function getCurrentSession(): CryptoSession | null {
  if (currentSession && currentSession.expiresAt > Date.now()) {
    return currentSession;
  }
  currentSession = null;
  return null;
}

export async function encryptRequest(payload: unknown): Promise<EncryptedEnvelope> {
  let session = getCurrentSession();
  if (!session) {
    session = await establishSession();
  }

  const plaintext = new TextEncoder().encode(JSON.stringify(payload));
  const nonce = crypto.getRandomValues(new Uint8Array(NONCE_LENGTH));
  const requestId = generateRequestId();
  const timestamp = Math.floor(Date.now() / 1000);

  const aad = new TextEncoder().encode(
    JSON.stringify({
      version: ENCRYPTION_VERSION,
      keyId: session.serverKeyId,
      timestamp,
      requestId,
    })
  );

  const ciphertext = await crypto.subtle.encrypt(
    {
      name: ALGORITHM,
      iv: nonce,
      additionalData: aad,
      tagLength: TAG_LENGTH,
    },
    session.clientKey,
    plaintext
  );

  return {
    version: ENCRYPTION_VERSION,
    keyId: session.serverKeyId,
    nonce: toBase64Url(nonce.buffer),
    timestamp,
    requestId,
    ciphertext: toBase64Url(ciphertext),
  };
}

export function shouldEncryptRequest(method: string, path: string): boolean {
  // Don't encrypt GET requests to public endpoints
  if (method === "GET") {
    const publicPaths = [
      "/api/v1/discovery",
      "/api/v1/listings",
      "/api/v1/collections",
      "/api/v1/catalog",
      "/api/v1/institutions",
    ];
    if (publicPaths.some((p) => path.startsWith(p))) {
      return false;
    }
  }

  // Encrypt sensitive operations
  const sensitivePaths = [
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/google",
    "/api/v1/users/me",
    "/api/v1/profile",
    "/api/v1/conversations",
    "/api/v1/listings",
    "/api/v1/collections",
  ];

  return sensitivePaths.some((p) => path.startsWith(p));
}

export function clearSession(): void {
  currentSession = null;
}
