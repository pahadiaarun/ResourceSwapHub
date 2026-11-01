"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import {
  Camera,
  CheckCircle2,
  Loader2,
  LogOut,
  MapPin,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { Avatar } from "@/components/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiClient, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";

const schema = z.object({
  name: z
    .string()
    .trim()
    .min(2, "Name must be at least 2 characters")
    .max(80, "Name must be 80 characters or fewer"),
  city: z.string().trim().max(80, "City is too long"),
  locality: z.string().trim().max(120, "Locality is too long"),
});

type Values = z.infer<typeof schema>;

export function ProfileForm() {
  const { user, setUser, logout } = useAuth();
  const router = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: user?.name ?? "",
      city: user?.city ?? "",
      locality: user?.locality ?? "",
    },
  });

  async function onSubmit(values: Values) {
    try {
      const updated = await apiClient.updateProfile({
        name: values.name,
        city: values.city || null,
        locality: values.locality || null,
      });
      setUser(updated);
      toast.success(
        updated.profileComplete
          ? "Profile saved. Your shelf is ready."
          : "Profile saved. Add both city and locality to start listing."
      );
      router.refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  async function uploadAvatar(file: File) {
    setUploading(true);
    try {
      const media = await apiClient.uploadMedia(file, "AVATAR");
      const updated = await apiClient.updateProfile({
        name: form.getValues("name") || user?.name || "ResourceSwapHub reader",
        city: form.getValues("city") || null,
        locality: form.getValues("locality") || null,
        avatarMediaId: media.id,
      });
      setUser(updated);
      toast.success("Profile photo updated.");
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="rounded-2xl border border-border bg-card p-5 sm:p-6"
      >
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
          <div className="relative">
            <Avatar
              name={user?.name}
              src={user?.avatarUrl}
              className="size-20 text-xl"
            />
            <button
              type="button"
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
              className="absolute -bottom-1 -right-1 flex size-7 items-center justify-center rounded-full bg-primary text-primary-foreground ring-2 ring-card transition hover:bg-primary/90 disabled:opacity-60"
              aria-label="Change profile photo"
            >
              {uploading ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Camera className="size-3.5" />
              )}
            </button>
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void uploadAvatar(file);
              }}
            />
          </div>
          <div>
            <h2 className="font-display text-2xl font-semibold">
              {user?.name ?? "Your profile"}
            </h2>
            <p className="text-sm text-muted-foreground">{user?.email}</p>
            <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
              <MapPin className="size-3" />
              {user?.locality ? `${user.locality}, ` : ""}
              {user?.city || "Add your city and locality"}
            </p>
          </div>
        </div>

        <div className="mt-7 grid gap-4 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 sm:col-span-2">
            <span className="text-sm font-medium">Display name</span>
            <Input
              {...form.register("name")}
              className="h-11 rounded-xl"
              autoComplete="name"
            />
            {form.formState.errors.name ? (
              <span className="text-xs text-destructive">
                {form.formState.errors.name.message}
              </span>
            ) : null}
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">City</span>
            <Input
              {...form.register("city")}
              className="h-11 rounded-xl"
              placeholder="Pune"
              autoComplete="address-level2"
            />
            {form.formState.errors.city ? (
              <span className="text-xs text-destructive">
                {form.formState.errors.city.message}
              </span>
            ) : null}
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">Locality</span>
            <Input
              {...form.register("locality")}
              className="h-11 rounded-xl"
              placeholder="Kothrud"
              autoComplete="address-level3"
            />
            {form.formState.errors.locality ? (
              <span className="text-xs text-destructive">
                {form.formState.errors.locality.message}
              </span>
            ) : null}
          </label>
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5">
          <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <ShieldCheck className="size-3.5 text-primary" />
            Your email and exact address are never shown publicly.
          </p>
          <Button
            type="submit"
            disabled={form.formState.isSubmitting}
            className="gap-2 rounded-xl"
          >
            {form.formState.isSubmitting ? (
              <Loader2 className="size-4 animate-spin" />
            ) : null}
            Save changes
          </Button>
        </div>
      </form>

      <div className="flex flex-col gap-4">
        <div className="rounded-2xl border border-border bg-card p-5">
          <div className="flex items-center gap-2">
            <span
              className={`flex size-8 items-center justify-center rounded-full ${
                user?.profileComplete
                  ? "bg-emerald-100 text-emerald-700"
                  : "bg-accent text-accent-foreground"
              }`}
            >
              {user?.profileComplete ? (
                <CheckCircle2 className="size-4" />
              ) : (
                <UserRound className="size-4" />
              )}
            </span>
            <h3 className="text-sm font-semibold">
              {user?.profileComplete ? "Profile complete" : "Finish your profile"}
            </h3>
          </div>
          <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
            {user?.profileComplete
              ? "You can list books and message owners. Your city and locality help students find books nearby."
              : "Add both a city and a locality so students can find your books and so you can start conversations."}
          </p>
          <ul className="mt-4 flex flex-col gap-2 text-sm">
            {[
              ["Name", Boolean(user?.name)],
              ["City", Boolean(user?.city)],
              ["Locality", Boolean(user?.locality)],
              ["Profile photo", Boolean(user?.avatarUrl)],
            ].map(([label, done]) => (
              <li key={label as string} className="flex items-center justify-between">
                <span className="text-muted-foreground">{label as string}</span>
                <span
                  className={
                    done ? "text-xs font-medium text-emerald-700" : "text-xs text-muted-foreground"
                  }
                >
                  {done ? "Done" : "Optional"}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div className="rounded-2xl border border-border bg-secondary/40 p-5 text-sm leading-relaxed text-muted-foreground">
          <p className="font-medium text-foreground">A note on privacy</p>
          <p className="mt-2">
            ResourceSwapHub shows your name, city, locality and profile photo to
            people viewing your listings. Conversations are only visible to the
            two participants.
          </p>
        </div>

        <div className="rounded-2xl border border-border bg-card p-5">
          <p className="text-sm font-semibold text-foreground">Session</p>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            Signing out clears your session on this device. Your listings and
            conversations stay safely on ResourceSwapHub.
          </p>
          <Button
            type="button"
            variant="outline"
            className="mt-4 w-full gap-2 rounded-xl"
            onClick={async () => {
              await logout();
              router.replace("/");
            }}
          >
            <LogOut className="size-4" />
            Sign out
          </Button>
        </div>
      </div>
    </div>
  );
}
