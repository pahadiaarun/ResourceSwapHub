"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { ArrowLeft, Layers, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  LocationSelector,
  locationFromInfo,
  type LocationValue,
} from "@/components/location-select";
import { apiClient, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { CollectionDetail } from "@/lib/types";

const schema = z.object({
  title: z
    .string()
    .trim()
    .min(2, "Give the collection a name")
    .max(120, "Name must be 120 characters or fewer"),
  description: z
    .string()
    .trim()
    .max(1000, "Description must be 1000 characters or fewer"),
  locality: z.string().trim().max(120, "Locality is too long"),
});

type Values = z.infer<typeof schema>;

export function CollectionForm({
  collection,
}: {
  collection?: CollectionDetail;
}) {
  const router = useRouter();
  const { user } = useAuth();
  const [location, setLocation] = useState<LocationValue>(() => {
    const fromCollection = locationFromInfo(collection?.location);
    return fromCollection.countryId || fromCollection.stateId || fromCollection.cityId
      ? fromCollection
      : locationFromInfo(user?.location);
  });
  const [locationError, setLocationError] = useState<string | null>(null);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: collection?.title ?? "",
      description: collection?.description ?? "",
      locality: collection?.locality ?? user?.locality ?? "",
    },
  });

  async function onSubmit(values: Values) {
    if (!location.cityId) {
      setLocationError("Choose the city where this set is available.");
      toast.error("Select a city from the list.");
      return;
    }
    try {
      const payload = {
        title: values.title,
        description: values.description || null,
        countryId: location.countryId ?? null,
        stateId: location.stateId ?? null,
        cityId: location.cityId ?? null,
        locality: values.locality || null,
      };
      const saved = collection
        ? await apiClient.updateCollection(collection.id, payload)
        : await apiClient.createCollection(payload);
      setLocationError(null);
      toast.success(collection ? "Collection updated." : "Collection created.");
      router.push(`/collections/${saved.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="mx-auto max-w-2xl">
      <button
        type="button"
        onClick={() => router.back()}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back
      </button>

      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
        {collection ? "Edit collection" : "New collection"}
      </p>
      <h1 className="mt-1.5 font-display text-3xl font-semibold sm:text-4xl">
        {collection ? "Update the set" : "Group books into a set"}
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Collections keep a class set together — one link, one message, every
        book a student needs for the term.
      </p>

      <div className="mt-6 rounded-2xl border border-border bg-card p-5">
        <div className="flex items-center gap-2 text-sm font-semibold">
          <Layers className="size-4 text-primary" /> Collection details
        </div>
        <div className="mt-4 flex flex-col gap-4">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">Name</span>
            <Input
              {...form.register("title")}
              placeholder="Class 10 CBSE essentials"
              className="h-11 rounded-xl"
            />
            {form.formState.errors.title ? (
              <span className="text-xs text-destructive">
                {form.formState.errors.title.message}
              </span>
            ) : null}
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">Description</span>
            <textarea
              {...form.register("description")}
              rows={4}
              placeholder="What's included, what condition it's in, and anything a student should know."
              className="w-full resize-y rounded-xl border border-input bg-card px-3 py-2 text-sm outline-none transition focus:border-ring focus:ring-3 focus:ring-ring/20"
            />
            {form.formState.errors.description ? (
              <span className="text-xs text-destructive">
                {form.formState.errors.description.message}
              </span>
            ) : null}
          </label>

          <div>
            <span className="mb-1.5 block text-sm font-medium">Availability location</span>
            <LocationSelector
              id="collection-location"
              value={location}
              onChange={(next) => {
                setLocation(next);
                if (next.cityId) setLocationError(null);
              }}
              autoDefaultCountry
              labels={{ country: "Country", state: "State", city: "City" }}
            />
            {locationError ? (
              <span className="mt-1.5 block text-xs text-destructive">{locationError}</span>
            ) : (
              <span className="mt-1.5 block text-[11px] text-muted-foreground">
                City and state are public; the locality stays a rough pickup hint.
              </span>
            )}
          </div>

          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">Locality</span>
            <Input
              {...form.register("locality")}
              placeholder="Kothrud"
              className="h-11 rounded-xl"
            />
          </label>
        </div>

        <div className="mt-6 flex justify-end border-t border-border pt-4">
          <Button type="submit" disabled={form.formState.isSubmitting} className="gap-2 rounded-xl">
            {form.formState.isSubmitting ? (
              <Loader2 className="size-4 animate-spin" />
            ) : null}
            {collection ? "Save collection" : "Create collection"}
          </Button>
        </div>
      </div>
    </form>
  );
}
