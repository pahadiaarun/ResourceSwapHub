"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Layers, MapPin, Pencil, UserRound } from "lucide-react";
import { Avatar } from "@/components/avatar";
import { EmptyState } from "@/components/empty-state";
import { ListingCard } from "@/components/listing-card";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { relativeTime } from "@/lib/format";

function CollectionDetail() {
  const params = useParams<{ id: string }>();
  const { user } = useAuth();
  const collection = useQuery({
    queryKey: ["collection", params.id],
    queryFn: () => apiClient.collection(params.id),
    enabled: Boolean(params.id),
  });

  if (collection.isLoading) {
    return (
      <div className="flex min-h-svh flex-col">
        <PublicHeader />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6">
          <div className="h-40 animate-pulse rounded-3xl bg-muted" />
          <div className="mt-6 grid grid-cols-2 gap-4 md:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className="aspect-[4/5] animate-pulse rounded-2xl bg-muted" />
            ))}
          </div>
        </main>
      </div>
    );
  }

  if (collection.isError || !collection.data) {
    return (
      <div className="flex min-h-svh flex-col">
        <PublicHeader />
        <main className="mx-auto flex w-full max-w-xl flex-1 flex-col items-center justify-center px-4 py-20 text-center">
          <Layers className="size-9 text-primary" />
          <h1 className="mt-4 font-display text-3xl font-semibold">
            Collection not found
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {errorMessage(collection.error)}
          </p>
          <Button render={<Link href="/discover" />} className="mt-5">
            Browse books
          </Button>
        </main>
      </div>
    );
  }

  const data = collection.data;
  const isOwner = user?.id === data.owner.id;

  return (
    <div className="flex min-h-svh flex-col">
      <PublicHeader />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
        <Link
          href={isOwner ? "/library" : "/discover"}
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-4" /> {isOwner ? "My library" : "Discover"}
        </Link>

        <div className="relative mt-5 overflow-hidden rounded-3xl border border-border bg-card p-6 sm:p-8">
          <div className="absolute -right-20 -top-20 size-64 rounded-full bg-secondary/70 blur-3xl" />
          <div className="relative flex flex-wrap items-start justify-between gap-5">
            <div className="max-w-2xl">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary px-2.5 py-1 text-[11px] font-medium text-secondary-foreground">
                <Layers className="size-3" /> Collection · {data.itemCount} book
                {data.itemCount === 1 ? "" : "s"}
              </span>
              <h1 className="mt-3 font-display text-4xl font-semibold sm:text-5xl">
                {data.title}
              </h1>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground sm:text-base">
                {data.description ||
                  "A set of books from one shelf, ready for a new reader."}
              </p>
              <div className="mt-4 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1.5">
                  <MapPin className="size-3.5" />
                  {data.locality ? `${data.locality}, ` : ""}
                  {data.city}
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <UserRound className="size-3.5" /> {data.owner.name}
                </span>
                <span>Listed {relativeTime(data.createdAt)}</span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Avatar
                name={data.owner.name}
                src={data.owner.avatarUrl}
                className="size-10"
              />
              <div>
                <p className="text-sm font-medium">{data.owner.name}</p>
                <p className="text-xs text-muted-foreground">
                  {data.availableCount} available
                </p>
              </div>
              {isOwner ? (
                <Button
                  variant="outline"
                  size="sm"
                  render={<Link href={`/collections/${data.id}/edit`} />}
                  className="ml-2 gap-1.5"
                >
                  <Pencil className="size-3.5" /> Edit
                </Button>
              ) : null}
            </div>
          </div>
        </div>

        <div className="mt-8 flex items-center justify-between gap-3">
          <h2 className="font-display text-2xl font-semibold">Books in this set</h2>
          <span className="text-xs text-muted-foreground">
            {data.availableCount} of {data.itemCount} available
          </span>
        </div>

        {data.items.length ? (
          <div className="mt-4 grid grid-cols-2 gap-4 sm:gap-5 md:grid-cols-3 lg:grid-cols-4">
            {data.items.map((listing) => (
              <ListingCard key={listing.id} listing={listing} />
            ))}
          </div>
        ) : (
          <div className="mt-4">
            <EmptyState
              icon={Layers}
              title="No books in this collection yet"
              description="When the owner adds a listing to this set, it will appear here."
            />
          </div>
        )}
      </main>
      <SiteFooter />
    </div>
  );
}

export default function CollectionPage() {
  return (
    <Suspense fallback={<CollectionFallback />}> 
      <CollectionDetail />
    </Suspense>
  );
}

function CollectionFallback() {
  return (
    <div className="flex min-h-svh flex-col">
      <PublicHeader />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6">
        <div className="h-40 animate-pulse rounded-3xl bg-muted" />
        <div className="mt-6 grid grid-cols-2 gap-4 md:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="aspect-[4/5] animate-pulse rounded-2xl bg-muted" />
          ))}
        </div>
      </main>
    </div>
  );
}
