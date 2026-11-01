import Link from "next/link";
import { LogoMark } from "@/components/brand";

export function SiteFooter() {
  return (
    <footer className="mt-auto border-t border-border/70 bg-card/60">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-8 px-4 py-10 sm:px-6 md:flex-row md:items-start md:justify-between lg:px-8">
        <div className="max-w-sm">
          <div className="flex items-center gap-2.5">
            <LogoMark className="size-7" />
            <span className="font-display text-lg font-semibold">ResourceSwapHub</span>
          </div>
          <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
            A student-first resource-swapping community. Every book, guide or
            study material deserves a second reader, and every reader deserves
            a fair price.
          </p>
        </div>
        <div className="grid grid-cols-2 gap-10 text-sm sm:grid-cols-3">
          <div>
            <p className="font-medium">Explore</p>
            <div className="mt-3 flex flex-col gap-2 text-muted-foreground">
              <Link href="/discover" className="hover:text-foreground">All books</Link>
              <Link href="/discover?priceType=FREE" className="hover:text-foreground">Free books</Link>
              <Link href="/discover?classLevel=10" className="hover:text-foreground">Class 10</Link>
              <Link href="/discover?board=CBSE" className="hover:text-foreground">CBSE</Link>
            </div>
          </div>
          <div>
            <p className="font-medium">Community</p>
            <div className="mt-3 flex flex-col gap-2 text-muted-foreground">
              <Link href="/#how-it-works" className="hover:text-foreground">How it works</Link>
              <Link href="/#safety" className="hover:text-foreground">Safety tips</Link>
              <Link href="/register" className="hover:text-foreground">Create account</Link>
              <Link href="/login" className="hover:text-foreground">Sign in</Link>
            </div>
          </div>
          <div>
            <p className="font-medium">Your shelf</p>
            <div className="mt-3 flex flex-col gap-2 text-muted-foreground">
              <Link href="/listings/new" className="hover:text-foreground">List a book</Link>
              <Link href="/library" className="hover:text-foreground">My listings</Link>
              <Link href="/inbox" className="hover:text-foreground">Inbox</Link>
              <Link href="/profile" className="hover:text-foreground">Profile</Link>
            </div>
          </div>
        </div>
      </div>
      <div className="border-t border-border/60">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-2 px-4 py-4 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
          {/* Year is a build-time constant, not Date.now(): a prerendered
              footer must not call new Date() during static generation. */}
          <p>© 2026 ResourceSwapHub. Built for readers, not algorithms.</p>
          <p>Made with care for students across India.</p>
        </div>
      </div>
    </footer>
  );
}
