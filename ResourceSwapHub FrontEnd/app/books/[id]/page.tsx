"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  ArrowLeft,
  BookOpen,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  Globe2,
  GraduationCap,
  MapPin,
  MessageCircle,
  Pencil,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { BookCover } from "@/components/book-cover";
import { Avatar } from "@/components/avatar";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatPrice, relativeTime, statusLabel } from "@/lib/format";
import type { ListingStatus } from "@/lib/types";

function ListingDetail() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user, loading } = useAuth();
  const [activeImage, setActiveImage] = useState(0);
  const [status, setStatus] = useState<ListingStatus | "">("");

  const listing = useQuery({
    queryKey: ["listing", id],
    queryFn: () => apiClient.listing(id),
    enabled: Boolean(id),
  });

  const startChat = useMutation({
    mutationFn: () => apiClient.startConversation(id),
    onSuccess: (conversation) => {
      router.push(`/inbox/${conversation.id}`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const changeStatus = useMutation({
    mutationFn: (next: ListingStatus) => apiClient.updateListingStatus(id, next),
    onSuccess: (updated) => {
      queryClient.setQueryData(["listing", id], updated);
      void queryClient.invalidateQueries({ queryKey: ["my-listings"] });
      toast.success(`Listing marked ${statusLabel(updated.status).toLowerCase()}.`);
      setStatus("");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (listing.isLoading) {
    return (
      <div className="flex min-h-svh flex-col">
        <PublicHeader />
        <main className="mx-auto grid w-full max-w-6xl flex-1 gap-10 px-4 py-10 sm:px-6 md:grid-cols-2">
          <div className="aspect-[4/5] animate-pulse rounded-3xl bg-muted" />
          <div className="space-y-4">
            <div className="h-6 w-1/3 animate-pulse rounded bg-muted" />
            <div className="h-10 w-5/6 animate-pulse rounded bg-muted" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-muted" />
            <div className="h-28 animate-pulse rounded-2xl bg-muted" />
          </div>
        </main>
      </div>
    );
  }

  if (listing.isError || !listing.data) {
    return (
      <div className="flex min-h-svh flex-col">
        <PublicHeader />
        <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col items-center justify-center px-4 py-20 text-center">
          <span className="flex size-14 items-center justify-center rounded-2xl bg-secondary">
            <BookOpen className="size-6 text-primary" />
          </span>
          <h1 className="mt-5 font-display text-3xl font-semibold">
            This book isn&apos;t on the shelf
          </h1>
          <p className="mt-2 max-w-md text-sm text-muted-foreground">
            {errorMessage(listing.error) ||
              "The listing may have been withdrawn or removed by its owner."}
          </p>
          <Button render={<Link href="/discover" />} className="mt-6">
            Browse other books
          </Button>
        </main>
        <SiteFooter />
      </div>
    );
  }

  const data = listing.data;
  const isOwner = user?.id === data.seller.id;
  const images = data.imageUrls;
  const available = data.status === "AVAILABLE";

  return (
    <div className="flex min-h-svh flex-col">
      <PublicHeader />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:py-10">
        <nav className="mb-5 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
          <Link href="/discover" className="inline-flex items-center gap-1 hover:text-foreground">
            <ArrowLeft className="size-3.5" /> Discover
          </Link>          <ChevronRight className="size-3" />
          <Link href={`/discover?subject=${encodeURIComponent(data.book.subject)}`} className="hover:text-foreground">
            {data.book.subject}
          </Link>
          <ChevronRight className="size-3" />
          <span className="truncate text-foreground">{data.book.title}</span>
        </nav>

        <div className="grid gap-8 md:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] lg:gap-12">
          <div>
            <div className="overflow-hidden rounded-3xl ring-1 ring-foreground/10 soft-shadow">
              <BookCover
                title={data.book.title}
                author={data.book.author}
                imageUrl={images[activeImage]}
                className="aspect-[4/5] w-full"
              />
            </div>
            {images.length > 1 ? (
              <div className="mt-3 flex gap-2 overflow-x-auto pb-1 no-scrollbar">
                {images.map((image, index) => (
                  <button
                    key={image}
                    type="button"
                    onClick={() => setActiveImage(index)}
                    className={`relative w-16 shrink-0 overflow-hidden rounded-lg ring-2 transition ${
                      activeImage === index ? "ring-primary" : "ring-transparent opacity-70 hover:opacity-100"
                    }`}
                    aria-label={`Photo ${index + 1}`}
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={image} alt="" className="aspect-[4/5] w-full object-cover" />
                  </button>
                ))}
              </div>
            ) : null}

            <div className="mt-5 rounded-2xl border border-border bg-card p-5">
              <h2 className="text-sm font-semibold">About this copy</h2>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                {data.notes?.trim() ||
                  "The owner hasn't added extra notes. Message them to ask about the condition, edition or pickup."}
              </p>
              <div className="mt-4 flex items-center gap-2 text-xs text-muted-foreground">
                <CalendarDays className="size-3.5" />
                Listed {relativeTime(data.createdAt)}
                {data.updatedAt !== data.createdAt ? (
                  <> · updated {relativeTime(data.updatedAt)}</>
                ) : null}
              </div>
            </div>
          </div>

          <div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge
                className={
                  available
                    ? "bg-emerald-700 text-white"
                    : "bg-muted text-muted-foreground"
                }
              >
                {statusLabel(data.status)}
              </Badge>
              <Badge variant="secondary">{data.conditionLabel}</Badge>
              <Badge variant="outline">
                {data.priceType === "FREE"
                  ? "Giveaway"
                  : data.priceType === "NEGOTIABLE"
                    ? "Open to offers"
                    : "Fixed price"}
              </Badge>
              {data.collection ? (
                <Badge variant="outline" className="gap-1">
                  <BookOpen className="size-3" /> {data.collection.title}
                </Badge>
              ) : null}
            </div>

            <h1 className="mt-4 font-display text-4xl font-semibold leading-tight text-balance sm:text-5xl">
              {data.book.title}
            </h1>
            <p className="mt-2 text-base text-muted-foreground">
              by {data.book.author}
              {data.book.publisher ? ` · ${data.book.publisher}` : ""}
            </p>

            <div className="mt-5 flex flex-wrap items-end gap-x-4 gap-y-2">
              <span className="font-display text-4xl font-semibold text-primary">
                {formatPrice(data)}
              </span>
              {data.priceType === "NEGOTIABLE" ? (
                <span className="pb-1 text-xs text-muted-foreground">
                  Talk to the owner about the final price
                </span>
              ) : null}
            </div>

            <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
              {[
                {
                  icon: GraduationCap,
                  label: "Class",
                  value: `Class ${data.book.classLevel}`,
                },
                { icon: BookOpen, label: "Board", value: data.book.boardLabel },
                { icon: BookOpen, label: "Subject", value: data.book.subject },
                {
                  icon: CalendarDays,
                  label: "Edition",
                  value: data.book.edition || "Not specified",
                },
                { icon: Globe2, label: "Language", value: data.book.language },
                {
                  icon: CheckCircle2,
                  label: "Condition",
                  value: data.conditionLabel,
                },
              ].map((meta) => (
                <div
                  key={meta.label}
                  className="rounded-xl border border-border bg-card px-3 py-2.5"
                >
                  <p className="flex items-center gap-1 text-[10px] uppercase tracking-wide text-muted-foreground">
                    <meta.icon className="size-3" /> {meta.label}
                  </p>
                  <p className="mt-1 truncate text-sm font-medium">{meta.value}</p>
                </div>
              ))}
            </div>

            <div className="mt-6 rounded-2xl border border-border bg-card p-4">
              <div className="flex items-start gap-3">
                <Avatar
                  name={data.seller.name}
                  src={data.seller.avatarUrl}
                  className="size-11"
                />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold">{data.seller.name}</p>
                  <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                    <MapPin className="size-3" />
                    {data.seller.locality ? `${data.seller.locality}, ` : ""}
                    {data.seller.city ?? data.city}
                  </p>
                </div>
                <span className="rounded-full bg-secondary px-2 py-0.5 text-[10px] font-medium text-secondary-foreground">
                  Owner
                </span>
              </div>

              <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                {isOwner ? (
                  <>
                    <Button
                      render={<Link href={`/listings/${data.id}/edit`} />}
                      className="flex-1 gap-2"
                    >
                      <Pencil className="size-4" /> Edit listing
                    </Button>
                    <div className="flex flex-1 gap-2">
                      <select
                        value={status || data.status}
                        onChange={(event) =>
                          setStatus(event.target.value as ListingStatus)
                        }
                        className="h-9 min-w-0 flex-1 rounded-lg border border-input bg-card px-2 text-sm"
                        aria-label="Listing status"
                      >
                        {["AVAILABLE", "UNAVAILABLE", "WITHDRAWN", "EXCHANGED"].map(
                          (option) => (
                            <option key={option} value={option}>
                              {statusLabel(option)}
                            </option>
                          )
                        )}
                      </select>
                      <Button
                        variant="outline"
                        disabled={
                          changeStatus.isPending ||
                          (status || data.status) === data.status
                        }
                        onClick={() =>
                          changeStatus.mutate((status || data.status) as ListingStatus)
                        }
                      >
                        Save
                      </Button>
                    </div>
                  </>
                ) : (
                  <>
                    <Button
                      className="flex-1 gap-2"
                      disabled={!available || startChat.isPending || loading}
                      onClick={() => {
                        if (!user) {
                          router.push(`/login?next=${encodeURIComponent(`/books/${data.id}`)}`);
                          return;
                        }
                        if (!user.profileComplete) {
                          toast.error("Add your city and locality before messaging an owner.");
                          router.push("/profile");
                          return;
                        }
                        startChat.mutate();
                      }}
                    >
                      <MessageCircle className="size-4" />
                      {startChat.isPending
                        ? "Opening chat…"
                        : available
                          ? "Message the owner"
                          : "No longer available"}
                    </Button>
                    <Button
                      variant="outline"
                      render={<Link href="/discover" />}
                      className="gap-2"
                    >
                      Keep browsing
                    </Button>
                  </>
                )}
              </div>
              <p className="mt-3 flex items-center gap-1.5 text-[11px] text-muted-foreground">
                <ShieldCheck className="size-3.5 text-primary" />
                Chat stays inside ResourceSwapHub. Meet in a public place and inspect
                the book before handing anything over.
              </p>
            </div>

            <div className="mt-4 flex items-center gap-2 text-xs text-muted-foreground">
              <UserRound className="size-3.5" />
              Pickup around {data.locality ? `${data.locality}, ` : ""}
              {data.city}
            </div>
          </div>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}

function DetailFallback() {
  return (
    <div className="flex min-h-svh flex-col">
      <PublicHeader />
      <main className="mx-auto grid w-full max-w-6xl flex-1 gap-10 px-4 py-10 sm:px-6 md:grid-cols-2">
        <div className="aspect-[4/5] animate-pulse rounded-3xl bg-muted" />
        <div className="space-y-4">
          <div className="h-6 w-1/3 animate-pulse rounded bg-muted" />
          <div className="h-10 w-5/6 animate-pulse rounded bg-muted" />
          <div className="h-4 w-1/2 animate-pulse rounded bg-muted" />
          <div className="h-28 animate-pulse rounded-2xl bg-muted" />
        </div>
      </main>
    </div>
  );
}

export default function ListingDetailPage() {
  return (
    <Suspense fallback={<DetailFallback />}>
      <ListingDetail />
    </Suspense>
  );
}
