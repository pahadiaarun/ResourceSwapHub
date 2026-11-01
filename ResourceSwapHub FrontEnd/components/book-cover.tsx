import { cn } from "@/lib/utils";
import { coverClass } from "@/lib/format";

interface BookCoverProps {
  title: string;
  author?: string;
  imageUrl?: string | null;
  className?: string;
  compact?: boolean;
}

export function BookCover({
  title,
  author,
  imageUrl,
  className,
  compact = false,
}: BookCoverProps) {
  if (imageUrl) {
    return (
      <div className={cn("relative overflow-hidden bg-muted", className)}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={imageUrl}
          alt={`${title} cover`}
          className="size-full object-cover"
          loading="lazy"
        />
      </div>
    );
  }

  return (
    <div
      className={cn(
        "relative overflow-hidden",
        coverClass(title),
        className
      )}
    >
      <div className="absolute inset-y-0 left-0 w-1.5 bg-black/15" />
      <div className="absolute inset-0 opacity-40 [background-image:radial-gradient(circle_at_1px_1px,rgba(255,255,255,0.5)_1px,transparent_0)] [background-size:14px_14px]" />
      <div className="relative flex h-full flex-col justify-between p-4 text-white">
        <div className="flex items-start justify-between gap-2">
          <span className="rounded-full border border-white/35 bg-white/10 px-2 py-0.5 text-[9px] font-semibold uppercase tracking-[0.18em] backdrop-blur-sm">
            ResourceSwapHub
          </span>
        </div>
        <div>
          <p
            className={cn(
              "font-display font-semibold leading-tight drop-shadow-sm",
              compact ? "text-sm" : "text-lg sm:text-xl"
            )}
          >
            {title}
          </p>
          {author ? (
            <p className="mt-1.5 line-clamp-1 text-[11px] text-white/75">
              {author}
            </p>
          ) : null}
        </div>
        <div className="absolute bottom-3 right-3 h-px w-10 bg-white/35" />
      </div>
    </div>
  );
}
