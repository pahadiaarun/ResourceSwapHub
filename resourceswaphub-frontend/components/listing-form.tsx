"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useDeferredValue, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import {
  ArrowLeft,
  BookOpen,
  Check,
  ChevronRight,
  Loader2,
  MapPin,
  Plus,
  Search,
  Sparkles,
  TriangleAlert,
} from "lucide-react";
import { BookCover } from "@/components/book-cover";
import {
  LocationSelector,
  locationFromInfo,
  type LocationValue,
} from "@/components/location-select";
import { PhotoUploader, mediaIdFromUrl, type Photo } from "@/components/photo-uploader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiClient, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatPrice } from "@/lib/format";
import type { Book, Listing } from "@/lib/types";
import { cn } from "@/lib/utils";

const CONDITION_OPTIONS = [
  ["NEW", "New"],
  ["LIKE_NEW", "Like new"],
  ["GOOD", "Good"],
  ["FAIR", "Fair"],
  ["POOR", "Poor"],
] as const;

const PRICE_OPTIONS = [
  {
    value: "FREE",
    title: "Free",
    text: "Pass it on to a student who needs it.",
  },
  {
    value: "FIXED_PRICE",
    title: "Fixed price",
    text: "Set one fair price. No haggling needed.",
  },
  {
    value: "NEGOTIABLE",
    title: "Negotiable",
    text: "Suggest a price and leave room to talk.",
  },
] as const;

const schema = z
  .object({
    condition: z.enum(["NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR"]),
    priceType: z.enum(["FREE", "FIXED_PRICE", "NEGOTIABLE"]),
    price: z.string(),
    locality: z.string().trim().max(120, "Locality is too long"),
    notes: z.string().trim().max(2000, "Notes must be 2000 characters or fewer"),
    collectionId: z.string(),
  })
  .superRefine((values, ctx) => {
    if (values.priceType === "FREE") return;
    const amount = Number(values.price);
    if (values.priceType === "FIXED_PRICE" && (!values.price || amount <= 0)) {
      ctx.addIssue({
        code: "custom",
        path: ["price"],
        message: "Enter a price greater than zero",
      });
    }
    if (
      values.priceType === "NEGOTIABLE" &&
      values.price &&
      (!Number.isFinite(amount) || amount <= 0)
    ) {
      ctx.addIssue({
        code: "custom",
        path: ["price"],
        message: "Enter a valid starting price or leave it blank",
      });
    }
  });

type Values = z.infer<typeof schema>;

const SELECT =
  "h-10 w-full appearance-none rounded-xl border border-input bg-card px-3 pr-8 text-sm outline-none transition focus:border-ring focus:ring-3 focus:ring-ring/20";

const emptyNewBook = {
  title: "",
  author: "",
  publisher: "",
  isbn: "",
  classLevel: "10",
  board: "CBSE",
  subject: "",
  edition: "",
  language: "English",
};

export function ListingForm({ listing }: { listing?: Listing }) {
  const editing = Boolean(listing);
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const [selectedBook, setSelectedBook] = useState<Book | null>(
    listing?.book ?? null
  );
  const [bookQuery, setBookQuery] = useState("");
  const deferredQuery = useDeferredValue(bookQuery.trim());
  const [showNewBook, setShowNewBook] = useState(false);
  const [newBook, setNewBook] = useState(emptyNewBook);
  const [photos, setPhotos] = useState<Photo[]>(
    (listing?.imageUrls ?? [])
      .map((url) => ({ url, id: mediaIdFromUrl(url) }))
      .filter((photo): photo is Photo => Boolean(photo.id))
  );

  // Listing location comes from the directory: pre-filled from the profile so the
  // common case is one tap, but fully overridable when pickup happens elsewhere.
  const [location, setLocation] = useState<LocationValue>(() => {
    const fromListing = locationFromInfo(listing?.location);
    return fromListing.countryId || fromListing.stateId || fromListing.cityId
      ? fromListing
      : locationFromInfo(user?.location);
  });
  const [locationError, setLocationError] = useState<string | null>(null);

  const [prevProfileLocation, setPrevProfileLocation] = useState(user?.location);
  if (prevProfileLocation !== user?.location) {
    setPrevProfileLocation(user?.location);
    if (!location.countryId && !location.stateId && !location.cityId) {
      setLocation(locationFromInfo(user?.location));
    }
  }

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      condition: listing?.condition ?? "GOOD",
      priceType: listing?.priceType ?? "FREE",
      price: listing?.price ? String(listing.price) : "",
      locality: listing?.locality ?? user?.locality ?? "",
      notes: listing?.notes ?? "",
      collectionId: listing?.collection?.id ?? "",
    },
  });

  const priceType = form.watch("priceType");
  const watched = form.watch();

  const taxonomy = useQuery({
    queryKey: ["taxonomy"],
    queryFn: apiClient.taxonomy,
    staleTime: 5 * 60_000,
  });

  const collections = useQuery({
    queryKey: ["my-collections"],
    queryFn: () => apiClient.myCollections(0, 50),
    enabled: Boolean(user),
  });

  const bookResults = useQuery({
    queryKey: ["book-search", deferredQuery],
    queryFn: () => apiClient.searchBooks(deferredQuery),
    enabled: !editing && !selectedBook && deferredQuery.length >= 2,
  });

  const createBook = useMutation({
    mutationFn: () =>
      apiClient.createBook({
        title: newBook.title.trim(),
        author: newBook.author.trim(),
        publisher: newBook.publisher.trim() || undefined,
        isbn: newBook.isbn.trim() || undefined,
        classLevel: Number(newBook.classLevel),
        board: newBook.board,
        subject: newBook.subject.trim(),
        edition: newBook.edition.trim() || undefined,
        language: newBook.language.trim() || "English",
      }),
    onSuccess: (book) => {
      setSelectedBook(book);
      setShowNewBook(false);
      setNewBook(emptyNewBook);
      void queryClient.invalidateQueries({ queryKey: ["book-search"] });
      toast.success("Edition added to the catalogue.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const save = useMutation({
    mutationFn: (values: Values) => {
      if (!selectedBook) throw new Error("Choose a book first.");
      const payload: Record<string, unknown> = {
        condition: values.condition,
        priceType: values.priceType,
        price:
          values.priceType === "FREE" || !values.price.trim()
            ? null
            : Number(values.price),
        countryId: location.countryId ?? null,
        stateId: location.stateId ?? null,
        cityId: location.cityId ?? null,
        locality: values.locality.trim() || null,
        notes: values.notes.trim() || null,
        imageIds: photos.map((photo) => photo.id),
      };
      if (!editing) {
        payload.bookId = selectedBook.id;
        if (values.collectionId) payload.collectionId = values.collectionId;
        return apiClient.createListing(payload);
      }
      return apiClient.updateListing(listing!.id, payload);
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ["my-listings"] });
      void queryClient.invalidateQueries({ queryKey: ["discover"] });
      void queryClient.invalidateQueries({ queryKey: ["listing", saved.id] });
      toast.success(editing ? "Listing updated." : "Your book is on the shelf.");
      router.push(editing ? `/books/${saved.id}` : "/library");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const preview = useMemo(
    () => ({
      title: selectedBook?.title ?? "Your book",
      author: selectedBook?.author ?? "Choose a title to preview",
      priceType: watched.priceType,
      price: watched.price ? Number(watched.price) : null,
    }),
    [selectedBook, watched.price, watched.priceType]
  );

  const profileReady = user?.profileComplete;

  function submit(values: Values) {
    if (!selectedBook) {
      toast.error("Choose a catalogue book first.");
      return;
    }
    if (!location.cityId) {
      setLocationError("Choose the city where you will hand the book over.");
      toast.error("Select your pickup city from the list.");
      return;
    }
    setLocationError(null);
    if (!profileReady) {
      toast.error("Add your city and locality to your profile first.");
      return;
    }
    save.mutate(values);
  }

  return (
    <form onSubmit={form.handleSubmit(submit)} className="mx-auto max-w-6xl">
      <button
        type="button"
        onClick={() => router.back()}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back
      </button>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
            {editing ? "Edit listing" : "New listing"}
          </p>
          <h1 className="mt-1.5 font-display text-3xl font-semibold sm:text-4xl">
            {editing ? "Update your book" : "Put a book on a shelf"}
          </h1>
          <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
            {editing
              ? "Adjust the price, condition or photos. Students already chatting with you keep the same thread."
              : "A clear photo, the exact edition and an honest condition help the right student find it."}
          </p>
        </div>
      </div>

      {!profileReady ? (
        <div className="mt-5 flex flex-wrap items-center gap-3 rounded-2xl border border-accent-foreground/15 bg-accent/50 p-4 text-sm">
          <TriangleAlert className="size-4 text-accent-foreground" />
          <span className="flex-1">
            Add a city and locality before listing so students know where to
            collect.
          </span>
          <Button type="button" variant="outline" size="sm" render={<Link href="/profile" />}>
            Complete profile
          </Button>
        </div>
      ) : null}

      <div className="mt-6 grid gap-5 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-5">
          <section className="rounded-2xl border border-border bg-card p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                <BookOpen className="size-4 text-primary" /> The book
              </h2>
              <span className="text-[11px] text-muted-foreground">Step 1 of 3</span>
            </div>

            {selectedBook ? (
              <div className="mt-4 flex items-center gap-3 rounded-xl border border-border bg-background p-3">
                <BookCover
                  title={selectedBook.title}
                  author={selectedBook.author}
                  className="size-16 shrink-0 rounded-md"
                  compact
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold">{selectedBook.title}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {selectedBook.author}
                  </p>
                  <p className="mt-0.5 text-[11px] text-primary">
                    Class {selectedBook.classLevel} · {selectedBook.boardLabel} ·{" "}
                    {selectedBook.subject}
                  </p>
                </div>
                {!editing ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setSelectedBook(null);
                      setBookQuery("");
                    }}
                  >
                    Change
                  </Button>
                ) : null}
              </div>
            ) : (
              <div className="mt-4">
                <div className="relative">
                  <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={bookQuery}
                    onChange={(event) => setBookQuery(event.target.value)}
                    placeholder="Search the catalogue by title or author…"
                    className="h-11 rounded-xl pl-9"
                  />
                  {bookQuery ? (
                    <button
                      type="button"
                      onClick={() => setBookQuery("")}
                      className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md px-2 py-1 text-xs text-muted-foreground hover:bg-muted"
                    >
                      Clear
                    </button>
                  ) : null}
                </div>

                {bookQuery.trim().length >= 2 ? (
                  <div className="mt-2 overflow-hidden rounded-xl border border-border">
                    {bookResults.isLoading ? (
                      <p className="p-4 text-sm text-muted-foreground">Searching…</p>
                    ) : bookResults.data?.length ? (
                      bookResults.data.map((book) => (
                        <button
                          key={book.id}
                          type="button"
                          onClick={() => {
                            setSelectedBook(book);
                            setBookQuery("");
                          }}
                          className="flex w-full items-center justify-between gap-3 border-b border-border/60 px-3 py-2.5 text-left text-sm transition last:border-0 hover:bg-muted"
                        >
                          <span className="min-w-0">
                            <span className="block truncate font-medium">{book.title}</span>
                            <span className="block truncate text-xs text-muted-foreground">
                              {book.author} · Class {book.classLevel} · {book.boardLabel}
                            </span>
                          </span>
                          <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
                        </button>
                      ))
                    ) : (
                      <div className="p-4">
                        <p className="text-sm text-muted-foreground">
                          No catalogue match. Add the edition once and it becomes
                          reusable for everyone.
                        </p>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="mt-3 gap-1.5"
                          onClick={() => setShowNewBook(true)}
                        >
                          <Plus className="size-4" /> Add this edition
                        </Button>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                    <p className="text-[11px] text-muted-foreground">
                      Search at least two characters, or add a missing edition.
                    </p>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="gap-1.5"
                      onClick={() => setShowNewBook(true)}
                    >
                      <Plus className="size-4" /> New edition
                    </Button>
                  </div>
                )}
              </div>
            )}

            {showNewBook ? (
              <div className="mt-4 rounded-xl border border-dashed border-border p-4">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium">Add a catalogue edition</p>
                  <button
                    type="button"
                    onClick={() => setShowNewBook(false)}
                    className="text-xs text-muted-foreground hover:text-foreground"
                  >
                    Hide
                  </button>
                </div>
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Title *</span>
                    <Input
                      value={newBook.title}
                      onChange={(event) => setNewBook({ ...newBook, title: event.target.value })}
                      placeholder="NCERT Mathematics"
                      className="h-9 rounded-lg"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Author *</span>
                    <Input
                      value={newBook.author}
                      onChange={(event) => setNewBook({ ...newBook, author: event.target.value })}
                      placeholder="NCERT"
                      className="h-9 rounded-lg"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Publisher</span>
                    <Input
                      value={newBook.publisher}
                      onChange={(event) => setNewBook({ ...newBook, publisher: event.target.value })}
                      className="h-9 rounded-lg"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">ISBN</span>
                    <Input
                      value={newBook.isbn}
                      onChange={(event) => setNewBook({ ...newBook, isbn: event.target.value })}
                      className="h-9 rounded-lg"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Class *</span>
                    <select
                      className="h-9 w-full rounded-lg border border-input bg-card px-2 text-sm"
                      value={newBook.classLevel}
                      onChange={(event) => setNewBook({ ...newBook, classLevel: event.target.value })}
                    >
                      {(taxonomy.data?.classes ?? Array.from({ length: 12 }, (_, i) => i + 1)).map(
                        (value) => (
                          <option key={value} value={value}>
                            Class {value}
                          </option>
                        )
                      )}
                    </select>
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Board *</span>
                    <select
                      className="h-9 w-full rounded-lg border border-input bg-card px-2 text-sm"
                      value={newBook.board}
                      onChange={(event) => setNewBook({ ...newBook, board: event.target.value })}
                    >
                      {(taxonomy.data?.boards ?? []).map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Subject *</span>
                    <Input
                      value={newBook.subject}
                      onChange={(event) => setNewBook({ ...newBook, subject: event.target.value })}
                      placeholder="Mathematics"
                      className="h-9 rounded-lg"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Edition</span>
                    <Input
                      value={newBook.edition}
                      onChange={(event) => setNewBook({ ...newBook, edition: event.target.value })}
                      placeholder="2024"
                      className="h-9 rounded-lg"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium">Language</span>
                    <Input
                      value={newBook.language}
                      onChange={(event) => setNewBook({ ...newBook, language: event.target.value })}
                      className="h-9 rounded-lg"
                    />
                  </label>
                </div>
                <Button
                  type="button"
                  size="sm"
                  className="mt-3 gap-1.5"
                  disabled={
                    createBook.isPending ||
                    !newBook.title.trim() ||
                    !newBook.author.trim() ||
                    !newBook.subject.trim()
                  }
                  onClick={() => createBook.mutate()}
                >
                  {createBook.isPending ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Check className="size-4" />
                  )}
                  Add edition
                </Button>
              </div>
            ) : null}
          </section>

          <section className="rounded-2xl border border-border bg-card p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-sm font-semibold">Photos of your copy</h2>
              <span className="text-[11px] text-muted-foreground">Step 2 of 3</span>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              Natural light and a clear cover photo build trust. The first photo
              becomes the cover.
            </p>
            <div className="mt-4">
              <PhotoUploader photos={photos} onChange={setPhotos} />
            </div>
          </section>

          <section className="rounded-2xl border border-border bg-card p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-sm font-semibold">Condition & price</h2>
              <span className="text-[11px] text-muted-foreground">Step 3 of 3</span>
            </div>

            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-medium text-muted-foreground">Condition</span>
                <select className={SELECT} {...form.register("condition")}>
                  {CONDITION_OPTIONS.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-medium text-muted-foreground">
                  Collection (optional)
                </span>
                <select className={SELECT} {...form.register("collectionId")}>
                  <option value="">No collection</option>
                  {(collections.data?.content ?? []).map((collection) => (
                    <option key={collection.id} value={collection.id}>
                      {collection.title}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="mt-4 grid gap-2 sm:grid-cols-3">
              {PRICE_OPTIONS.map((option) => {
                const active = priceType === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => form.setValue("priceType", option.value, { shouldValidate: true })}
                    className={cn(
                      "rounded-xl border p-3 text-left transition",
                      active
                        ? "border-primary bg-secondary/70 ring-1 ring-primary/30"
                        : "border-border bg-background hover:border-primary/30"
                    )}
                  >
                    <span className="flex items-center justify-between text-sm font-medium">
                      {option.title}
                      <span
                        className={cn(
                          "flex size-4 items-center justify-center rounded-full border",
                          active ? "border-primary bg-primary text-primary-foreground" : "border-input"
                        )}
                      >
                        {active ? <Check className="size-2.5" /> : null}
                      </span>
                    </span>
                    <span className="mt-1 block text-[11px] leading-relaxed text-muted-foreground">
                      {option.text}
                    </span>
                  </button>
                );
              })}
            </div>

            {priceType !== "FREE" ? (
              <label className="mt-4 flex max-w-xs flex-col gap-1.5">
                <span className="text-xs font-medium text-muted-foreground">
                  {priceType === "FIXED_PRICE" ? "Price (INR)" : "Starting price (optional)"}
                </span>
                <div className="relative">
                  <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                    ₹
                  </span>
                  <Input
                    {...form.register("price")}
                    inputMode="decimal"
                    placeholder={priceType === "FIXED_PRICE" ? "250" : "180"}
                    className="h-10 rounded-xl pl-7"
                  />
                </div>
                {form.formState.errors.price ? (
                  <span className="text-xs text-destructive">
                    {form.formState.errors.price.message}
                  </span>
                ) : null}
              </label>
            ) : null}
          </section>

          <section className="rounded-2xl border border-border bg-card p-5">
            <h2 className="flex items-center gap-2 text-sm font-semibold">
              <MapPin className="size-4 text-primary" /> Pickup & notes
            </h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <span className="mb-1.5 block text-xs font-medium text-muted-foreground">
                  Pickup location
                </span>
                <LocationSelector
                  id="listing-location"
                  value={location}
                  onChange={(next) => {
                    setLocation(next);
                    if (next.cityId) setLocationError(null);
                  }}
                  autoDefaultCountry
                  labels={{ country: "Country", state: "State", city: "City" }}
                />
                {locationError ? (
                  <span className="mt-1.5 block text-xs text-destructive">
                    {locationError}
                  </span>
                ) : (
                  <span className="mt-1.5 block text-[11px] text-muted-foreground">
                    Only city and state are shown publicly — never your address.
                  </span>
                )}
              </div>
              <label className="flex flex-col gap-1.5 sm:col-span-2">
                <span className="text-xs font-medium text-muted-foreground">Locality</span>
                <Input
                  {...form.register("locality")}
                  placeholder="Kothrud"
                  className="h-10 rounded-xl"
                />
              </label>
            </div>
            <label className="mt-4 flex flex-col gap-1.5">
              <span className="text-xs font-medium text-muted-foreground">
                Notes for the next reader
              </span>
              <textarea
                {...form.register("notes")}
                rows={4}
                placeholder="Any highlights, margin notes, a small tear on the cover? Honesty helps."
                className="w-full resize-y rounded-xl border border-input bg-card px-3 py-2 text-sm outline-none transition focus:border-ring focus:ring-3 focus:ring-ring/20"
              />
              <span className="text-[11px] text-muted-foreground">
                {(watched.notes ?? "").length}/2000
              </span>
            </label>
          </section>
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="rounded-2xl border border-border bg-card p-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              Live preview
            </p>
            <div className="mt-3 overflow-hidden rounded-xl ring-1 ring-border">
              <BookCover
                title={preview.title}
                author={preview.author}
                imageUrl={photos[0]?.url}
                className="aspect-[4/5] w-full"
                compact
              />
            </div>
            <div className="mt-3">
              <p className="line-clamp-1 text-sm font-semibold">{preview.title}</p>
              <p className="mt-0.5 flex items-center justify-between text-xs">
                <span className="text-muted-foreground">
                  {location.cityName ?? listing?.city ?? user?.city ?? "Your city"}
                  {location.stateName ? `, ${location.stateName}` : ""}
                </span>
                <span className="font-medium text-primary">{formatPrice(preview)}</span>
              </p>
            </div>

            <div className="mt-4 space-y-2 border-t border-border pt-4">
              {[
                ["Catalogue book selected", Boolean(selectedBook)],
                ["At least one photo", photos.length > 0],
                ["Pickup city added", Boolean(location.cityId)],
                ["Price looks valid", !form.formState.errors.price],
              ].map(([label, done]) => (
                <div key={label as string} className="flex items-center gap-2 text-xs">
                  <span
                    className={cn(
                      "flex size-4 items-center justify-center rounded-full",
                      done
                        ? "bg-emerald-100 text-emerald-700"
                        : "bg-muted text-muted-foreground"
                    )}
                  >
                    {done ? <Check className="size-2.5" /> : <span className="size-1 rounded-full bg-current" />}
                  </span>
                  <span className={done ? "" : "text-muted-foreground"}>
                    {label as string}
                  </span>
                </div>
              ))}
            </div>

            <Button
              type="submit"
              disabled={save.isPending || !selectedBook}
              className="mt-5 w-full gap-2 rounded-xl"
            >
              {save.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Sparkles className="size-4" />
              )}
              {editing ? "Save changes" : "Publish listing"}
            </Button>
            <p className="mt-2 text-center text-[10px] leading-relaxed text-muted-foreground">
              You can pause, withdraw or mark the listing exchanged at any time.
            </p>
          </div>
        </aside>
      </div>
    </form>
  );
}
