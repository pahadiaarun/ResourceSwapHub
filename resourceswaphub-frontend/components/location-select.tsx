"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Check, ChevronDown, Loader2, MapPin, Search, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiClient } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { DirectoryCity, DirectoryCountry, DirectoryState, LocationInfo } from "@/lib/types";

const DIRECTORY_STALE_MS = 24 * 60 * 60 * 1000;
const CITY_SEARCH_DEBOUNCE_MS = 250;
const CITY_PAGE_SIZE = 30;

export interface LocationOption {
  id: string;
  name: string;
  hint?: string | null;
}

/** The subset of a standardized location a form works with; ids are the source of truth. */
export interface LocationValue {
  countryId?: string | null;
  stateId?: string | null;
  cityId?: string | null;
  districtId?: string | null;
  countryName?: string | null;
  stateName?: string | null;
  cityName?: string | null;
  districtName?: string | null;
}

export function locationFromInfo(info?: LocationInfo | null): LocationValue {
  if (!info) return {};
  return {
    countryId: info.countryId ?? null,
    stateId: info.stateId ?? null,
    cityId: info.cityId ?? null,
    countryName: info.countryName ?? null,
    stateName: info.stateName ?? null,
    cityName: info.cityName ?? null,
  };
}

/**
 * Fills in display names for a location that only has ids (for example a shared
 * search URL), so filters never show a blank box next to an active selection.
 */
export function useLocationLabels(value: LocationValue): LocationValue {
  const needsCity = Boolean(value.cityId) && !value.cityName;
  const needsState = Boolean(value.stateId) && !value.stateName;

  const cityLookup = useQuery({
    queryKey: ["locations", "city", value.cityId],
    queryFn: () => apiClient.city(value.cityId as string),
    enabled: needsCity,
    staleTime: DIRECTORY_STALE_MS,
  });

  // One city row from the state carries its canonical state name.
  const stateLookup = useQuery({
    queryKey: ["locations", "cities", value.stateId, ""],
    queryFn: () => apiClient.cities(value.stateId as string, "", 0, 1),
    enabled: needsState && !needsCity,
    staleTime: DIRECTORY_STALE_MS,
  });

  return useMemo(
    () => ({
      ...value,
      stateId: value.stateId ?? cityLookup.data?.stateId ?? null,
      cityName: value.cityName ?? cityLookup.data?.name ?? null,
      districtId: value.districtId ?? cityLookup.data?.districtId ?? null,
      districtName: value.districtName ?? cityLookup.data?.districtName ?? null,
      stateName:
        value.stateName ??
        cityLookup.data?.stateName ??
        stateLookup.data?.content[0]?.stateName ??
        null,
    }),
    [cityLookup.data, stateLookup.data, value]
  );
}

function normalize(text: string) {
  return text.trim().toLowerCase().replace(/\s+/g, " ");
}

function useDebounced<T>(value: T, delayMs: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

interface ComboboxProps {
  id?: string;
  label: string;
  value: LocationOption | null;
  options: LocationOption[];
  isLoading: boolean;
  disabled?: boolean;
  disabledHint?: string;
  placeholder: string;
  emptyMessage: string;
  /**
   * False when the parent already narrowed `options` server-side (cities can be
   * thousands per state and are never fetched as one list).
   */
  localFilter: boolean;
  onQueryChange?: (query: string) => void;
  onChange: (option: LocationOption | null) => void;
  className?: string;
}

/**
 * Single accessible combobox behind every location dropdown: keyboard, screen
 * reader and touch behaviour are implemented once here rather than per page.
 */
function LocationCombobox({
  id,
  label,
  value,
  options,
  isLoading,
  disabled = false,
  disabledHint,
  placeholder,
  emptyMessage,
  localFilter,
  onQueryChange,
  onChange,
  className,
}: ComboboxProps) {
  const [query, setQuery] = useState("");
  const [isOpen, setIsOpen] = useState(false);
  const [active, setActive] = useState(0);
  const boxRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  // A disabled level (no country or state chosen yet) can never keep a stale panel open.
  const open = isOpen && !disabled;

  const visible = useMemo(() => {
    const needle = normalize(query);
    if (!localFilter || !needle) return options;
    return options.filter(
      (option) =>
        normalize(option.name).includes(needle) ||
        normalize(option.hint ?? "").includes(needle)
    );
  }, [localFilter, options, query]);

  useEffect(() => {
    onQueryChange?.(query.trim());
  }, [query, onQueryChange]);

  useEffect(() => {
    function onPointerDown(event: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(event.target as Node)) {
        setIsOpen(false);
        setQuery("");
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  useEffect(() => {
    const el = listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`);
    el?.scrollIntoView({ block: "nearest" });
  }, [active, isOpen]);

  function choose(option: LocationOption) {
    onChange(option);
    setQuery("");
    setIsOpen(false);
  }

  function clear() {
    onChange(null);
    setQuery("");
    setIsOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "Escape") {
      setIsOpen(false);
      setQuery("");
      return;
    }
    if (visible.length === 0) return;
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        setIsOpen(true);
        setActive((i) => (i + 1) % visible.length);
        break;
      case "ArrowUp":
        event.preventDefault();
        setIsOpen(true);
        setActive((i) => (i - 1 + visible.length) % visible.length);
        break;
      case "Enter":
        event.preventDefault();
        choose(visible[Math.min(active, visible.length - 1)]);
        break;
    }
  }

  const listboxId = `${id ?? label}-listbox`.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const displayText = open ? query : query || value?.name || "";

  return (
    <div className={cn("relative", className)} ref={boxRef}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          id={id}
          role="combobox"
          aria-label={label}
          aria-expanded={open}
          aria-controls={listboxId}
          aria-autocomplete={localFilter ? "list" : "none"}
          aria-haspopup="listbox"
          autoComplete="off"
          disabled={disabled}
          value={displayText}
          onChange={(event) => {
            setQuery(event.target.value);
            setActive(0);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          onKeyDown={onKeyDown}
          placeholder={disabled ? (disabledHint ?? placeholder) : placeholder}
          className={cn(
            "h-11 rounded-xl pl-9 pr-16",
            disabled && "cursor-not-allowed bg-muted/50 text-muted-foreground"
          )}
        />
        {value && !open ? (
          <button
            type="button"
            aria-label={`Clear ${label}`}
            onClick={clear}
            className="absolute right-3 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="size-4" />
          </button>
        ) : (
          <ChevronDown
            className={cn(
              "pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground transition-transform",
              open && "rotate-180"
            )}
          />
        )}
      </div>

      {open && (
        <div className="absolute z-50 mt-2 w-full overflow-hidden rounded-xl border border-border bg-card shadow-lg">
          {isLoading ? (
            <div className="flex items-center gap-2 p-3 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> Loading...
            </div>
          ) : visible.length === 0 ? (
            <div className="p-3 text-sm text-muted-foreground">
              {normalize(query) ? emptyMessage : "No options available."}
            </div>
          ) : (
            <ul
              id={listboxId}
              role="listbox"
              ref={listRef}
              aria-label={label}
              className="max-h-64 overflow-auto py-1"
            >
              {visible.map((option, index) => {
                const selected = value?.id === option.id;
                const highlighted = active === index;
                return (
                  <li key={option.id} role="option" aria-selected={selected} data-index={index}>
                    <button
                      type="button"
                      tabIndex={-1}
                      onMouseEnter={() => setActive(index)}
                      onClick={() => choose(option)}
                      className={cn(
                        "flex w-full items-start gap-2 px-3 py-2 text-left",
                        highlighted ? "bg-muted" : "hover:bg-muted/60"
                      )}
                    >
                      <MapPin className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium">{option.name}</span>
                        {option.hint && (
                          <span className="block truncate text-xs text-muted-foreground">
                            {option.hint}
                          </span>
                        )}
                      </span>
                      {selected && <Check className="mt-1 size-4 shrink-0 text-primary" />}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function optionOf(value: { id?: string | null; name?: string | null } | null | undefined) {
  return value?.id ? { id: value.id, name: value.name ?? "" } : null;
}

/** Free-standing searchable country picker; India is seeded but the list is directory-driven. */
export function CountrySelect({
  id,
  value,
  onChange,
  disabled,
  className,
}: {
  id?: string;
  value: LocationValue;
  onChange: (country: LocationOption | null) => void;
  disabled?: boolean;
  className?: string;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["locations", "countries"],
    queryFn: () => apiClient.countries(),
    staleTime: DIRECTORY_STALE_MS,
  });

  const options: LocationOption[] = useMemo(
    () => (data ?? []).map((country: DirectoryCountry) => ({ id: country.id, name: country.name })),
    [data]
  );

  return (
    <LocationCombobox
      id={id}
      label="Country"
      placeholder="Search country..."
      emptyMessage="No countries found."
      localFilter
      options={options}
      isLoading={isLoading}
      disabled={disabled}
      value={optionOf({ id: value.countryId, name: value.countryName })}
      onChange={onChange}
      className={className}
    />
  );
}

/** All states/UTs of the chosen country, searchable and alphabetically ordered. */
export function StateSelect({
  id,
  countryId,
  value,
  onChange,
  className,
}: {
  id?: string;
  countryId?: string | null;
  value: LocationValue;
  onChange: (state: LocationOption | null) => void;
  className?: string;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["locations", "states", countryId],
    queryFn: () => apiClient.states(countryId as string),
    enabled: Boolean(countryId),
    staleTime: DIRECTORY_STALE_MS,
  });

  const options: LocationOption[] = useMemo(
    () =>
      (data ?? []).map((state: DirectoryState) => ({
        id: state.id,
        name: state.name,
        hint: state.stateType,
      })),
    [data]
  );

  return (
    <LocationCombobox
      id={id}
      label="State / Union Territory"
      placeholder={countryId ? "Search state or union territory..." : "Select a country first"}
      emptyMessage="No states or union territories found."
      localFilter
      options={options}
      isLoading={Boolean(countryId) && isLoading}
      disabled={!countryId}
      disabledHint="Select a country first"
      value={optionOf({ id: value.stateId, name: value.stateName })}
      onChange={onChange}
      className={className}
    />
  );
}

/** Optional district narrowing for discovery; a city already carries its own district. */
export function DistrictSelect({
  id,
  stateId,
  value,
  onChange,
  className,
}: {
  id?: string;
  stateId?: string | null;
  value: LocationValue;
  onChange: (district: LocationOption | null) => void;
  className?: string;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["locations", "districts", stateId],
    queryFn: () => apiClient.districts(stateId as string),
    enabled: Boolean(stateId),
    staleTime: DIRECTORY_STALE_MS,
  });

  const options: LocationOption[] = useMemo(
    () =>
      (data ?? []).map((district) => ({
        id: district.id,
        name: district.name,
        hint: district.officialCode,
      })),
    [data]
  );

  return (
    <LocationCombobox
      id={id}
      label="District"
      placeholder={stateId ? "Search district (optional)..." : "Select a state first"}
      emptyMessage="No districts found for this state."
      localFilter
      options={options}
      isLoading={Boolean(stateId) && isLoading}
      disabled={!stateId}
      disabledHint="Select a state first"
      value={optionOf({ id: value.districtId, name: value.districtName })}
      onChange={onChange}
      className={className}
    />
  );
}

/**
 * Cities are fetched per state and searched server-side so one request never
 * carries a whole state's directory; the picker is inert until a state exists.
 */
export function CitySelect({
  id,
  stateId,
  value,
  onChange,
  className,
}: {
  id?: string;
  stateId?: string | null;
  value: LocationValue;
  onChange: (city: LocationOption | null) => void;
  className?: string;
}) {
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebounced(query, CITY_SEARCH_DEBOUNCE_MS);

  const { data, isLoading } = useQuery({
    queryKey: ["locations", "cities", stateId, debouncedQuery],
    queryFn: () => apiClient.cities(stateId as string, debouncedQuery, 0, CITY_PAGE_SIZE),
    enabled: Boolean(stateId),
    staleTime: 5 * 60 * 1000,
  });

  const options: LocationOption[] = useMemo(
    () =>
      (data?.content ?? []).map((city: DirectoryCity) => ({
        id: city.id,
        name: city.name,
        hint: city.districtName ? `${city.districtName} District` : null,
      })),
    [data]
  );

  const hasMore = (data?.totalElements ?? 0) > options.length;

  return (
    <div className={className}>
      <LocationCombobox
        id={id}
        label="City"
        placeholder={stateId ? "Search city, town or district..." : "Select a state first"}
        emptyMessage={`No cities match "${query.trim()}".`}
        localFilter={false}
        options={options}
        isLoading={Boolean(stateId) && isLoading}
        disabled={!stateId}
        disabledHint="Select a state first"
        onQueryChange={setQuery}
        value={optionOf({ id: value.cityId, name: value.cityName })}
        onChange={onChange}
      />
      {hasMore && (
        <p className="mt-1.5 text-xs text-muted-foreground">
          {data?.totalElements} matches — keep typing to narrow the list.
        </p>
      )}
    </div>
  );
}

/**
 * The one cascading country/state/city control used by registration, profile,
 * listings, collections and search. Changing a level clears everything below it
 * so a selection can never end up as a city that belongs to another state.
 */
export function LocationSelector({
  value,
  onChange,
  labels,
  includeDistrict = false,
  autoDefaultCountry = false,
  countryDisabled = false,
  className,
  id,
}: {
  value: LocationValue;
  onChange: (next: LocationValue) => void;
  labels?: { country?: string; state?: string; city?: string; district?: string };
  includeDistrict?: boolean;
  autoDefaultCountry?: boolean;
  countryDisabled?: boolean;
  className?: string;
  id?: string;
}) {
  const [countryTouched, setCountryTouched] = useState(false);
  const { data: countries } = useQuery({
    queryKey: ["locations", "countries"],
    queryFn: () => apiClient.countries(),
    staleTime: DIRECTORY_STALE_MS,
  });

  // Default to India without restricting the directory to it: the picker stays
  // searchable and the operator can choose any seeded country instead.
  useEffect(() => {
    if (!autoDefaultCountry || countryTouched || value.countryId || !countries?.length) return;
    const fallback = countries.find((country) => country.isoCode === "IN") ?? countries[0];
    onChange({ ...value, countryId: fallback.id, countryName: fallback.name });
    // Intentionally fires only when the list first arrives or the value is empty.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoDefaultCountry, countryTouched, countries, value.countryId]);

  const fieldId = (key: string) => (id ? `${id}-${key}` : undefined);

  return (
    <div className={cn("grid gap-4 sm:grid-cols-2", className)}>
      <div className="space-y-2">
        <Label htmlFor={fieldId("country")} className="text-xs font-medium text-muted-foreground">
          {labels?.country ?? "Country"}
        </Label>
        <CountrySelect
          id={fieldId("country")}
          value={value}
          disabled={countryDisabled}
          className={cn(countryDisabled && "opacity-70")}
          onChange={(country) => {
            setCountryTouched(true);
            onChange({
              countryId: country?.id ?? null,
              countryName: country?.name ?? null,
            });
          }}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor={fieldId("state")} className="text-xs font-medium text-muted-foreground">
          {labels?.state ?? "State / Union Territory"}
        </Label>
        <StateSelect
          id={fieldId("state")}
          countryId={value.countryId}
          value={value}
          onChange={(state) =>
            onChange({
              ...value,
              stateId: state?.id ?? null,
              stateName: state?.name ?? null,
              districtId: null,
              districtName: null,
              cityId: null,
              cityName: null,
            })
          }
        />
      </div>

      {includeDistrict && (
        <div className="space-y-2">
          <Label htmlFor={fieldId("district")} className="text-xs font-medium text-muted-foreground">
            {labels?.district ?? "District"}
          </Label>
          <DistrictSelect
            id={fieldId("district")}
            stateId={value.stateId}
            value={value}
            onChange={(district) =>
              onChange({
                ...value,
                districtId: district?.id ?? null,
                districtName: district?.name ?? null,
              })
            }
          />
        </div>
      )}

      <div className="space-y-2">
        <Label htmlFor={fieldId("city")} className="text-xs font-medium text-muted-foreground">
          {labels?.city ?? "City / Town"}
        </Label>
        <CitySelect
          id={fieldId("city")}
          stateId={value.stateId}
          value={value}
          onChange={(city) =>
            onChange({ ...value, cityId: city?.id ?? null, cityName: city?.name ?? null })
          }
        />
      </div>
    </div>
  );
}
