import type {
  AuthResponse,
  CollectionDetail,
  CollectionSummary,
  Conversation,
  Listing,
  Media,
  Message,
  MessagePage,
  PageResponse,
  SearchFilters,
  Taxonomy,
  User,
} from "./types";

/**
 * Requests go through Next's rewrite to the Spring API. Same-origin requests
 * keep the HttpOnly refresh cookie working without cross-site cookie rules.
 */
export const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api/v1";
const TOKEN_KEY = "bookbridge.access-token";

export class ApiError extends Error {
  status: number;
  code: string;
  details?: { field: string; message: string }[];

  constructor(
    message: string,
    status: number,
    code = "REQUEST_FAILED",
    details?: { field: string; message: string }[]
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token: string | null) {
  if (typeof window === "undefined") return;
  if (token) window.localStorage.setItem(TOKEN_KEY, token);
  else window.localStorage.removeItem(TOKEN_KEY);
}

let refreshInFlight: Promise<string | null> | null = null;

export async function refreshSession(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (response) => {
        if (!response.ok) {
          setAccessToken(null);
          return null;
        }
        const payload = (await response.json()) as { data: AuthResponse };
        setAccessToken(payload.data.accessToken);
        return payload.data.accessToken;
      })
      .catch(() => {
        setAccessToken(null);
        return null;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  formData?: FormData;
  auth?: boolean;
  retryOnUnauthorized?: boolean;
  signal?: AbortSignal;
}

async function execute<T>(
  path: string,
  options: RequestOptions,
  allowRetry: boolean
): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  const token = getAccessToken();
  if (token && options.auth !== false) {
    headers.Authorization = `Bearer ${token}`;
  }

  let body: BodyInit | undefined;
  if (options.formData) {
    body = options.formData;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? "GET",
    headers,
    body,
    credentials: "include",
    signal: options.signal,
    cache: "no-store",
  });

  if (response.status === 401 && allowRetry && options.auth !== false) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return execute<T>(path, options, false);
    }
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const error = payload?.error;
    throw new ApiError(
      error?.message ?? "Something went wrong. Please try again.",
      response.status,
      error?.code,
      error?.details
    );
  }

  return (payload?.data ?? payload) as T;
}

export function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return execute<T>(path, options, options.retryOnUnauthorized !== false);
}

function queryString(filters: object) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && `${value}`.trim() !== "") {
      params.set(key, String(value));
    }
  }
  const query = params.toString();
  return query ? `?${query}` : "";
}

export const apiClient = {
  async register(input: { name: string; email: string; password: string }) {
    return api<AuthResponse>("/auth/register", {
      method: "POST",
      body: input,
      auth: false,
    });
  },
  async login(input: { email: string; password: string }) {
    return api<AuthResponse>("/auth/login", {
      method: "POST",
      body: input,
      auth: false,
    });
  },
  async loginWithGoogle(credential: string) {
    return api<AuthResponse>("/auth/google", {
      method: "POST",
      body: { credential },
      auth: false,
    });
  },
  async providers() {
    return api<{ googleEnabled: boolean }>("/auth/providers", {
      auth: false,
    });
  },
  async logout() {
    return api<void>("/auth/logout", {
      method: "POST",
      auth: false,
      retryOnUnauthorized: false,
    });
  },
  me() {
    return api<User>("/users/me");
  },
  updateProfile(input: {
    name: string;
    city?: string | null;
    locality?: string | null;
    avatarMediaId?: string | null;
  }) {
    return api<User>("/users/me", { method: "PATCH", body: input });
  },
  uploadMedia(file: File, purpose: "LISTING" | "AVATAR") {
    const data = new FormData();
    data.append("file", file);
    data.append("purpose", purpose);
    return api<Media>("/media", { method: "POST", formData: data });
  },
  taxonomy() {
    return api<Taxonomy>("/catalog/taxonomy", { auth: false });
  },
  searchBooks(q: string) {
    return api<import("./types").Book[]>(
      `/catalog/books${queryString({ q, limit: 10 })}`,
      { auth: false }
    );
  },
  createBook(input: Record<string, unknown>) {
    return api<import("./types").Book>("/catalog/books", {
      method: "POST",
      body: input,
    });
  },
  discover(filters: SearchFilters) {
    return api<PageResponse<Listing>>(
      `/discovery/listings${queryString(filters)}`,
      { auth: false }
    );
  },
  listing(id: string) {
    return api<Listing>(`/listings/${id}`, { auth: false });
  },
  createListing(input: Record<string, unknown>) {
    return api<Listing>("/listings", { method: "POST", body: input });
  },
  updateListing(id: string, input: Record<string, unknown>) {
    return api<Listing>(`/listings/${id}`, { method: "PATCH", body: input });
  },
  updateListingStatus(id: string, status: string) {
    return api<Listing>(`/listings/${id}/status`, {
      method: "PATCH",
      body: { status },
    });
  },
  myListings(page = 0, size = 24, status?: string) {
    return api<PageResponse<Listing>>(
      `/users/me/listings${queryString({ page, size, status })}`
    );
  },
  myCollections(page = 0, size = 24) {
    return api<PageResponse<CollectionSummary>>(
      `/users/me/collections${queryString({ page, size })}`
    );
  },
  collection(id: string) {
    return api<CollectionDetail>(`/collections/${id}`, { auth: false });
  },
  createCollection(input: Record<string, unknown>) {
    return api<CollectionDetail>("/collections", {
      method: "POST",
      body: input,
    });
  },
  updateCollection(id: string, input: Record<string, unknown>) {
    return api<CollectionDetail>(`/collections/${id}`, {
      method: "PATCH",
      body: input,
    });
  },
  conversations(page = 0, size = 30) {
    return api<PageResponse<Conversation>>(
      `/conversations${queryString({ page, size })}`
    );
  },
  conversation(id: string) {
    return api<Conversation>(`/conversations/${id}`);
  },
  startConversation(listingId: string) {
    return api<Conversation>("/conversations", {
      method: "POST",
      body: { listingId },
    });
  },
  messages(id: string, beforeId?: number) {
    return api<MessagePage>(
      `/conversations/${id}/messages${queryString({ beforeId, size: 50 })}`
    );
  },
  sendMessage(id: string, body: string) {
    return api<Message>(`/conversations/${id}/messages`, {
      method: "POST",
      body: { body },
    });
  },
  markRead(id: string, lastMessageId: number) {
    return api<{ count: number }>(`/conversations/${id}/read`, {
      method: "POST",
      body: { lastMessageId },
    });
  },
unreadCount() {
    return api<{ count: number }>("/conversations/unread-count");
  },

  // Extended Profile
  getExtendedProfile() {
    return api<import("./types").ExtendedProfile>("/profile/me");
  },
  updateExtendedProfile(input: Record<string, unknown>) {
    return api<import("./types").ExtendedProfile>("/profile/me", {
      method: "PUT",
      body: input,
    });
  },
  getProfile(userId: string) {
    return api<import("./types").ExtendedProfile>(`/profile/${userId}`);
  },

  // Institutions
  searchInstitutions(params: {
    q?: string;
    type?: string;
    state?: string;
    city?: string;
    page?: number;
    size?: number;
  } = {}) {
    return api<PageResponse<import("./types").Institution>>(
      `/institutions/search${queryString(params)}`,
      { auth: false }
    );
  },
  getInstitution(id: string) {
    return api<import("./types").Institution>(`/institutions/${id}`, {
      auth: false,
    });
  },
  suggestInstitution(input: {
    name: string;
    institutionType: string;
    state?: string;
    city?: string;
    district?: string;
    additionalInfo?: string;
  }) {
    return api<unknown>("/institutions/suggestions", {
      method: "POST",
      body: input,
    });
  },
};

export function errorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong. Please try again.";
}
