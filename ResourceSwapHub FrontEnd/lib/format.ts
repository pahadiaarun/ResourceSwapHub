import type { Listing } from "./types";

export function formatPrice(listing: Pick<Listing, "priceType" | "price">) {
  if (listing.priceType === "FREE") return "Free";
  if (listing.priceType === "NEGOTIABLE") {
    return listing.price ? `₹${listing.price.toLocaleString("en-IN")} · negotiable` : "Negotiable";
  }
  return listing.price
    ? `₹${listing.price.toLocaleString("en-IN")}`
    : "Fixed price";
}

export function money(value?: number | null) {
  if (value === undefined || value === null) return "—";
  return `₹${value.toLocaleString("en-IN")}`;
}

export function relativeTime(value?: string | null) {
  if (!value) return "";
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return "";
  const diff = Date.now() - then;
  const minutes = Math.round(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  const weeks = Math.round(days / 7);
  if (weeks < 5) return `${weeks}w ago`;
  return new Date(value).toLocaleDateString("en-IN", {
    month: "short",
    day: "numeric",
    year: then < Date.now() - 365 * 86400000 ? "numeric" : undefined,
  });
}

export function clockTime(value?: string | null) {
  if (!value) return "";
  return new Date(value).toLocaleTimeString("en-IN", {
    hour: "numeric",
    minute: "2-digit",
  });
}

export function initials(name?: string | null) {
  if (!name) return "?";
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

const COVERS = [
  "cover-fern",
  "cover-clay",
  "cover-ink",
  "cover-sun",
  "cover-plum",
  "cover-slate",
];

export function coverClass(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) % 997;
  }
  return COVERS[hash % COVERS.length];
}

export function statusLabel(status: string) {
  switch (status) {
    case "AVAILABLE":
      return "Available";
    case "UNAVAILABLE":
      return "Unavailable";
    case "WITHDRAWN":
      return "Withdrawn";
    case "EXCHANGED":
      return "Exchanged";
    default:
      return status;
  }
}

export function avatarUrl(url?: string | null) {
  return url || null;
}
