"use client";

import { useRef, useState } from "react";
import { ImagePlus, Loader2, X } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { apiClient, errorMessage } from "@/lib/api";

export interface Photo {
  id: string;
  url: string;
}

export function mediaIdFromUrl(url: string): string | null {
  const match = url.match(/\/media\/([0-9a-f-]{36})/i);
  return match?.[1] ?? null;
}

export function PhotoUploader({
  photos,
  onChange,
  max = 5,
}: {
  photos: Photo[];
  onChange: (photos: Photo[]) => void;
  max?: number;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  async function upload(files: FileList) {
    const remaining = max - photos.length;
    if (remaining <= 0) {
      toast.error(`You can add up to ${max} photos.`);
      return;
    }
    const selected = Array.from(files).slice(0, remaining);
    setUploading(true);
    try {
      const uploaded: Photo[] = [];
      for (const file of selected) {
        if (file.size > 5 * 1024 * 1024) {
          toast.error(`${file.name} is larger than 5 MB.`);
          continue;
        }
        const media = await apiClient.uploadMedia(file, "LISTING");
        uploaded.push({ id: media.id, url: media.url });
      }
      if (uploaded.length) onChange([...photos, ...uploaded]);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  return (
    <div>
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
        {photos.map((photo, index) => (
          <div
            key={photo.id}
            className="group relative aspect-square overflow-hidden rounded-xl bg-muted ring-1 ring-border"
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={photo.url}
              alt={`Book photo ${index + 1}`}
              className="size-full object-cover"
            />
            {index === 0 ? (
              <span className="absolute left-1.5 top-1.5 rounded-full bg-black/55 px-1.5 py-0.5 text-[9px] font-medium text-white">
                Cover
              </span>
            ) : null}
            <button
              type="button"
              onClick={() => onChange(photos.filter((item) => item.id !== photo.id))}
              className="absolute right-1.5 top-1.5 flex size-6 items-center justify-center rounded-full bg-black/55 text-white opacity-0 transition group-hover:opacity-100 focus-visible:opacity-100"
              aria-label="Remove photo"
            >
              <X className="size-3.5" />
            </button>
          </div>
        ))}

        {photos.length < max ? (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            disabled={uploading}
            className="flex aspect-square flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-border bg-card text-muted-foreground transition hover:border-primary/40 hover:text-foreground disabled:opacity-60"
          >
            {uploading ? (
              <Loader2 className="size-5 animate-spin" />
            ) : (
              <ImagePlus className="size-5" />
            )}
            <span className="text-[10px] font-medium">
              {uploading ? "Uploading" : "Add photo"}
            </span>
          </button>
        ) : null}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        className="sr-only"
        onChange={(event) => {
          if (event.target.files?.length) void upload(event.target.files);
        }}
      />

      <div className="mt-2 flex items-center justify-between gap-2">
        <p className="text-[11px] text-muted-foreground">
          JPG, PNG or WebP · up to 5 MB each · {photos.length}/{max} added
        </p>
        {photos.length ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onChange([])}
            className="h-6 px-2 text-[11px] text-muted-foreground"
          >
            Clear all
          </Button>
        ) : null}
      </div>
    </div>
  );
}
