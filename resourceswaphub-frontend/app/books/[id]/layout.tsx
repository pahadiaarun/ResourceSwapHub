import type { Metadata } from "next";
import type { ReactNode } from "react";

/**
 * The listing detail screen is a client component, so it cannot export
 * metadata itself. This layout supplies a per-listing browser title and
 * Open Graph image by reading the public listing endpoint on the server.
 */
const backend = process.env.BACKEND_URL ?? "http://127.0.0.1:8080";

type ListingSummary = {
  title: string;
  author?: string | null;
  coverUrl?: string | null;
};

async function fetchListing(id: string): Promise<ListingSummary | null> {
  try {
    const response = await fetch(`${backend}/api/v1/listings/${id}`, {
      next: { revalidate: 300 },
    });
    if (!response.ok) return null;
    return (await response.json()) as ListingSummary;
  } catch {
    return null;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await params;
  const listing = await fetchListing(id);
  if (!listing?.title) return {};

  const description = listing.author
    ? `${listing.title} by ${listing.author} — free, fixed price or negotiable on ResourceSwapHub.`
    : `${listing.title} — free, fixed price or negotiable on ResourceSwapHub.`;

  return {
    title: listing.title,
    description,
    openGraph: {
      title: listing.title,
      description,
      ...(listing.coverUrl ? { images: [{ url: listing.coverUrl }] } : {}),
    },
  };
}

export default function BookLayout({ children }: { children: ReactNode }) {
  return children;
}
