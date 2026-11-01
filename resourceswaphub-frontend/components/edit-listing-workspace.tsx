"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { ListingForm } from "@/components/listing-form";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export function EditListingWorkspace() {
  const params = useParams<{ id: string }>();
  const { user } = useAuth();
  const listing = useQuery({
    queryKey: ["listing", params.id],
    queryFn: () => apiClient.listing(params.id),
    enabled: Boolean(params.id),
  });

  if (listing.isLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" /> Loading your listing…
      </div>
    );
  }

  if (listing.isError || !listing.data) {
    return (
      <div className="mx-auto max-w-lg py-16 text-center">
        <h1 className="font-display text-3xl font-semibold">Listing not found</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {errorMessage(listing.error)}
        </p>
        <Button render={<Link href="/library" />} className="mt-5">
          Back to my library
        </Button>
      </div>
    );
  }

  if (user && listing.data.seller.id !== user.id) {
    return (
      <div className="mx-auto max-w-lg py-16 text-center">
        <h1 className="font-display text-3xl font-semibold">This isn&apos;t your listing</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Only the owner can edit a listing.
        </p>
        <Button render={<Link href="/library" />} className="mt-5">
          Back to my library
        </Button>
      </div>
    );
  }

  return <ListingForm listing={listing.data} />;
}
