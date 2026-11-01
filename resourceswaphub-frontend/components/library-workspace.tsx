"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  BookOpen,
  ChevronLeft,
  ChevronRight,
  Layers,
  Library,
  MapPin,
  Pencil,
  Plus,
  Sparkles,
} from "lucide-react";
import { BookCover } from "@/components/book-cover";
import { EmptyState } from "@/components/empty-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage } from "@/lib/api";
import { formatPrice, relativeTime, statusLabel } from "@/lib/format";
import type { Listing, ListingStatus } from "@/lib/types";
import { cn } from "@/lib/utils";

const TABS: { label: string; value?: ListingStatus }[] = [
  { label: "All books" },
  { label: "Available", value: "AVAILABLE" },
  { label: "Paused", value: "UNAVAILABLE" },
  { label: "Withdrawn", value: "WITHDRAWN" },
  { label: "Exchanged", value: "EXCHANGED" },
];

function statusTone(status: ListingStatus) {
  switch (status) {
    case "AVAILABLE":
      return "bg-emerald-100 text-emerald-800";
    case "UNAVAILABLE":
      return "bg-amber-100 text-amber-800";
    case "WITHDRAWN":
      return "bg-muted text-muted-foreground";
    default:
      return "bg-blue-100 text-blue-800";
  }
}

function ListingRow({ listing }: { listing: Listing }) {
  const queryClient = useQueryClient();
  const update = useMutation({
    mutationFn: (status: ListingStatus) =>
      apiClient.updateListingStatus(listing.id, status),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ["my-listings"] });
      void queryClient.invalidateQueries({ queryKey: ["discover"] });
      void queryClient.invalidateQueries({ queryKey: ["listing", updated.id] });
      toast.success(`Marked ${statusLabel(updated.status).toLowerCase()}.`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border bg-card p-3 sm:flex-row sm:items-center sm:p-4">
      <Link href={`/books/${listing.id}`} className="flex min-w-0 flex-1 items-center gap-3">
        <BookCover
          title={listing.book.title}
          author={listing.book.author}
          imageUrl={listing.imageUrls[0]}
          className="h-20 w-16 shrink-0 rounded-lg"
          compact
        />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge className={cn("border-0", statusTone(listing.status))}>
              {statusLabel(listing.status)}
            </Badge>
            <span className="text-[11px] font-medium text-primary">
              {formatPrice(listing)}
            </span>
          </div>
          <p className="mt-1 line-clamp-1 text-sm font-semibold">{listing.book.title}</p>
          <p className="line-clamp-1 text-xs text-muted-foreground">
            {listing.book.author} · Class {listing.book.classLevel} ·{" "}
            {listing.book.boardLabel}
          </p>
          <p className="mt-1 flex items-center gap-2 text-[11px] text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <MapPin className="size-3" /> {listing.city}
            </span>
            <span>· {relativeTime(listing.createdAt)}</span>
            {listing.collection ? (
              <span className="inline-flex items-center gap-1">
                · <Layers className="size-3" /> {listing.collection.title}
              </span>
            ) : null}
          </p>
        </div>
      </Link>

      <div className="flex shrink-0 items-center gap-2 sm:pl-3">
        <select
          value={listing.status}
          disabled={update.isPending}
          onChange={(event) => update.mutate(event.target.value as ListingStatus)}
          className="h-9 min-w-0 flex-1 rounded-lg border border-input bg-card px-2 text-xs sm:w-32 sm:flex-none"
          aria-label={`Status for ${listing.book.title}`}
        >
          {["AVAILABLE", "UNAVAILABLE", "WITHDRAWN", "EXCHANGED"].map((status) => (
            <option key={status} value={status}>
              {statusLabel(status)}
            </option>
          ))}
        </select>
        <Button
          variant="outline"
          size="sm"
          className="gap-1.5"
          render={<Link href={`/listings/${listing.id}/edit`} />}
        >
          <Pencil className="size-3.5" /> Edit
        </Button>
      </div>
    </div>
  );
}

export function LibraryWorkspace() {
  const [tab, setTab] = useState<ListingStatus | undefined>(undefined);
  const [page, setPage] = useState(0);

  const listings = useQuery({
    queryKey: ["my-listings", tab, page],
    queryFn: () => apiClient.myListings(page, 12, tab),
  });

  const collections = useQuery({
    queryKey: ["my-collections"],
    queryFn: () => apiClient.myCollections(0, 12),
  });

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
            Your shelf
          </p>
          <h1 className="mt-1.5 font-display text-3xl font-semibold sm:text-4xl">
            My library
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Keep availability honest, refresh a price, or group a term&apos;s
            books into one collection.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" render={<Link href="/collections/new" />} className="gap-1.5">
            <Layers className="size-4" /> New collection
          </Button>
          <Button render={<Link href="/listings/new" />} className="gap-1.5">
            <Plus className="size-4" /> List a book
          </Button>
        </div>
      </div>

      <section className="mt-6">
        <div className="flex gap-1 overflow-x-auto rounded-xl border border-border bg-card p-1 no-scrollbar">
          {TABS.map((item) => (
            <button
              key={item.label}
              type="button"
              onClick={() => {
                setTab(item.value);
                setPage(0);
              }}
              className={cn(
                "shrink-0 rounded-lg px-3.5 py-2 text-sm font-medium transition",
                tab === item.value
                  ? "bg-secondary text-secondary-foreground"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground"
              )}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="mt-4 flex flex-col gap-2.5">
          {listings.isLoading ? (
            Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="h-28 animate-pulse rounded-2xl bg-muted" />
            ))
          ) : listings.isError ? (
            <div className="rounded-2xl border border-destructive/25 bg-destructive/5 p-5 text-sm">
              <p className="font-medium">Couldn&apos;t load your listings.</p>
              <p className="mt-1 text-muted-foreground">{errorMessage(listings.error)}</p>
              <Button variant="outline" className="mt-3" onClick={() => void listings.refetch()}>
                Try again
              </Button>
            </div>
          ) : listings.data?.content.length ? (
            <>
              {listings.data.content.map((listing) => (
                <ListingRow key={listing.id} listing={listing} />
              ))}
              {listings.data.totalPages > 1 ? (
                <div className="mt-2 flex items-center justify-between">
                  <p className="text-xs text-muted-foreground">
                    Page {page + 1} of {listings.data.totalPages}
                  </p>
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={page <= 0}
                      onClick={() => setPage((value) => Math.max(0, value - 1))}
                      className="gap-1"
                    >
                      <ChevronLeft className="size-4" /> Previous
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={!listings.data.hasNext}
                      onClick={() => setPage((value) => value + 1)}
                      className="gap-1"
                    >
                      Next <ChevronRight className="size-4" />
                    </Button>
                  </div>
                </div>
              ) : null}
            </>
          ) : (
            <EmptyState
              icon={Library}
              title={
                tab
                  ? `No ${statusLabel(tab).toLowerCase()} books`
                  : "Your shelf is waiting"
              }
              description={
                tab
                  ? "Try another tab, or update a listing's availability from the All books tab."
                  : "List a book you no longer need. It takes a couple of minutes and could save a student a term's worth of searching."
              }
              action={
                <Button render={<Link href="/listings/new" />} className="gap-1.5">
                  <Plus className="size-4" /> List a book
                </Button>
              }
            />
          )}
        </div>
      </section>

      <section className="mt-10">
        <div className="flex items-end justify-between gap-4">
          <div>
            <h2 className="flex items-center gap-2 font-display text-2xl font-semibold">
              <Layers className="size-5 text-primary" /> Collections
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Group books that belong together — a class set, a subject bundle,
              or a whole term.
            </p>
          </div>
          <Button variant="ghost" size="sm" render={<Link href="/collections/new" />} className="gap-1">
            <Plus className="size-4" /> New
          </Button>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {collections.isLoading
            ? Array.from({ length: 2 }).map((_, index) => (
                <div key={index} className="h-32 animate-pulse rounded-2xl bg-muted" />
              ))
            : collections.data?.content.map((collection) => (
                <Link
                  key={collection.id}
                  href={`/collections/${collection.id}`}
                  className="group rounded-2xl border border-border bg-card p-4 transition hover:border-primary/30 hover:shadow-sm"
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="flex size-9 items-center justify-center rounded-xl bg-secondary text-primary">
                      <Layers className="size-4" />
                    </span>
                    <span className="text-[11px] text-muted-foreground">
                      {collection.availableCount}/{collection.itemCount} available
                    </span>
                  </div>
                  <p className="mt-3 line-clamp-1 text-sm font-semibold group-hover:text-primary">
                    {collection.title}
                  </p>
                  <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">
                    {collection.description || "A set of books from one shelf."}
                  </p>
                  <p className="mt-3 flex items-center gap-1 text-[11px] text-muted-foreground">
                    <MapPin className="size-3" /> {collection.city}
                  </p>
                </Link>
              ))}

          <Link
            href="/collections/new"
            className="flex min-h-32 flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-border bg-card/40 p-4 text-center text-muted-foreground transition hover:border-primary/30 hover:text-foreground"
          >
            <span className="flex size-9 items-center justify-center rounded-xl bg-muted">
              <Sparkles className="size-4" />
            </span>
            <span className="text-sm font-medium">Start a collection</span>
            <span className="text-[11px]">Bundle a class set in one place</span>
          </Link>
        </div>
      </section>

      {!listings.isLoading && !listings.data?.content.length && tab ? (
        <p className="mt-6 flex items-center justify-center gap-1 text-xs text-muted-foreground">
          <BookOpen className="size-3" /> Tip: mark a book Available again when a
          buyer changes their mind.
        </p>
      ) : null}
    </div>
  );
}
