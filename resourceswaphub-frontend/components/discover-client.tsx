"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronLeft,
  ChevronRight,
  Compass,
  Filter,
  RotateCcw,
  Search,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { ListingCard } from "@/components/listing-card";
import {
  LocationSelector,
  useLocationLabels,
  type LocationValue,
} from "@/components/location-select";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiClient, errorMessage } from "@/lib/api";
import type { SearchFilters } from "@/lib/types";

const SELECT_CLASS =
  "h-10 w-full appearance-none rounded-xl border border-input bg-card px-3 pr-8 text-sm outline-none transition focus:border-ring focus:ring-3 focus:ring-ring/20";

const FILTER_KEYS = [
  "q",
  "subject",
  "classLevel",
  "board",
  "priceType",
  "condition",
  "city",
  "stateId",
  "cityId",
  "districtId",
  "locality",
  "page",
] as const;

function readFilters(params: URLSearchParams): SearchFilters {
  const result: SearchFilters = { size: 12, page: 0 };
  for (const key of FILTER_KEYS) {
    const value = params.get(key);
    if (value) {
      if (key === "page") result.page = Math.max(0, Number(value) || 0);
      else result[key] = value;
    }
  }
  return result;
}

/** Standardized location filters come from the URL as ids only; names are resolved lazily. */
function readLocation(params: URLSearchParams): LocationValue {
  return {
    stateId: params.get("stateId"),
    cityId: params.get("cityId"),
    districtId: params.get("districtId"),
  };
}

function sameLocationIds(a: LocationValue, b: LocationValue) {
  return (
    (a.stateId ?? "") === (b.stateId ?? "") &&
    (a.cityId ?? "") === (b.cityId ?? "") &&
    (a.districtId ?? "") === (b.districtId ?? "")
  );
}

function SelectField({
  label,
  value,
  onChange,
  children,
  placeholder = "Any",
}: {
  label: string;
  value?: string;
  onChange: (value: string) => void;
  children: React.ReactNode;
  placeholder?: string;
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      <select
        className={SELECT_CLASS}
        value={value ?? ""}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{placeholder}</option>
        {children}
      </select>
    </label>
  );
}

function FiltersPanel({
  filters,
  location,
  onChange,
  onLocationChange,
  onReset,
  taxonomy,
}: {
  filters: SearchFilters;
  location: LocationValue;
  onChange: (patch: SearchFilters) => void;
  onLocationChange: (next: LocationValue) => void;
  onReset: () => void;
  taxonomy?: Awaited<ReturnType<typeof apiClient.taxonomy>>;
}) {
  return (
    <div className="flex flex-col gap-4">
      <SelectField
        label="Class"
        value={filters.classLevel}
        onChange={(value) => onChange({ classLevel: value })}
      >
        {(taxonomy?.classes ?? Array.from({ length: 12 }, (_, i) => i + 1)).map(
          (value) => (
            <option key={value} value={value}>
              Class {value}
            </option>
          )
        )}
      </SelectField>

      <SelectField
        label="Education board"
        value={filters.board}
        onChange={(value) => onChange({ board: value })}
      >
        {(taxonomy?.boards ?? []).map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </SelectField>

      <SelectField
        label="Subject"
        value={filters.subject}
        onChange={(value) => onChange({ subject: value })}
      >
        {(taxonomy?.subjects ?? []).map((subject) => (
          <option key={subject} value={subject}>
            {subject}
          </option>
        ))}
      </SelectField>

      <SelectField
        label="Price"
        value={filters.priceType}
        onChange={(value) => onChange({ priceType: value })}
      >
        {(taxonomy?.priceTypes ?? []).map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </SelectField>

      <SelectField
        label="Condition"
        value={filters.condition}
        onChange={(value) => onChange({ condition: value })}
      >
        {(taxonomy?.conditions ?? []).map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </SelectField>

      <div>
        <span className="mb-1.5 block text-xs font-medium text-muted-foreground">
          Location
        </span>
        <LocationSelector
          id="discover-location"
          value={location}
          onChange={onLocationChange}
          includeDistrict
          autoDefaultCountry
          className="grid-cols-1 sm:grid-cols-1"
          labels={{
            country: "Country",
            state: "State / UT",
            district: "District",
            city: "City",
          }}
        />
        {!location.stateId ? (
          <p className="mt-1.5 text-[11px] text-muted-foreground">
            Pick a state to unlock its cities.
          </p>
        ) : null}
      </div>

      <label className="flex flex-col gap-1.5">
        <span className="text-xs font-medium text-muted-foreground">Locality</span>
        <Input
          value={filters.locality ?? ""}
          onChange={(event) => onChange({ locality: event.target.value })}
          placeholder="e.g. Kothrud"
          className="h-10 rounded-xl bg-card"
        />
      </label>

      <Button
        variant="outline"
        onClick={onReset}
        className="mt-1 gap-2 rounded-xl"
      >
        <RotateCcw className="size-4" /> Reset filters
      </Button>
    </div>
  );
}

export function DiscoverClient() {
  const router = useRouter();
  const params = useSearchParams();
  const [filters, setFilters] = useState<SearchFilters>(() =>
    readFilters(new URLSearchParams(params.toString()))
  );
  const [location, setLocation] = useState<LocationValue>(() =>
    readLocation(new URLSearchParams(params.toString()))
  );
  const [qInput, setQInput] = useState(filters.q ?? "");
  const [mobileOpen, setMobileOpen] = useState(false);

  // Sync local filter state from URL changes using the React-adjusting-state
  // render pattern (setState during render on prop change) instead of an
  // effect, which avoids cascading renders.
  const [prevParams, setPrevParams] = useState(params);
  if (prevParams !== params) {
    const next = readFilters(new URLSearchParams(params.toString()));
    const nextLocation = readLocation(new URLSearchParams(params.toString()));
    setPrevParams(params);
    setFilters(next);
    setQInput(next.q ?? "");
    // Keep locally known names when only this component caused the URL change.
    setLocation((current) => (sameLocationIds(current, nextLocation) ? current : nextLocation));
  }

  const displayLocation = useLocationLabels(location);

  const taxonomy = useQuery({
    queryKey: ["taxonomy"],
    queryFn: apiClient.taxonomy,
    staleTime: 5 * 60_000,
  });

  const listings = useQuery({
    queryKey: ["discover", filters],
    queryFn: () => apiClient.discover(filters),
    placeholderData: (previous) => previous,
  });

  const activeCount = useMemo(
    () =>
      [
        filters.subject,
        filters.classLevel,
        filters.board,
        filters.priceType,
        filters.condition,
        filters.city,
        filters.stateId,
        filters.cityId,
        filters.districtId,
        filters.locality,
      ].filter(Boolean).length,
    [filters]
  );

  function push(next: SearchFilters) {
    setFilters(next);
    const search = new URLSearchParams();
    for (const key of FILTER_KEYS) {
      const value = next[key];
      if (value !== undefined && value !== null && `${value}` !== "") {
        search.set(key, String(value));
      }
    }
    const query = search.toString();
    router.replace(`/discover${query ? `?${query}` : ""}`, { scroll: false });
  }

  function change(patch: SearchFilters) {
    push({ ...filters, ...patch, page: 0 });
  }

  /** Location filters travel as directory ids so same-name cities in different states never collide. */
  function changeLocation(next: LocationValue) {
    setLocation(next);
    push({
      ...filters,
      stateId: next.stateId ?? undefined,
      cityId: next.cityId ?? undefined,
      districtId: next.districtId ?? undefined,
      city: undefined,
      page: 0,
    });
  }

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    change({ q: qInput.trim() });
  }

  function reset() {
    setQInput("");
    setLocation({ countryId: location.countryId, countryName: location.countryName });
    push({ size: 12, page: 0 });
  }

  const page = filters.page ?? 0;
  const totalPages = listings.data?.totalPages ?? 0;

  return (
    <div>
      <div className="rounded-2xl border border-border bg-card p-4 soft-shadow sm:p-5">
        <form onSubmit={submitSearch} className="flex flex-col gap-2 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={qInput}
              onChange={(event) => setQInput(event.target.value)}
              placeholder="Search by title, author or subject…"
              className="h-11 rounded-xl pl-9 text-base"
              aria-label="Search books"
            />
          </div>
          <div className="flex gap-2">
            <Button type="submit" className="h-11 flex-1 rounded-xl px-5 sm:flex-none">
              Search
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => setMobileOpen((open) => !open)}
              className="h-11 gap-2 rounded-xl md:hidden"
            >
              <Filter className="size-4" />
              Filters
              {activeCount ? (
                <span className="flex size-5 items-center justify-center rounded-full bg-primary text-[10px] text-primary-foreground">
                  {activeCount}
                </span>
              ) : null}
            </Button>
          </div>
        </form>
      </div>

      <div className="mt-6 grid gap-8 md:grid-cols-[240px_1fr] lg:grid-cols-[260px_1fr]">
        <aside className="hidden md:block">
          <div className="sticky top-24 rounded-2xl border border-border bg-card p-5">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                <SlidersHorizontal className="size-4 text-primary" /> Refine
              </h2>
              {activeCount ? (
                <span className="rounded-full bg-secondary px-2 py-0.5 text-[10px] font-medium text-secondary-foreground">
                  {activeCount} active
                </span>
              ) : null}
            </div>
            <FiltersPanel
              filters={filters}
              location={displayLocation}
              onChange={change}
              onLocationChange={changeLocation}
              onReset={reset}
              taxonomy={taxonomy.data}
            />
          </div>
        </aside>

        {mobileOpen ? (
          <div className="rounded-2xl border border-border bg-card p-5 md:hidden">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-semibold">Refine results</h2>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Close filters"
                onClick={() => setMobileOpen(false)}
              >
                <X className="size-4" />
              </Button>
            </div>
            <FiltersPanel
              filters={filters}
              location={displayLocation}
              onChange={change}
              onLocationChange={changeLocation}
              onReset={reset}
              taxonomy={taxonomy.data}
            />
            <Button className="mt-4 w-full" onClick={() => setMobileOpen(false)}>
              Show results
            </Button>
          </div>
        ) : null}

        <section>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 className="font-display text-3xl font-semibold sm:text-4xl">
                {filters.q ? `Results for “${filters.q}”` : "Books near you"}
              </h1>
              <p className="mt-1 text-sm text-muted-foreground">
                {listings.data
                  ? `${listings.data.totalElements} book${
                      listings.data.totalElements === 1 ? "" : "s"
                    } available`
                  : "Finding books on nearby shelves…"}
              </p>
            </div>
            <span className="rounded-full border border-border bg-card px-3 py-1.5 text-xs text-muted-foreground">
              Recently added first
            </span>
          </div>

          {listings.isError ? (
            <div className="mt-6 rounded-2xl border border-destructive/25 bg-destructive/5 p-6">
              <p className="text-sm font-medium">We couldn&apos;t load the shelves.</p>
              <p className="mt-1 text-sm text-muted-foreground">
                {errorMessage(listings.error)}
              </p>
              <Button
                variant="outline"
                className="mt-4"
                onClick={() => void listings.refetch()}
              >
                Try again
              </Button>
            </div>
          ) : listings.isLoading ? (
            <div className="mt-6 grid grid-cols-2 gap-4 sm:gap-5 lg:grid-cols-3 xl:grid-cols-4">
              {Array.from({ length: 8 }).map((_, index) => (
                <div key={index} className="space-y-2">
                  <div className="aspect-[4/5] animate-pulse rounded-2xl bg-muted" />
                  <div className="h-4 w-4/5 animate-pulse rounded bg-muted" />
                  <div className="h-3 w-1/2 animate-pulse rounded bg-muted" />
                </div>
              ))}
            </div>
          ) : listings.data && listings.data.content.length > 0 ? (
            <>
              <div className="mt-6 grid grid-cols-2 gap-4 sm:gap-5 lg:grid-cols-3 xl:grid-cols-4">
                {listings.data.content.map((listing) => (
                  <ListingCard key={listing.id} listing={listing} />
                ))}
              </div>

              <div className="mt-8 flex flex-col items-center justify-between gap-3 border-t border-border pt-5 sm:flex-row">
                <p className="text-xs text-muted-foreground">
                  Page {Math.min(page + 1, Math.max(totalPages, 1))} of{" "}
                  {Math.max(totalPages, 1)}
                </p>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page <= 0}
                    onClick={() => push({ ...filters, page: page - 1 })}
                    className="gap-1"
                  >
                    <ChevronLeft className="size-4" /> Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={!listings.data.hasNext}
                    onClick={() => push({ ...filters, page: page + 1 })}
                    className="gap-1"
                  >
                    Next <ChevronRight className="size-4" />
                  </Button>
                </div>
              </div>
            </>
          ) : (
            <div className="mt-6">
              <EmptyState
                icon={Compass}
                title="No books match those filters"
                description="Try a broader class, a different board, or clear the filters to see everything nearby."
                action={
                  <Button variant="outline" onClick={reset}>
                    Clear filters
                  </Button>
                }
              />
              <p className="mt-4 text-center text-sm text-muted-foreground">
                Nothing on your shelf for this search?{" "}
                <Link href="/listings/new" className="font-medium text-primary underline-offset-4 hover:underline">
                  List a book
                </Link>{" "}
                or{" "}
                <Link href="/discover?priceType=FREE" className="font-medium text-primary underline-offset-4 hover:underline">
                  browse free books
                </Link>
                .
              </p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
