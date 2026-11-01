"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import {
  BookOpen,
  Compass,
  Inbox,
  Library,
  LogOut,
  Plus,
  UserRound,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Logo, LogoMark } from "@/components/brand";
import { Avatar } from "@/components/avatar";
import { Button } from "@/components/ui/button";
import { apiClient } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

const nav = [
  { href: "/discover", label: "Discover", icon: Compass },
  { href: "/inbox", label: "Inbox", icon: Inbox, badge: true },
  { href: "/library", label: "My library", icon: Library },
  { href: "/listings/new", label: "List a book", icon: Plus },
  { href: "/profile", label: "Profile", icon: UserRound },
];

function Spinner({ label }: { label: string }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-sm text-muted-foreground">
      <span className="size-7 animate-spin rounded-full border-2 border-primary/25 border-t-primary" />
      {label}
    </div>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const { user, loading, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && !user) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [loading, user, pathname, router]);

  const unread = useQuery({
    queryKey: ["unread-count"],
    queryFn: apiClient.unreadCount,
    enabled: Boolean(user),
    refetchInterval: 30_000,
  });

  if (loading || !user) {
    return <Spinner label={loading ? "Opening your shelf…" : "Taking you to sign in…"} />;
  }

  const active = (href: string) =>
    href === "/library"
      ? pathname.startsWith("/library") ||
        pathname.startsWith("/collections") ||
        (pathname.startsWith("/listings") && pathname !== "/listings/new")
      : pathname === href || (href === "/inbox" && pathname.startsWith("/inbox"));

  return (
    <div className="min-h-svh bg-background">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-sidebar-border bg-sidebar lg:flex">
        <div className="px-5 pb-4 pt-6">
          <Logo />
        </div>

        <div className="px-4">
          <Button
            render={<Link href="/listings/new" />}
            className="h-10 w-full justify-start gap-2 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90"
          >
            <Plus className="size-4" />
            Give a book a home
          </Button>
        </div>

        <nav className="mt-6 flex flex-1 flex-col gap-1 px-3">
          {nav.map((item) => {
            const Icon = item.icon;
            const isActive = active(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground"
                )}
              >
                <Icon
                  className={cn(
                    "size-4.5",
                    isActive ? "text-primary" : "text-muted-foreground group-hover:text-primary"
                  )}
                />
                <span>{item.label}</span>
                {item.badge && (unread.data?.count ?? 0) > 0 ? (
                  <span className="ml-auto inline-flex min-w-5 items-center justify-center rounded-full bg-primary px-1.5 py-0.5 text-[10px] font-semibold text-primary-foreground">
                    {unread.data?.count}
                  </span>
                ) : null}
              </Link>
            );
          })}
        </nav>

        <div className="m-3 rounded-2xl border border-sidebar-border bg-card p-3">
          <div className="flex items-center gap-2.5">
            <Avatar name={user.name} src={user.avatarUrl} />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{user.name}</p>
              <p className="truncate text-xs text-muted-foreground">
                {user.locality ? `${user.locality}, ` : ""}
                {user.city ?? "Add your city"}
              </p>
            </div>
          </div>
          <div className="mt-3 flex items-center justify-between">
            <Link
              href="/profile"
              className="text-xs font-medium text-muted-foreground hover:text-foreground"
            >
              Edit profile
            </Link>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="Sign out"
              onClick={async () => {
                await logout();
                router.replace("/");
              }}
            >
              <LogOut className="size-3.5" />
            </Button>
          </div>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border/70 bg-background/85 px-4 backdrop-blur-xl lg:hidden">
          <Link href="/discover" className="flex items-center gap-2">
            <LogoMark className="size-7" />
            <span className="font-display text-lg font-semibold">ResourceSwapHub</span>
          </Link>
          <Link href="/profile" aria-label="Profile">
            <Avatar name={user.name} src={user.avatarUrl} className="size-8" />
          </Link>
        </header>

        <main className="mx-auto w-full max-w-7xl px-4 pb-24 pt-5 sm:px-6 lg:px-8 lg:pb-10 lg:pt-8">
          {children}
        </main>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background/95 px-2 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl lg:hidden">
        <div className="mx-auto flex max-w-md items-end justify-around">
          {[nav[0], nav[1], nav[3], nav[2], nav[4]].map((item) => {
            const Icon = item.icon;
            const isPrimary = item.href === "/listings/new";
            const isActive = active(item.href);
            if (isPrimary) {
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-label={item.label}
                  className="-mt-5 flex size-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-lg shadow-primary/25"
                >
                  <Plus className="size-6" />
                </Link>
              );
            }
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "relative flex flex-1 flex-col items-center gap-1 py-2.5 text-[10px] font-medium transition",
                  isActive ? "text-primary" : "text-muted-foreground"
                )}
              >
                <Icon className="size-5" />
                {item.label === "My library" ? "Library" : item.label}
                {item.badge && (unread.data?.count ?? 0) > 0 ? (
                  <span className="absolute right-1/4 top-1.5 size-2 rounded-full bg-destructive" />
                ) : null}
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
