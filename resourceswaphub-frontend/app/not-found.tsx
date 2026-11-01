import Link from "next/link";
import { BookOpen } from "lucide-react";
import { LogoMark } from "@/components/brand";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center bg-background px-6 text-center">
      <LogoMark className="size-12" />
      <span className="mt-6 flex size-12 items-center justify-center rounded-2xl bg-secondary text-primary">
        <BookOpen className="size-5" />
      </span>
      <h1 className="mt-4 font-display text-3xl font-semibold">
        This page is off the shelf
      </h1>
      <p className="mt-2 max-w-sm text-sm text-muted-foreground">
        The link may be old, or the book may have found a new reader already.
      </p>
      <div className="mt-6 flex gap-2">
        <Button render={<Link href="/discover" />}>Discover books</Button>
        <Button variant="outline" render={<Link href="/" />}>
          Go home
        </Button>
      </div>
    </main>
  );
}
