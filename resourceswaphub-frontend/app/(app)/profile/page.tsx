import Link from "next/link";
import { ArrowRight, Briefcase, GraduationCap, Lightbulb } from "lucide-react";
import { ProfileForm } from "@/components/profile-form";

export const metadata = {
  title: "Your profile",
};

// The (app) shell authenticates in the browser, so this page renders nothing during
// prerender and instant-navigation validation cannot run for this segment.
export const instant = false;

export default function ProfilePage() {
  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
          Your account
        </p>
        <h1 className="mt-1.5 font-display text-3xl font-semibold sm:text-4xl">
          Profile & pickup area
        </h1>
        <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
          This is what neighbours see when they find your books. Keep it short,
          friendly and accurate.
        </p>
      </div>

      <Link
        href="/profile/extended"
        className="group mb-6 flex flex-col gap-3 rounded-2xl border border-border bg-card p-4 transition hover:border-primary/40 hover:shadow-sm sm:flex-row sm:items-center sm:gap-4 sm:p-5"
      >
        <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <GraduationCap className="size-5" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-center gap-2 text-sm font-semibold text-foreground">
            School, college & work details
            <ArrowRight className="size-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-primary" />
          </span>
          <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <GraduationCap className="size-3.5" /> Education
            </span>
            <span className="inline-flex items-center gap-1">
              <Briefcase className="size-3.5" /> Employment
            </span>
            <span className="inline-flex items-center gap-1">
              <Lightbulb className="size-3.5" /> Interests
            </span>
            <span className="text-muted-foreground/80">
              Optional · private by default
            </span>
          </span>
        </span>
      </Link>

      <ProfileForm />
    </div>
  );
}
