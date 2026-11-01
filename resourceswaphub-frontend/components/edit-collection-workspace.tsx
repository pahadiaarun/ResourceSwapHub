"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { CollectionForm } from "@/components/collection-form";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage } from "@/lib/api";

export function EditCollectionWorkspace() {
  const params = useParams<{ id: string }>();
  const collection = useQuery({
    queryKey: ["collection", params.id],
    queryFn: () => apiClient.collection(params.id),
    enabled: Boolean(params.id),
  });

  if (collection.isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" /> Loading collection…
      </div>
    );
  }

  if (collection.isError || !collection.data) {
    return (
      <div className="mx-auto max-w-lg py-16 text-center">
        <h1 className="font-display text-3xl font-semibold">Collection not found</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {errorMessage(collection.error)}
        </p>
        <Button render={<Link href="/library" />} className="mt-5">
          Back to my library
        </Button>
      </div>
    );
  }

  return <CollectionForm collection={collection.data} />;
}
