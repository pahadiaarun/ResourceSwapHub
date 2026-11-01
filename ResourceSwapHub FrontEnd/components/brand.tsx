import Link from "next/link";
import { cn } from "@/lib/utils";

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 48 48"
      fill="none"
      aria-hidden="true"
      className={cn("size-8", className)}
    >
      <rect width="48" height="48" rx="14" className="fill-primary" />
      <circle cx="11.5" cy="11.5" r="3.5" className="fill-accent" />
      <path
        d="M15 19.5H29"
        className="stroke-primary-foreground"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <path
        d="M29 15L36 19.5L29 24Z"
        className="fill-primary-foreground"
      />
      <path
        d="M33 30.5H19"
        className="stroke-primary-foreground"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <path
        d="M19 26L12 30.5L19 35Z"
        className="fill-primary-foreground"
      />
    </svg>
  );
}

export function Logo({ className }: { className?: string }) {
  return (
    <Link
      href="/"
      className={cn("inline-flex items-center gap-2.5", className)}
      aria-label="ResourceSwapHub home"
    >
      <LogoMark />
      <span className="font-display text-xl font-semibold tracking-tight">
        ResourceSwapHub
      </span>
    </Link>
  );
}
