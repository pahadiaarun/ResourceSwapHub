"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, ShieldCheck, Lock, Globe } from "lucide-react";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { ProfileVisibility } from "@/lib/types";

const OPTIONS: { value: ProfileVisibility; label: string; hint: string; icon: typeof Lock }[] = [
  { value: "PRIVATE", label: "Private", hint: "Only you can see your extended profile. Others see just your name and photo.", icon: Lock },
  { value: "CONNECTIONS_ONLY", label: "Connections only", hint: "Treated as private for now — connections are coming later. Others see only your name and photo.", icon: ShieldCheck },
  { value: "PUBLIC", label: "Public", hint: "Anyone can see your public profile card. Individual education, work and skill entries still have their own visibility.", icon: Globe },
];

export function PrivacyControls() {
  const queryClient = useQueryClient();
  const { data: profile, isLoading } = useQuery({
    queryKey: ["extended-profile"],
    queryFn: apiClient.getExtendedProfile,
  });
  const [selected, setSelected] = useState<ProfileVisibility | null>(null);

  const current = selected ?? profile?.profileVisibility ?? "PRIVATE";

  const save = useMutation({
    mutationFn: (value: ProfileVisibility) => apiClient.updateExtendedProfile({ profileVisibility: value }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["extended-profile"] });
      toast.success("Privacy updated");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <section className="rounded-2xl border border-border bg-card p-6">
      <div className="mb-4">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <ShieldCheck className="size-5 text-primary" /> Privacy
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Control who can see your profile. Everything is private by default, and each education,
          work and skill entry can be set individually.
        </p>
      </div>

      {isLoading ? (
        <div className="flex justify-center p-6"><Loader2 className="size-5 animate-spin" /></div>
      ) : (
        <>
          <div className="grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Profile visibility">
            {OPTIONS.map((option) => {
              const Icon = option.icon;
              const isActive = current === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={isActive}
                  onClick={() => setSelected(option.value)}
                  className={cn(
                    "rounded-xl border p-3 text-left transition",
                    isActive ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"
                  )}
                >
                  <span className="flex items-center gap-2 text-sm font-medium">
                    <Icon className="size-4 text-primary" /> {option.label}
                  </span>
                  <span className="mt-1 block text-xs text-muted-foreground">{option.hint}</span>
                </button>
              );
            })}
          </div>

          {selected && selected !== profile?.profileVisibility && (
            <div className="mt-4 flex justify-end gap-2">
              <Button type="button" variant="ghost" className="rounded-xl" onClick={() => setSelected(null)}>
                Cancel
              </Button>
              <Button
                type="button"
                className="gap-2 rounded-xl"
                onClick={() => save.mutate(selected)}
                disabled={save.isPending}
              >
                {save.isPending && <Loader2 className="size-4 animate-spin" />}
                Save privacy
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
