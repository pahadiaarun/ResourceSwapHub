"use client";

import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Building2, Check, Loader2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { apiClient } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { Institution } from "@/lib/types";

interface InstitutionSearchProps {
  value?: string;
  onSelect: (institution: { id: string; name: string }) => void;
  institutionType?: string;
  state?: string;
  placeholder?: string;
  /** Free-text fallback when the directory has no match; emits id="" so callers can store a manual name. */
  onUseManual?: (name: string) => void;
  id?: string;
}

export function InstitutionSearch({
  value,
  onSelect,
  institutionType,
  state,
  placeholder = "Search institution...",
  onUseManual,
  id,
}: InstitutionSearchProps) {
  const [query, setQuery] = useState(value || "");
  const [isOpen, setIsOpen] = useState(false);
  const [active, setActive] = useState(0);
  const boxRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const enabled = query.trim().length >= 2;
  const { data, isLoading } = useQuery({
    queryKey: ["institutions", query, institutionType, state],
    queryFn: () =>
      apiClient.searchInstitutions({
        q: query,
        type: institutionType,
        state,
        size: 10,
      }),
    enabled,
  });

  const results = data?.content ?? [];
  // Row count includes an optional "use manual name" affordance at the end.
  const rowCount = results.length + (onUseManual && enabled ? 1 : 0);

  useEffect(() => {
    function onPointerDown(event: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  useEffect(() => {
    const el = listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`);
    el?.scrollIntoView({ block: "nearest" });
  }, [active]);

  function chooseInstitution(inst: Institution) {
    onSelect({ id: inst.id, name: inst.name });
    setQuery(inst.name);
    setIsOpen(false);
  }

  function chooseManual() {
    const name = query.trim();
    if (!name) return;
    onUseManual?.(name);
    onSelect({ id: "", name });
    setIsOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (!isOpen || rowCount === 0) {
      if ((event.key === "ArrowDown" || event.key === "Enter") && enabled) {
        setIsOpen(true);
      }
      return;
    }
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        setActive((i) => (i + 1) % rowCount);
        break;
      case "ArrowUp":
        event.preventDefault();
        setActive((i) => (i - 1 + rowCount) % rowCount);
        break;
      case "Enter":
        event.preventDefault();
        if (active < results.length) chooseInstitution(results[active]);
        else chooseManual();
        break;
      case "Escape":
        setIsOpen(false);
        break;
    }
  }

  const listboxId = `${id ?? "institution-search"}-listbox`;

  return (
    <div className="relative" ref={boxRef}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          id={id}
          role="combobox"
          aria-expanded={isOpen}
          aria-controls={listboxId}
          aria-autocomplete="list"
          autoComplete="off"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setActive(0);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          onKeyDown={onKeyDown}
          placeholder={placeholder}
          className="h-11 rounded-xl pl-9"
        />
      </div>

      {isOpen && enabled && (
        <div className="absolute z-50 mt-2 w-full overflow-hidden rounded-xl border border-border bg-card shadow-lg">
          {isLoading ? (
            <div className="flex items-center gap-2 p-3 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> Searching...
            </div>
          ) : rowCount === 0 ? (
            <div className="p-3 text-sm text-muted-foreground">
              No institutions found for &ldquo;{query.trim()}&rdquo;.
            </div>
          ) : (
            <ul id={listboxId} role="listbox" ref={listRef} className="max-h-64 overflow-auto py-1">
              {results.map((inst, index) => {
                const selected = active === index;
                return (
                  <li key={inst.id} role="option" aria-selected={selected} data-index={index}>
                    <button
                      type="button"
                      tabIndex={-1}
                      onMouseEnter={() => setActive(index)}
                      onClick={() => chooseInstitution(inst)}
                      className={cn(
                        "flex w-full items-start gap-2 px-3 py-2 text-left",
                        selected ? "bg-muted" : "hover:bg-muted/60"
                      )}
                    >
                      <Building2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium">{inst.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {[inst.institutionType, inst.city, inst.state]
                            .filter(Boolean)
                            .join(" • ")}
                        </span>
                      </span>
                      {selected && <Check className="mt-1 size-4 shrink-0 text-primary" />}
                    </button>
                  </li>
                );
              })}

              {onUseManual && (
                <li
                  role="option"
                  aria-selected={active === results.length}
                  data-index={results.length}
                >
                  <button
                    type="button"
                    tabIndex={-1}
                    onMouseEnter={() => setActive(results.length)}
                    onClick={chooseManual}
                    className={cn(
                      "flex w-full items-center gap-2 border-t border-border px-3 py-2 text-left text-sm",
                      active === results.length ? "bg-muted" : "hover:bg-muted/60"
                    )}
                  >
                    <span className="text-muted-foreground">Can&rsquo;t find it? Use</span>
                    <span className="font-medium">&ldquo;{query.trim()}&rdquo;</span>
                  </button>
                </li>
              )}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
