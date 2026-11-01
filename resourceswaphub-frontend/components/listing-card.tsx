import Link from "next/link";
import { MapPin } from "lucide-react";
import { BookCover } from "@/components/book-cover";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { formatPrice, relativeTime } from "@/lib/format";
import type { Listing } from "@/lib/types";

export function ListingCard({
  listing,
  className,
}: {
  listing: Listing;
  className?: string;
}) {
  const free = listing.priceType === "FREE";
  return (
    <Link
      href={`/books/${listing.id}`}
      className={cn(
        "group flex flex-col overflow-hidden rounded-2xl bg-card ring-1 ring-foreground/8 transition duration-200 hover:-translate-y-0.5 hover:shadow-[0_18px_45px_-30px_rgba(23,60,48,0.55)] hover:ring-foreground/15",
        className
      )}
    >
      <div className="relative">
        <BookCover
          title={listing.book.title}
          author={listing.book.author}
          imageUrl={listing.imageUrls[0]}
          className="aspect-[4/5] w-full transition duration-300 group-hover:scale-[1.015]"
          compact
        />
        <div className="absolute left-3 top-3 flex gap-1.5">
          <Badge
            className={cn(
              "border-0 shadow-sm backdrop-blur",
              free
                ? "bg-emerald-700 text-white"
                : "bg-background/95 text-foreground"
            )}
          >
            {formatPrice(listing)}
          </Badge>
        </div>
        <div className="absolute right-3 top-3">
          <span className="rounded-full bg-black/25 px-2 py-0.5 text-[10px] font-medium text-white backdrop-blur">
            {listing.conditionLabel}
          </span>
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-1 p-4">
        <div className="flex items-start justify-between gap-2">
          <h3 className="line-clamp-2 text-sm font-semibold leading-snug text-foreground">
            {listing.book.title}
          </h3>
        </div>
        <p className="line-clamp-1 text-xs text-muted-foreground">
          {listing.book.author}
        </p>
        <p className="mt-1 text-[11px] font-medium uppercase tracking-wide text-primary/80">
          Class {listing.book.classLevel} · {listing.book.boardLabel} ·{" "}
          {listing.book.subject}
        </p>
        <div className="mt-auto flex items-center justify-between gap-2 pt-3 text-[11px] text-muted-foreground">
          <span className="inline-flex min-w-0 items-center gap-1">
            <MapPin className="size-3 shrink-0" />
            <span className="truncate">
              {listing.locality ? `${listing.locality}, ` : ""}
              {listing.city}
            </span>
          </span>
          <span className="shrink-0">{relativeTime(listing.createdAt)}</span>
        </div>
      </div>
    </Link>
  );
}
