import { Suspense } from "react";
import { DiscoverClient } from "@/components/discover-client";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";

export const metadata = {
  title: "Discover books",
  description:
    "Browse free and affordable school books by class, board, subject, condition and city.",
};

export default function DiscoverPage() {
  return (
    <div className="flex min-h-svh flex-col">
      <PublicHeader />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8 lg:py-10">
        <Suspense
          fallback={
            <div className="h-96 animate-pulse rounded-2xl bg-muted/60" />
          }
        >
          <DiscoverClient />
        </Suspense>
      </main>
      <SiteFooter />
    </div>
  );
}
