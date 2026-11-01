"use client";

import { Lock, Users, Globe } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { RecordVisibility } from "@/lib/types";

export const VISIBILITY_OPTIONS: { value: RecordVisibility; label: string; hint: string }[] = [
  { value: "PRIVATE", label: "Only me", hint: "Visible only to you." },
  { value: "PLATFORM_USERS", label: "Platform users", hint: "Anyone signed in to ResourceSwapHub." },
  { value: "PUBLIC", label: "Public", hint: "Anyone, including people not signed in." },
];

const ICONS: Record<RecordVisibility, typeof Lock> = {
  PRIVATE: Lock,
  PLATFORM_USERS: Users,
  PUBLIC: Globe,
};

const LABELS: Record<RecordVisibility, string> = {
  PRIVATE: "Only me",
  PLATFORM_USERS: "Platform users",
  PUBLIC: "Public",
};

export function VisibilityBadge({ value }: { value?: RecordVisibility | null }) {
  const v: RecordVisibility = (value ?? "PRIVATE") as RecordVisibility;
  const Icon = ICONS[v] ?? Lock;
  return (
    <Badge variant="outline" className="gap-1 font-normal text-muted-foreground">
      <Icon className="size-3" /> {LABELS[v] ?? v}
    </Badge>
  );
}
