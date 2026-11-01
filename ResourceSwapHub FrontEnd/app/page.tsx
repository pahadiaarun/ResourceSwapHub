"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  BadgeIndianRupee,
  BookMarked,
  HeartHandshake,
  MapPin,
  MessageCircle,
  Search,
  Sparkles,
  Truck,
} from "lucide-react";
import { BookCover } from "@/components/book-cover";
import { ListingCard } from "@/components/listing-card";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiClient } from "@/lib/api";
import { coverClass } from "@/lib/format";

const featured = [
  {
    title: "NCERT Mathematics",
    author: "Class 10 · CBSE",
    note: "Free",
    rotate: "-rotate-6",
    translate: "translate-y-4",
  },
  {
    title: "Concise Physics",
    author: "Class 9 · ICSE",
    note: "₹180",
    rotate: "rotate-3",
    translate: "-translate-y-2",
  },
  {
    title: "First Flight",
    author: "English reader",
    note: "Free",
    rotate: "rotate-8",
    translate: "translate-y-6",
  },
];

export default function HomePage() {
  const router = useRouter();
  const [q, setQ] = useState("");

  const latest = useQuery({
    queryKey: ["landing-latest"],
    queryFn: () => apiClient.discover({ page: 0, size: 4 }),
  });

  function search(event: React.FormEvent) {
    event.preventDefault();
    router.push(`/discover${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`);
  }

  return (
    <div className="flex min-h-svh flex-col">
      <PublicHeader />

      <main className="flex-1">
        <section className="relative overflow-hidden">
          <div className="paper-grid absolute inset-0 opacity-60" />
          <div className="absolute -left-24 top-10 size-72 rounded-full bg-secondary/70 blur-3xl" />
          <div className="absolute -right-20 top-24 size-80 rounded-full bg-accent/60 blur-3xl" />

          <div className="relative mx-auto grid w-full max-w-7xl gap-14 px-4 pb-20 pt-14 sm:px-6 lg:grid-cols-[1.05fr_0.95fr] lg:items-center lg:px-8 lg:pb-24 lg:pt-20">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full border border-primary/15 bg-card/80 px-3 py-1.5 text-xs font-medium text-primary shadow-sm backdrop-blur">
                <Sparkles className="size-3.5" />
                A community shelf for students
              </div>
              <h1 className="mt-6 max-w-2xl font-display text-5xl font-semibold leading-[1.04] tracking-tight text-balance sm:text-6xl lg:text-7xl">
                The book you need is already on someone&apos;s shelf.
              </h1>
              <p className="mt-6 max-w-xl text-base leading-relaxed text-muted-foreground sm:text-lg">
                Find school and college books near you — free, fixed-price, or
                open to a conversation. Message the owner directly and keep good
                books moving.
              </p>

              <form
                onSubmit={search}
                className="mt-8 flex w-full max-w-xl flex-col gap-2 rounded-2xl border border-border bg-card p-2 shadow-sm sm:flex-row"
              >
                <div className="relative flex-1">
                  <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={q}
                    onChange={(event) => setQ(event.target.value)}
                    placeholder="Search a title, subject or author…"
                    aria-label="Search books"
                    className="h-11 border-0 bg-transparent pl-9 text-base shadow-none focus-visible:ring-0"
                  />
                </div>
                <Button type="submit" className="h-11 gap-2 rounded-xl px-5">
                  Find books
                  <ArrowRight className="size-4" />
                </Button>
              </form>

              <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span>Popular:</span>
                {[
                  ["Class 10", "/discover?classLevel=10"],
                  ["Free books", "/discover?priceType=FREE"],
                  ["CBSE", "/discover?board=CBSE"],
                  ["Pune", "/discover?city=Pune"],
                ].map(([label, href]) => (
                  <Link
                    key={href}
                    href={href}
                    className="rounded-full border border-border bg-card/80 px-2.5 py-1 font-medium transition hover:border-primary/30 hover:text-foreground"
                  >
                    {label}
                  </Link>
                ))}
              </div>

              <div className="mt-9 flex flex-wrap gap-x-7 gap-y-3 text-sm text-muted-foreground">
                <span className="inline-flex items-center gap-2">
                  <HeartHandshake className="size-4 text-primary" /> Give or sell
                </span>
                <span className="inline-flex items-center gap-2">
                  <MapPin className="size-4 text-primary" /> Pickup nearby
                </span>
                <span className="inline-flex items-center gap-2">
                  <MessageCircle className="size-4 text-primary" /> Chat directly
                </span>
              </div>
            </div>

            <div className="relative mx-auto w-full max-w-md lg:max-w-none">
              <div className="relative mx-auto aspect-square w-full max-w-[460px]">
                <div className="absolute inset-8 rounded-[3rem] bg-primary/8" />
                <div className="absolute inset-0 flex items-end justify-center pb-4">
                  {featured.map((book, index) => (
                    <div
                      key={book.title}
                      className={`absolute w-[42%] ${book.rotate} ${book.translate} ${
                        index === 0
                          ? "left-2 bottom-10 z-10"
                          : index === 1
                            ? "left-1/2 bottom-16 z-20 -translate-x-1/2"
                            : "right-2 bottom-6 z-0"
                      }`}
                    >
                      <div className="overflow-hidden rounded-lg shadow-2xl shadow-foreground/25 ring-1 ring-black/10">
                        <BookCover
                          title={book.title}
                          author={book.author}
                          className="aspect-[3/4.2] w-full"
                        />
                      </div>
                      <span className="absolute -right-2 -top-2 rounded-full bg-card px-2.5 py-1 text-[11px] font-semibold text-foreground shadow-md ring-1 ring-border">
                        {book.note}
                      </span>
                    </div>
                  ))}
                </div>

                <div className="absolute left-0 top-6 z-30 rounded-2xl border border-border bg-card/95 px-3.5 py-2.5 shadow-lg backdrop-blur">
                  <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                    Pickup in
                  </p>
                  <p className="mt-0.5 flex items-center gap-1.5 text-sm font-semibold">
                    <MapPin className="size-3.5 text-primary" /> Kothrud, Pune
                  </p>
                </div>

                <div className="absolute bottom-0 right-0 z-30 flex items-center gap-2 rounded-2xl border border-border bg-card/95 px-3.5 py-2.5 shadow-lg backdrop-blur">
                  <span className="flex size-8 items-center justify-center rounded-full bg-secondary">
                    <MessageCircle className="size-4 text-primary" />
                  </span>
                  <div>
                    <p className="text-xs font-semibold">Still available?</p>
                    <p className="text-[10px] text-muted-foreground">
                      Reply in minutes
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="border-y border-border/70 bg-card/50">
          <div className="mx-auto grid w-full max-w-7xl gap-6 px-4 py-10 sm:px-6 md:grid-cols-3 lg:px-8">
            {[
              {
                icon: BookMarked,
                title: "Real books, real shelves",
                text: "Every listing is a physical copy someone actually owns — not a catalogue fantasy.",
              },
              {
                icon: BadgeIndianRupee,
                title: "Free or fair",
                text: "Filter for giveaways, fixed prices or open negotiation. No hidden fees.",
              },
              {
                icon: Truck,
                title: "Close to campus",
                text: "Browse by city and locality, then arrange a pickup that suits you both.",
              },
            ].map((item) => (
              <div key={item.title} className="flex gap-3">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-secondary text-primary">
                  <item.icon className="size-5" />
                </span>
                <div>
                  <h3 className="text-sm font-semibold">{item.title}</h3>
                  <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                    {item.text}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="mx-auto w-full max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-20">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                Recently shelved
              </p>
              <h2 className="mt-2 font-display text-3xl font-semibold sm:text-4xl">
                Fresh on the shelves
              </h2>
              <p className="mt-2 max-w-xl text-sm text-muted-foreground">
                Newly listed books from readers in your city. Updated as people
                add to their shelves.
              </p>
            </div>
            <Button variant="outline" render={<Link href="/discover" />}>
              Browse everything <ArrowRight className="size-4" />
            </Button>
          </div>

          <div className="mt-8 grid grid-cols-2 gap-4 sm:gap-5 md:grid-cols-4">
            {latest.isLoading
              ? Array.from({ length: 4 }).map((_, index) => (
                  <div
                    key={index}
                    className="aspect-[3/4.5] animate-pulse rounded-2xl bg-muted"
                  />
                ))
              : latest.data?.content.map((listing) => (
                  <ListingCard key={listing.id} listing={listing} />
                ))}
          </div>
          {latest.isError ? (
            <p className="mt-4 text-sm text-muted-foreground">
              The shelves are taking a moment to load.{" "}
              <Link href="/discover" className="text-primary underline">
                Try discovery
              </Link>
              .
            </p>
          ) : null}
        </section>

        <section
          id="how-it-works"
          className="border-y border-border/70 bg-secondary/30"
        >
          <div className="mx-auto w-full max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-20">
            <div className="max-w-2xl">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                Simple by design
              </p>
              <h2 className="mt-2 font-display text-3xl font-semibold sm:text-4xl">
                From shelf to backpack in three steps
              </h2>
            </div>
            <div className="mt-10 grid gap-5 md:grid-cols-3">
              {[
                {
                  number: "01",
                  title: "Find the exact edition",
                  text: "Filter by class, board, subject, condition and price. Search by title when you already know what you need.",
                },
                {
                  number: "02",
                  title: "Start a real conversation",
                  text: "Open the listing and message the owner. Ask about condition, pickup, or whether the price is flexible.",
                },
                {
                  number: "03",
                  title: "Meet, check, take it home",
                  text: "Arrange a local pickup, inspect the book, and mark the listing exchanged when it finds a new reader.",
                },
              ].map((step) => (
                <div
                  key={step.number}
                  className="rounded-2xl border border-border bg-card p-6 soft-shadow"
                >
                  <span className="font-mono text-xs font-semibold text-primary">
                    {step.number}
                  </span>
                  <h3 className="mt-4 font-display text-xl font-semibold">
                    {step.title}
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {step.text}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section
          id="for-givers"
          className="mx-auto grid w-full max-w-7xl gap-10 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:items-center lg:px-8 lg:py-24"
        >
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
              For the shelf-clearers
            </p>
            <h2 className="mt-2 font-display text-3xl font-semibold sm:text-4xl">
              That book did its job. Let it do another.
            </h2>
            <p className="mt-4 max-w-lg text-sm leading-relaxed text-muted-foreground sm:text-base">
              List one book or a whole collection in a couple of minutes. Add
              photos, set a condition, choose free or a fair price, and decide
              how you want to be contacted. You stay in control of every
              conversation.
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Button render={<Link href="/listings/new" />} className="gap-2">
                List your first book <ArrowRight className="size-4" />
              </Button>
              <Button
                variant="outline"
                render={<Link href="/register" />}
              >
                Create an account
              </Button>
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {[
              ["Give it away", "Set FREE and pass it on to a student who needs it."],
              ["Name your price", "Fixed price for certainty, or negotiable for a chat."],
              ["Build a collection", "Group a class set so one message covers every book."],
              ["Stay in control", "Pause, withdraw or mark a book exchanged any time."],
            ].map(([title, text], index) => (
              <div
                key={title}
                className={`rounded-2xl border border-border p-5 ${
                  index % 2 === 0 ? "bg-card" : "bg-accent/40"
                }`}
              >
                <h3 className="text-sm font-semibold">{title}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                  {text}
                </p>
              </div>
            ))}
          </div>
        </section>

        <section
          id="safety"
          className="mx-auto w-full max-w-7xl px-4 pb-16 sm:px-6 lg:px-8 lg:pb-24"
        >
          <div className="relative overflow-hidden rounded-3xl bg-primary px-6 py-10 text-primary-foreground sm:px-10 sm:py-12">
            <div className={`absolute -right-10 -top-16 size-64 rounded-full ${coverClass("bridge")} opacity-30 blur-2xl`} />
            <div className="relative grid gap-8 md:grid-cols-[1.4fr_1fr] md:items-center">
              <div>
                <h2 className="font-display text-3xl font-semibold sm:text-4xl">
                  A small, local, human way to share books.
                </h2>
                <p className="mt-3 max-w-xl text-sm leading-relaxed text-primary-foreground/75 sm:text-base">
                  ResourceSwapHub keeps conversations between two people and never
                  asks for payment details. Meet in a public place, inspect the
                  book, and trust your instincts.
                </p>
              </div>
              <div className="flex flex-wrap gap-3 md:justify-end">
                <Button
                  render={<Link href="/discover" />}
                  className="bg-primary-foreground text-primary hover:bg-primary-foreground/90"
                >
                  Find a book
                </Button>
                <Button
                  variant="outline"
                  render={<Link href="/listings/new" />}
                  className="border-primary-foreground/30 bg-transparent text-primary-foreground hover:bg-primary-foreground/10 hover:text-primary-foreground"
                >
                  Share a book
                </Button>
              </div>
            </div>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  );
}
