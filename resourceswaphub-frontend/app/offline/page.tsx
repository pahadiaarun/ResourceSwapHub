import Link from "next/link";
import { WifiOff } from "lucide-react";
import { LogoMark } from "@/components/brand";
import { Button } from "@/components/ui/button";

export const metadata = {
  title: "Offline",
};

export default function OfflinePage() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center bg-background px-6 text-center">
      <LogoMark className="size-12" />
      <span className="mt-6 flex size-12 items-center justify-center rounded-2xl bg-secondary text-primary">
        <WifiOff className="size-5" />
      </span>
      <h1 className="mt-4 font-display text-3xl font-semibold">
        You&apos;re offline
      </h1>
      <p className="mt-2 max-w-sm text-sm leading-relaxed text-muted-foreground">
        The shelves will be back as soon as you reconnect. Pages you already
        visited may still be available.
      </p>
      <Button render={<Link href="/" />} className="mt-6">
        Try again
      </Button>
    </main>
  );
}
