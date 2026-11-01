"use client";

import Link from "next/link";
import { useState } from "react";
import { Menu, Plus, Search, X } from "lucide-react";
import { Logo } from "@/components/brand";
import { Avatar } from "@/components/avatar";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";

const links = [
  { href: "/discover", label: "Discover" },
  { href: "/#how-it-works", label: "How it works" },
  { href: "/#for-givers", label: "For givers" },
];

export function PublicHeader() {
  const { user, loading } = useAuth();
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 border-b border-border/70 bg-background/85 backdrop-blur-xl">
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center gap-6 px-4 sm:px-6 lg:px-8">
        <Logo />

        <nav className="hidden items-center gap-1 md:flex">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="rounded-lg px-3 py-2 text-sm text-muted-foreground transition hover:bg-muted hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            aria-label={open ? "Close menu" : "Open menu"}
            onClick={() => setOpen((value) => !value)}
          >
            {open ? <X /> : <Menu />}
          </Button>

          {loading ? (
            <div className="h-8 w-20 animate-pulse rounded-full bg-muted" />
          ) : user ? (
            <>
              <Link
                href="/inbox"
                className="hidden rounded-lg px-3 py-2 text-sm text-muted-foreground transition hover:bg-muted hover:text-foreground sm:block"
              >
                Inbox
              </Link>
              <Link
                href="/library"
                className="hidden items-center gap-2 rounded-full border border-border bg-card py-1 pl-1 pr-3 text-sm font-medium transition hover:bg-muted sm:inline-flex"
              >
                <Avatar
                  name={user.name}
                  src={user.avatarUrl}
                  className="size-7"
                />
                <span className="max-w-28 truncate">{user.name.split(" ")[0]}</span>
              </Link>
              <Button render={<Link href="/listings/new" />} className="hidden sm:inline-flex">
                <Plus className="size-4" />
                List a book
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                render={<Link href="/login" />}
                className="hidden sm:inline-flex"
              >
                Sign in
              </Button>
              <Button render={<Link href="/register" />} className="shrink-0">
                {/* Shorten the CTA on the smallest screens so the header
                    never widens the page beyond the viewport. */}
                <span className="sm:hidden">Join</span>
                <span className="hidden sm:inline">Join ResourceSwapHub</span>
              </Button>
            </>
          )}
        </div>
      </div>

      {open ? (
        <div className="border-t border-border bg-background px-4 py-4 md:hidden">
          <nav className="flex flex-col gap-1">
            {links.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                onClick={() => setOpen(false)}
                className="rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-muted"
              >
                {link.label}
              </Link>
            ))}
            <Link
              href="/discover"
              onClick={() => setOpen(false)}
              className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-muted"
            >
              <Search className="size-4" /> Search books
            </Link>
            {user ? (
              <Link
                href="/library"
                onClick={() => setOpen(false)}
                className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-muted"
              >
                <Avatar name={user.name} src={user.avatarUrl} className="size-6" />
                My library
              </Link>
            ) : (
              <Link
                href="/login"
                onClick={() => setOpen(false)}
                className="rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-muted"
              >
                Sign in
              </Link>
            )}
          </nav>
        </div>
      ) : null}
    </header>
  );
}
