import { Suspense } from "react";
import { AppShell } from "@/components/app-shell";

// Routes under (app) are gated by client-side auth in AppShell, so their pages
// never render during prerender and instant-navigation validation cannot run.
export const instant = false;

function ShellFallback() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-3 text-sm text-muted-foreground">
      <span className="size-7 animate-spin rounded-full border-2 border-primary/25 border-t-primary" />
      Opening your shelf…
    </div>
  );
}

export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <Suspense fallback={<ShellFallback />}>
      <AppShell>{children}</AppShell>
    </Suspense>
  );
}
